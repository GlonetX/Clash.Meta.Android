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
        // `clash/...` lives under filesDir, `shared_prefs/...` next to it in
        // the data dir. Anything else in the archive is not ours to write.
        val filesRoot = context.filesDir.canonicalFile
        val dataRoot = File(context.applicationInfo.dataDir).canonicalFile

        var entries = 0
        var written = 0
        var totalBytes = 0L

        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break

                if (++entries > MAX_ENTRIES)
                    throw BackupException("Refusing to restore more than $MAX_ENTRIES entries")

                val name = entry.name.replace('\\', '/')

                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }

                val root = when {
                    name.startsWith("clash/") -> filesRoot
                    name.startsWith("shared_prefs/") -> dataRoot
                    else -> throw BackupException("Refusing to restore unexpected entry: $name")
                }

                // Reject empty, current and parent segments outright.
                val segments = name.split('/')

                if (segments.any { it.isEmpty() || it == "." || it == ".." })
                    throw BackupException("Refusing to restore unsafe path: $name")

                // zip-slip guard: every entry must stay inside its root
                val destination = File(root, name).canonicalFile

                if (!destination.path.startsWith(root.path + File.separator))
                    throw BackupException("Refusing to restore outside the app directory: $name")

                destination.parentFile?.mkdirs()

                try {
                    destination.outputStream().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                        while (true) {
                            val read = zip.read(buffer)

                            if (read < 0)
                                break

                            totalBytes += read

                            if (totalBytes > MAX_TOTAL_BYTES) {
                                throw BackupException(
                                    "Refusing to restore more than " +
                                        "${MAX_TOTAL_BYTES / 1024 / 1024} MiB"
                                )
                            }

                            out.write(buffer, 0, read)
                        }
                    }
                } catch (e: Exception) {
                    // Never leave a half-written file behind for the core to read.
                    destination.delete()

                    throw e
                }

                written++

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

    private const val MAX_ENTRIES = 4096
    private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
}
