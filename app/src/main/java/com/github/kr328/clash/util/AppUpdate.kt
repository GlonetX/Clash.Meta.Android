package com.github.kr328.clash.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
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
    private const val RELEASES_API = "https://api.github.com/repos/GlonetX/glonetx-android/releases"
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
        fun parts(value: String): List<Int>? {
            val match = Regex("^v?(\\d+(?:\\.\\d+){2,3})(?:[.-](?:Alpha|debug))*$", RegexOption.IGNORE_CASE)
                .matchEntire(value) ?: return null
            return match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
        }
        val a = parts(current) ?: return false
        val b = parts(remote) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return y > x
        }
        return false
    }

    /** Prereleases use a fixed tag, so their signed APK filename carries the version. */
    internal fun releaseVersion(tag: String, assetName: String): String? {
        val tagVersion = tag.removePrefix("v")
        if (tagVersion.matches(Regex("\\d+(?:\\.\\d+){2,3}"))) return tagVersion
        if (tag != "Prerelease-alpha") return null
        return Regex("^.+?-(\\d+(?:\\.\\d+){2,3})-alpha-.+\\.apk$", RegexOption.IGNORE_CASE)
            .matchEntire(assetName)?.groupValues?.get(1)
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

                val abi = if (Build.SUPPORTED_ABIS.isEmpty()) "arm64-v8a" else Build.SUPPORTED_ABIS[0]
                val preferred = if (abi == "armeabi-v7a") "armeabi-v7a" else abi

                val assets = release.optJSONArray("assets") ?: return@withContext null

                val asset = assets.firstAsset {
                    // exclude the `<apk>.sha256` sidecar assets — their names
                    // contain the ABI marker too
                    val name = it.optString("name")
                    name.endsWith(".apk", ignoreCase = true) && name.contains(preferred) &&
                        name.contains("-${com.github.kr328.clash.BuildConfig.FLAVOR}-", ignoreCase = true)
                } ?: return@withContext null

                val versionName = releaseVersion(tagName, asset.optString("name")) ?: return@withContext null
                if (!isNewer(currentVersionName(context), versionName)) return@withContext null

                val apkUrl = asset.optString("browser_download_url")

                if (!apkUrl.startsWith("https://")) {
                    Log.w("Refusing to download update from a non-HTTPS URL: $apkUrl")

                    return@withContext null
                }

                // The release workflows attach a `<apk>.sha256` file next to
                // each APK. Fail closed when the checksum is missing: an
                // unverifiable download must never be installed.
                val apkSha256 = assets
                    .firstAsset { it.optString("name") == "${asset.optString("name")}.sha256" }
                    ?.let { fetchSha256(it.optString("browser_download_url")) }

                if (apkSha256 == null) {
                    Log.w("No published checksum for ${versionName}; refusing the update")

                    return@withContext null
                }

                Release(
                    tagName = tagName,
                    versionName = versionName,
                    apkUrl = apkUrl,
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
        if (!url.startsWith("https://")) {
            Log.w("Refusing to fetch an update checksum over a non-HTTPS URL: $url")

            return null
        }

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

                    val safeVersion = release.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")

                    val cacheRoot = context.cacheDir.canonicalFile
                    val file = File(context.cacheDir, "update-$safeVersion.apk").canonicalFile

                    if (!file.path.startsWith(cacheRoot.path + File.separator)) {
                        Log.w("Refusing to download an update outside the cache directory: ${file.path}")

                        return@withContext null
                    }

                    // Hash while copying — no second pass over the file.
                    val digest = MessageDigest.getInstance("SHA-256")

                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            val total = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
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
                        Log.w("No published checksum for ${release.versionName}; refusing the download")

                        file.delete()

                        return@withContext null
                    }

                    val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }

                    if (!actual.equals(expected, ignoreCase = true)) {
                        Log.w("APK checksum mismatch for ${release.versionName}: expected $expected, got $actual")

                        file.delete()

                        return@withContext null
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

    /**
     * Verifies that the downloaded archive is a newer build of this very app,
     * signed by the same certificate(s) as the installed package. Without this
     * an attacker who can replace the cached file between the checksum check
     * and the install prompt could hand us an arbitrary APK.
     */
    private fun verifyDownloadedApk(context: Context, file: File): Boolean {
        return try {
            if (!file.isFile) {
                Log.w("Refusing to install a missing update file: ${file.path}")

                return false
            }

            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= 28)
                PackageManager.GET_SIGNING_CERTIFICATES
            else
                PackageManager.GET_SIGNATURES

            val archive = packageArchiveInfo(pm, file.absolutePath, flags)

            if (archive == null) {
                Log.w("Unable to read the downloaded APK: ${file.path}")

                return false
            }

            if (archive.packageName != context.packageName) {
                Log.w("Update package name mismatch: ${archive.packageName}")

                return false
            }

            val installed = installedPackageInfo(pm, context.packageName, flags)

            if (installed == null) {
                Log.w("Unable to read the installed package info")

                return false
            }

            if (versionCodeOf(archive) < versionCodeOf(installed)) {
                Log.w("Update version ${versionCodeOf(archive)} is older than the installed ${versionCodeOf(installed)}")

                return false
            }

            val archiveSigners = signerDigests(archive)
            val installedSigners = signerDigests(installed)

            if (archiveSigners.isEmpty() || archiveSigners != installedSigners) {
                Log.w("Update signer mismatch: $archiveSigners != $installedSigners")

                return false
            }

            true
        } catch (e: Exception) {
            Log.w("Verify downloaded APK failed: ${e.message}")

            false
        }
    }

    @Suppress("DEPRECATION")
    private fun packageArchiveInfo(pm: PackageManager, path: String, flags: Int): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33)
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else
            pm.getPackageArchiveInfo(path, flags)

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(pm: PackageManager, packageName: String, flags: Int): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33)
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else
            pm.getPackageInfo(packageName, flags)

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    /**
     * SHA-256 of every signer certificate. API 28+ exposes the full signer set
     * through [android.content.pm.SigningInfo], so multi-signed builds compare
     * correctly; older platforms only ever have one signature.
     */
    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures: List<Signature> = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }

        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
    }

    fun installApk(context: Context, file: File): Boolean {
        if (!verifyDownloadedApk(context, file)) {
            Log.w("Refusing to install an unverified update: ${file.path}")

            return false
        }

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
