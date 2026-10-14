package com.github.kr328.clash.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.design.settings.UpdateChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object AppUpdate {
    private const val RELEASES_API = "https://api.github.com/repos/GlonetX/Clash.Meta.Android/releases"
    private const val LAST_CHECK_PREF = "app_update_last_check"
    private val CHECK_INTERVAL = TimeUnit.HOURS.toMillis(24)

    data class Release(
        val tagName: String,
        val versionName: String,
        val apkUrl: String,
        val apkSize: Long,
        val notes: String,
        /** Expected SHA-256 of [apkUrl], or null when the release publishes none. */
        val apkSha256: String? = null,
    )

    fun isAutoCheckDue(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
        val last = prefs.getLong(LAST_CHECK_PREF, 0)

        return System.currentTimeMillis() - last > CHECK_INTERVAL
    }

    fun markChecked(context: Context) {
        context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
            .edit()
            .putLong(LAST_CHECK_PREF, System.currentTimeMillis())
            .apply()
    }

    fun currentVersionName(context: Context): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }

    /**
     * Compares dotted version strings segment by segment, numerically.
     */
    fun isNewer(current: String, remote: String): Boolean {
        val a = current.split('.').map { it.toIntOrNull() ?: 0 }
        val b = remote.split('.').map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrNull(i) ?: 0
            val y = b.getOrNull(i) ?: 0

            if (x != y)
                return y > x
        }

        return false
    }

    suspend fun checkUpdate(
        context: Context,
        channel: UpdateChannel = UpdateChannel.Stable,
    ): Release? {
        return withContext(Dispatchers.IO) {
            try {
                val release = fetchRelease(channel) ?: return@withContext null

                // The stable channel never offers prereleases or drafts; the
                // prerelease channel selected one explicitly above.
                if (channel != UpdateChannel.Prerelease &&
                    (release.optBoolean("prerelease") || release.optBoolean("draft"))
                )
                    return@withContext null

                val tagName = release.optString("tag_name")
                val versionName = tagName.removePrefix("v")

                if (versionName.isBlank() || !isNewer(currentVersionName(context), versionName))
                    return@withContext null

                val abi = if (Build.SUPPORTED_ABIS.isEmpty()) "arm64-v8a" else Build.SUPPORTED_ABIS[0]
                val preferred = if (abi == "armeabi-v7a") "armeabi-v7a" else abi

                val assets = release.optJSONArray("assets") ?: return@withContext null

                val asset = assets.firstAsset {
                    // exclude the `<apk>.sha256` sidecar assets — their names
                    // contain the ABI marker too
                    it.optString("name").endsWith(".apk") && it.optString("name").contains(preferred)
                } ?: return@withContext null

                // The release workflows attach a `<apk>.sha256` file next to
                // each APK. Releases published before that carry no checksum.
                val apkSha256 = assets.firstAsset { it.optString("name") == "${asset.optString("name")}.sha256" }
                    ?.let { fetchSha256(it.optString("browser_download_url")) }

                Release(
                    tagName = tagName,
                    versionName = versionName,
                    apkUrl = asset.optString("browser_download_url"),
                    apkSize = asset.optLong("size"),
                    notes = release.optString("body"),
                    apkSha256 = apkSha256,
                )
            } catch (e: Exception) {
                Log.w("Check update failed: ${e.message}")

                null
            }
        }
    }

    /**
     * Stable: the latest published release (`/latest` skips prereleases and
     * drafts by itself). Prerelease: the newest prerelease from the release
     * list, falling back to the latest stable release when none exists.
     */
    private fun fetchRelease(channel: UpdateChannel): JSONObject? {
        if (channel != UpdateChannel.Prerelease)
            return fetchReleaseJson("$RELEASES_API/latest")

        val releases = fetchBody(RELEASES_API)?.let { body ->
            try {
                JSONArray(body)
            } catch (e: Exception) {
                null
            }
        } ?: return null

        val prerelease = (0 until releases.length()).asSequence()
            .map { releases.optJSONObject(it) }
            .firstOrNull { it != null && it.optBoolean("prerelease") && !it.optBoolean("draft") }

        return prerelease ?: fetchReleaseJson("$RELEASES_API/latest")
    }

    private fun fetchReleaseJson(url: String): JSONObject? {
        return fetchBody(url)?.let { body ->
            try {
                JSONObject(body)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun fetchBody(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection

        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "clash.meta.android")

        try {
            if (connection.responseCode != 200)
                return null

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray.firstAsset(predicate: (JSONObject) -> Boolean): JSONObject? {
        return (0 until length()).asSequence()
            .map { optJSONObject(it) }
            .firstOrNull { it != null && predicate(it) }
    }

    /** Hex digest from a `sha256sum`-style asset: `<hex>  <filename>`. */
    private fun fetchSha256(url: String): String? {
        val hex = fetchBody(url)?.trim()?.split(Regex("\\s+"))?.firstOrNull()

        if (hex == null || !hex.matches(Regex("[0-9a-fA-F]{64}"))) {
            Log.w("Invalid published checksum for update: $hex")

            return null
        }

        return hex.lowercase()
    }

    suspend fun downloadApk(context: Context, release: Release, onProgress: (Int) -> Unit): File? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(release.apkUrl).openConnection() as HttpURLConnection

                connection.connectTimeout = 15000
                connection.readTimeout = 60000
                connection.setRequestProperty("User-Agent", "clash.meta.android")

                try {
                    if (connection.responseCode != 200)
                        return@withContext null

                    val file = File(context.cacheDir, "update-${release.versionName}.apk")

                    // Hash while copying — no second pass over the file.
                    val digest = MessageDigest.getInstance("SHA-256")

                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            val total = connection.contentLengthLong
                            var copied = 0L
                            val buffer = ByteArray(64 * 1024)

                            while (true) {
                                val read = input.read(buffer)

                                if (read < 0)
                                    break

                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)

                                copied += read

                                if (total > 0)
                                    onProgress((copied * 100 / total).toInt())
                            }
                        }
                    }

                    val expected = release.apkSha256

                    if (expected == null) {
                        // Releases published before checksums were attached;
                        // keep the install but make the skip visible.
                        Log.i("No published checksum for ${release.versionName}; skipping APK verification")
                    } else {
                        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }

                        if (!actual.equals(expected, ignoreCase = true)) {
                            Log.w("APK checksum mismatch for ${release.versionName}: expected $expected, got $actual")

                            file.delete()

                            return@withContext null
                        }
                    }

                    file
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.w("Download update failed: ${e.message}")

                null
            }
        }
    }

    fun installApk(context: Context, file: File): Boolean {
        return try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= 24) {
                FileProvider.getUriForFile(context, context.packageName + ".update.provider", file)
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            context.startActivity(intent)

            true
        } catch (e: Exception) {
            Log.w("Install update failed: ${e.message}")

            false
        }
    }
}
