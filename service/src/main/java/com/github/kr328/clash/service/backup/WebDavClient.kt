package com.github.kr328.clash.service.backup

import android.util.Xml
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class WebDavClient(private val config: BackupConfig, private val password: String, private val client: OkHttpClient = defaultClient()) {
    companion object {
        private fun defaultClient() = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES)
        .followRedirects(false).followSslRedirects(false).build()
        private val EMPTY = ByteArray(0).toRequestBody(null)
        fun validate(config: BackupConfig) {
            val url = config.url.trim().toHttpUrl()
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "Use an HTTPS WebDAV URL without embedded credentials, query or fragment" }
            require(config.directory.trim('/').split('/').none { it == "." || it == ".." || '\\' in it }) { "Invalid WebDAV directory" }
        }
    }
    @Volatile private var activeCall: okhttp3.Call? = null
    fun cancel() { activeCall?.cancel() }

    private fun directory(): HttpUrl {
        validate(config)
        val base = config.url.trim().toHttpUrl().newBuilder()
        if (!config.url.trim().endsWith('/')) base.addPathSegment("")
        config.directory.trim('/').split('/').filter(String::isNotEmpty).forEach { base.addPathSegment(it) }
        base.addPathSegment("")
        return base.build()
    }
    private fun file(name: String): HttpUrl {
        require(name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != "..") { "Invalid remote backup name" }
        return directory().newBuilder().addPathSegment(name).build()
    }
    private fun request(url: HttpUrl, method: String, body: RequestBody? = null, depth: String? = null): Response {
        val builder = Request.Builder().url(url).method(method, body)
        if (config.username.isNotEmpty()) builder.header("Authorization", Credentials.basic(config.username, password, Charsets.UTF_8))
        if (depth != null) builder.header("Depth", depth)
        val call = client.newCall(builder.build()); activeCall = call
        return call.execute()
    }
    private fun Response.requireSuccess() {
        if (!isSuccessful) throw IOException(when (code) {
            401, 403 -> "WebDAV authentication or permission denied (HTTP $code)"
            in 300..399 -> "WebDAV redirects are disabled; configure the final HTTPS URL"
            else -> "WebDAV request failed (HTTP $code)"
        })
    }
    private fun ensureDirectory() {
        request(directory(), "PROPFIND", depth = "0").use {
            if (it.isSuccessful) return
            if (it.code != 404) { it.requireSuccess(); return }
        }
        request(directory(), "MKCOL", EMPTY).use { it.requireSuccess() }
    }
    fun list(): List<BackupRemoteFile> {
        request(directory(), "PROPFIND", depth = "1").use { response ->
            if (response.code == 404) return emptyList()
            response.requireSuccess()
            val stream = response.body?.byteStream() ?: throw IOException("Empty WebDAV response")
            val bounded = BoundedInputStream(stream, 1024 * 1024)
            val parser = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); setInput(bounded, "UTF-8") }
            val result = mutableListOf<BackupRemoteFile>()
            var href = ""; var length = -1L; var modified = ""; var isDirectory = false
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                    "response" -> { href = ""; length = -1; modified = ""; isDirectory = false }
                    "href" -> href = parser.nextText().trim()
                    "getcontentlength" -> length = parser.nextText().toLongOrNull() ?: -1
                    "getlastmodified" -> modified = parser.nextText()
                    "collection" -> isDirectory = true
                }
                if (parser.eventType == XmlPullParser.END_TAG && parser.name == "response" && !isDirectory) {
                    val resolved = directory().resolve(href)
                    if (resolved != null && resolved.scheme == directory().scheme && resolved.host == directory().host && resolved.port == directory().port) {
                        val segments = resolved.pathSegments
                        val parent = directory().pathSegments.filter(String::isNotEmpty)
                        if (segments.size == parent.size + 1 && segments.dropLast(1) == parent) {
                            val name = segments.last()
                            if ((name.endsWith(".gxbackup.age") || name.endsWith(".zip")) && result.size < BackupLimits.ENTRIES)
                                result += BackupRemoteFile(name, length, modified)
                        }
                    }
                }
                parser.next()
            }
            return result.sortedByDescending { it.name }
        }
    }
    suspend fun download(name: String, target: File) {
        request(file(name), "GET").use { response ->
            response.requireSuccess()
            val body = response.body ?: throw IOException("Empty WebDAV response")
            require(body.contentLength() <= BackupLimits.TRANSFER) { "Backup exceeds 256 MiB" }
            try {
                body.byteStream().use { input -> target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= BackupLimits.TRANSFER) { "Backup exceeds 256 MiB" }; output.write(buffer, 0, n) }
                    output.fd.sync()
                } }
            } catch (e: Throwable) { target.delete(); throw e }
        }
    }
    fun upload(name: String, source: File) {
        require(source.length() <= BackupLimits.TRANSFER) { "Backup exceeds 256 MiB" }
        ensureDirectory()
        request(file(name), "PUT", source.asRequestBody("application/octet-stream".toMediaType())).use { it.requireSuccess() }
    }
}

internal class BoundedInputStream(input: java.io.InputStream, private val maximum: Long) : java.io.FilterInputStream(input) {
    private var read = 0L
    override fun read(): Int { val v = super.read(); if (v >= 0) checkLimit(1); return v }
    override fun read(b: ByteArray, off: Int, len: Int): Int { val n = `in`.read(b, off, len); if (n > 0) checkLimit(n); return n }
    private fun checkLimit(n: Int) { read += n; if (read > maximum) throw IOException("Response exceeds permitted size") }
}
