package com.github.kr328.clash.design.settings

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * A deliberately small WebDAV client.
 *
 * There is no WebDAV code in this project yet and no HTTP dependency is
 * allowed in this phase, so this is built on `HttpURLConnection` plus the
 * platform XML pull parser. It implements only what backup and restore need:
 * PROPFIND (list), MKCOL (create the backup folder) and PUT/GET (transfer).
 */
object WebDav {

    class WebDavException(message: String, cause: Throwable? = null) :
        IOException(message, cause)

    data class Config(
        val url: String,
        val directory: String,
        val username: String,
        val password: String,
    ) {
        /** `https://host/base` with any trailing slash removed. */
        val baseUrl: String
            get() {
                val trimmed = url.trim()

                val withScheme = when {
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                    trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                    trimmed.isEmpty() -> ""
                    else -> "https://$trimmed"
                }

                return withScheme.trimEnd('/')
            }

        val path: String
            get() = directory.trim().trim('/')

        val isConfigured: Boolean
            get() = baseUrl.isNotEmpty()

        fun fileUrl(name: String): String {
            return if (path.isEmpty()) "$baseUrl/$name" else "$baseUrl/$path/$name"
        }

        fun directoryUrl(): String {
            return if (path.isEmpty()) "$baseUrl/" else "$baseUrl/$path/"
        }
    }

    data class RemoteFile(
        val name: String,
        val size: Long,
        val lastModified: Long,
    )

    /**
     * Lists the files in the configured backup directory, newest first.
     * An absent directory is created rather than reported as an error.
     */
    suspend fun list(config: Config): List<RemoteFile> = withContext(Dispatchers.IO) {
        requireConfigured(config)

        val url = URL(config.directoryUrl())

        val connection = open(config, url, "PROPFIND")

        try {
            connection.setRequestProperty("Depth", "1")

            val code = connection.responseCode

            if (code == HttpURLConnection.HTTP_NOT_FOUND
                || code == HttpURLConnection.HTTP_CLIENT_ERROR
            ) {
                connection.disconnect()

                ensureDirectory(config)

                return@withContext emptyList()
            }

            if (code !in 200..299)
                throw WebDavException("PROPFIND failed with HTTP $code")

            val body = connection.inputStream.bufferedReader().use { it.readText() }

            parseMultistatus(body, config)
        } finally {
            connection.disconnect()
        }
    }

    /** Writes [data] to `<directory>/<name>`, creating the directory if needed. */
    suspend fun upload(config: Config, name: String, data: ByteArray) {
        withContext(Dispatchers.IO) {
            requireConfigured(config)

            ensureDirectory(config)

            val connection = open(config, URL(config.fileUrl(name)), "PUT")

            try {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(data.size)
                connection.outputStream.use { it.write(data) }

                val code = connection.responseCode

                if (code !in 200..299)
                    throw WebDavException("PUT failed with HTTP $code")
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Reads `<directory>/<name>`. */
    suspend fun download(config: Config, name: String): ByteArray = withContext(Dispatchers.IO) {
        requireConfigured(config)

        val connection = open(config, URL(config.fileUrl(name)), "GET")

        try {
            val code = connection.responseCode

            if (code !in 200..299)
                throw WebDavException("GET failed with HTTP $code")

            val output = ByteArrayOutputStream()

            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)

                while (true) {
                    val read = input.read(buffer)

                    if (read < 0)
                        break

                    output.write(buffer, 0, read)
                }
            }

            output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun requireConfigured(config: Config) {
        if (!config.isConfigured)
            throw WebDavException("WebDAV server URL is not set")
    }

    private suspend fun ensureDirectory(config: Config) {
        if (config.path.isEmpty())
            return

        val probe = open(config, URL(config.directoryUrl()), "PROPFIND")

        try {
            probe.setRequestProperty("Depth", "0")

            if (probe.responseCode in 200..299)
                return
        } finally {
            probe.disconnect()
        }

        // 405 means the server refuses PROPFIND but the collection is there.
        val connection = open(config, URL(config.directoryUrl()), "MKCOL")

        try {
            val code = connection.responseCode

            if (code !in 200..299 && code != HttpURLConnection.HTTP_CLIENT_ERROR)
                throw WebDavException("MKCOL failed with HTTP $code")
        } finally {
            connection.disconnect()
        }
    }

    private fun open(config: Config, url: URL, method: String): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = method
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.useCaches = false

        if (config.username.isNotEmpty()) {
            val token = Base64.encodeToString(
                "${config.username}:${config.password}".toByteArray(),
                Base64.NO_WRAP,
            )

            connection.setRequestProperty("Authorization", "Basic $token")
        }

        return connection
    }

    private fun parseMultistatus(body: String, config: Config): List<RemoteFile> {
        val factory = XmlPullParserFactory.newInstance()

        factory.isNamespaceAware = false

        val parser = factory.newPullParser()

        parser.setInput(body.reader())

        val files = mutableListOf<String>()
        val sizes = mutableListOf<Long>()
        val modified = mutableListOf<Long>()

        var href: String? = null
        var size = -1L
        var lastModified = 0L

        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.substringAfterLast(':')) {
                    "href" -> href = parser.nextText().trim()
                    "getcontentlength" -> size = parser.nextText().trim().toLongOrNull() ?: -1L
                    "getlastmodified" -> lastModified = parseHttpDate(parser.nextText())
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (parser.name.substringAfterLast(':')) {
                    "response" -> {
                        href?.let {
                            files.add(it)
                            sizes.add(size)
                            modified.add(lastModified)
                        }

                        href = null
                        size = -1L
                        lastModified = 0L
                    }
                }
            }

            event = parser.next()
        }

        val prefix = config.directoryUrl()

        return files.mapIndexedNotNull { index, href ->
            val name = href.substringAfterLast('/').substringBefore('?')

            // the collection itself comes back as an entry too
            if (name.isEmpty() || href.trimEnd('/') == prefix.trimEnd('/'))
                return@mapIndexedNotNull null

            RemoteFile(
                name = name,
                size = sizes.getOrElse(index) { -1L },
                lastModified = modified.getOrElse(index) { 0L },
            )
        }.sortedByDescending { it.lastModified }
    }

    private fun parseHttpDate(value: String): Long {
        for (pattern in HTTP_DATE_PATTERNS) {
            try {
                val format = java.text.SimpleDateFormat(pattern, java.util.Locale.US)

                format.timeZone = java.util.TimeZone.getTimeZone("GMT")

                return format.parse(value.trim())?.time ?: 0L
            } catch (e: Exception) {
                // try the next pattern
            }
        }

        return 0L
    }

    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 60000

    private val HTTP_DATE_PATTERNS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
    )
}
