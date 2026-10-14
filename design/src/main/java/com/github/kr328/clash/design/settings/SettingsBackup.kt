package com.github.kr328.clash.design.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Packs the user's data into one zip and unpacks it again.
 *
 * What is included:
 *   - `clash/`          — profiles, imported configs, geo databases
 *   - `shared_prefs/`   — every `SharedPreferences` file, so subscriptions,
 *                         overrides and the UI settings come back with it
 *
 * Restore deliberately writes into a staging area first and refuses entries
 * that would escape the app's data directory.
 */
object SettingsBackup {

    class BackupException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    suspend fun create(context: Context): ByteArray = withContext(Dispatchers.IO) {
        val roots = listOf("clash" to clashDir(context), "shared_prefs" to prefsDir(context))

        val output = ByteArrayOutputStream()

        ZipOutputStream(output).use { zip ->
            for ((name, dir) in roots) {
                if (!dir.isDirectory)
                    continue

                dir.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        val entryName = "$name/${file.relativeTo(dir).invariantSeparatorsPath}"

                        zip.putNextEntry(ZipEntry(entryName))

                        file.inputStream().use { it.copyTo(zip) }

                        zip.closeEntry()
                    }
            }

            zip.finish()
        }
        output.toByteArray()
    }

    suspend fun restore(context: Context, archive: ByteArray) = withContext(Dispatchers.IO) {
        val target = context.filesDir

        var written = 0

        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break

                val name = entry.name

                if (!entry.isDirectory) {
                    // zip-slip guard: every entry must stay inside filesDir
                    val destination = File(target, name).canonicalFile

                    if (!destination.path.startsWith(target.canonicalPath + File.separator)) {
                        throw BackupException("Refusing to restore outside the app directory: $name")
                    }

                    destination.parentFile?.mkdirs()

                    destination.outputStream().use { out ->
                        zip.copyTo(out)
                    }

                    written++
                }

                zip.closeEntry()
            }
        }

        if (written == 0)
            throw BackupException("The archive did not contain anything to restore")
    }

    /** `GlonetX-clash-backup-yyyyMMdd-HHmmss.zip` */
    fun backupFileName(now: Long = System.currentTimeMillis()): String {
        val format = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)

        return "GlonetX-clash-backup-${format.format(java.util.Date(now))}.zip"
    }

    private fun clashDir(context: Context): File = context.filesDir.resolve("clash")

    private fun prefsDir(context: Context): File =
        File(context.applicationInfo.dataDir).resolve("shared_prefs")
}
