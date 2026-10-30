package com.github.kr328.clash.service.security

import android.content.Context
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.service.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Applied to every actual connection, not just to an earlier validation lookup. */
object PublicProfileAddress {
    fun allowed(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address.map { it.toInt() and 255 }
        if (b.size == 16) return b[0] and 0xfe != 0xfc && b[0] and 0xe0 == 0x20
        if (b.size != 4) return false
        return !(b[0] == 0 || b[0] >= 224 ||
            (b[0] == 100 && b[1] in 64..127) ||
            (b[0] == 192 && b[1] == 0 && b[2] in listOf(0, 2)) ||
            (b[0] == 198 && b[1] in 18..19) ||
            (b[0] == 198 && b[1] == 51 && b[2] == 100) ||
            (b[0] == 203 && b[1] == 0 && b[2] == 113))
    }

    fun parse(value: String): HttpUrl {
        val url = value.toHttpUrlOrNull() ?: throw IOException("Invalid HTTP(S) profile URL")
        if (url.username.isNotEmpty() || url.password.isNotEmpty())
            throw IOException("Credentials in an external profile URL are not supported")
        val host = url.host.lowercase().trimEnd('.')
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local"))
            throw IOException("External profiles cannot access the local network")
        return url
    }
}

object ExternalProfileDownload {
    fun validateProviders(config: File, secretKey: String?, override: String = "{}") {
        val inventory = kotlinx.serialization.json.Json.parseToJsonElement(
            com.github.kr328.clash.core.bridge.BackupBridge.providerInventory(config.path, secretKey.orEmpty(), override)
        ) as kotlinx.serialization.json.JsonObject
        validateProviderInventory(inventory)
    }

    internal fun validateProviderInventory(inventory: kotlinx.serialization.json.JsonObject) {
        require("error" !in inventory) { "Cannot validate providers in external subscription" }
        require(inventory["hasProviderUrl"]?.toString() == "false") {
            "External subscriptions cannot contain provider URLs; import this subscription manually instead"
        }
    }
    private const val MAX_BYTES = 16L * 1024 * 1024
    private val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .dns(object : Dns { override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isEmpty() || addresses.any { !PublicProfileAddress.allowed(it) })
                throw java.net.UnknownHostException("External profile address is not public")
            return addresses
        } })
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS).build()

    suspend fun download(context: Context, source: String, destination: File): FetchStatus? {
        val userAgent = SettingsManager.get(context).getSnapshot().desired.globalUserAgent
            .ifBlank { "GlonetX-Android" }
        var url = PublicProfileAddress.parse(source)
        repeat(6) { hop ->
            currentCoroutineContext().ensureActive()
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            val response = client.newCall(request).await()
            response.use { reply ->
                if (reply.code in listOf(301, 302, 303, 307, 308)) {
                    if (hop == 5) throw IOException("Too many profile redirects")
                    val target = reply.header("Location")?.let(url::resolve)
                        ?: throw IOException("Invalid profile redirect")
                    if (url.isHttps && !target.isHttps) throw IOException("Profile HTTPS downgrade rejected")
                    url = PublicProfileAddress.parse(target.toString())
                } else {
                    if (!reply.isSuccessful) throw IOException("Profile download failed: HTTP ${reply.code}")
                    val body = reply.body ?: throw IOException("Empty profile response")
                    if (body.contentLength() > MAX_BYTES) throw IOException("Profile exceeds 16 MiB")
                    withContext(Dispatchers.IO) {
                        destination.parentFile?.mkdirs()
                        val temp = File.createTempFile("external-", ".download", destination.parentFile)
                        try {
                            body.byteStream().use { input ->
                                FileOutputStream(temp).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var size = 0L
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        size += count
                                        if (size > MAX_BYTES) throw IOException("Profile exceeds 16 MiB")
                                        output.write(buffer, 0, count)
                                    }
                                    if (size == 0L) throw IOException("Empty profile response")
                                    output.fd.sync()
                                }
                            }
                            if (!temp.renameTo(destination)) throw IOException("Could not commit downloaded profile")
                        } finally { temp.delete() }
                    }
                    return subscriptionInfo(reply)
                }
            }
        }
        throw IOException("Too many profile redirects")
    }

    private fun subscriptionInfo(response: Response): FetchStatus? {
        val header = response.header("subscription-userinfo")
        val interval = response.header("profile-update-interval")?.trim()?.toLongOrNull()
            ?.takeIf { it > 0 && it <= Long.MAX_VALUE / 3_600_000 }
            ?.times(3_600_000L)
        if (header == null && interval == null) return null
        val fields = header.orEmpty().split(';').mapNotNull {
            val pair = it.trim().split('=', limit = 2)
            if (pair.size == 2) pair[0] to pair[1].toLongOrNull()?.takeIf { value -> value >= 0 } else null
        }.toMap()
        return FetchStatus(FetchStatus.Action.SubscriptionInfo, emptyList(), -1, -1,
            fields["upload"], fields["download"], fields["total"],
            fields["expire"]?.takeIf { it <= Long.MAX_VALUE / 1000 }?.times(1000), interval)
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, resource, _ -> resource.close() }
            }
        })
    }
}
