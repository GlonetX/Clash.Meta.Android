package com.github.kr328.clash.service.backup

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import android.util.Xml
import androidx.room.withTransaction
import com.github.kr328.clash.core.bridge.BackupBridge
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.service.ClashService
import com.github.kr328.clash.service.TunService
import com.github.kr328.clash.service.ProfileReceiver
import com.github.kr328.clash.service.clash.awaitClashRuntimeStopped
import com.github.kr328.clash.service.data.*
import com.github.kr328.clash.service.settings.SettingsManager
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.sendProfileChanged
import com.github.kr328.clash.service.util.sendBroadcastSelf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable internal data class BackupSnapshot(
    val version: Int = 1,
    val imported: List<Imported>,
    val pending: List<Pending>,
    val selections: List<Selection>,
    val activeProfile: String?,
    val settings: String,
    val override: String,
)
@Serializable internal data class ArchiveEntry(val size: Long, val sha256: String)
@Serializable internal data class ArchiveManifest(val version: Int = 1, val createdAt: Long = System.currentTimeMillis(), val entries: Map<String, ArchiveEntry>)
@Serializable private data class RestoreJournal(val phase: String, val importedExisted: Boolean, val pendingExisted: Boolean, val overrideExisted: Boolean)
internal data class RestoreInfo(val imported: Int, val pending: Int, val legacy: Boolean)

/** Preserve user-managed files while omitting known remote provider caches. */
internal fun includeProfileFile(relative: String, remotePaths: Set<String>): Boolean =
    relative == "config.yaml" || (relative.startsWith("providers/") && relative !in remotePaths)

/** File algorithm owned by :background. UI never obtains an app-data path. */
internal object BackupArchive {
    private val json = Json { encodeDefaults = true }
    fun root(context: Context) = File(context.filesDir, "backup-maintenance").apply { mkdirs() }
    fun jobDirectory(context: Context, id: String): File {
        require(UUID.fromString(id).toString() == id) { "Invalid backup job ID" }
        return File(root(context), "jobs/$id").apply { mkdirs() }
    }
    private fun overrideFile(context: Context) = File(context.filesDir, "clash/override.json")

    private suspend fun snapshot(context: Context, recovery: Boolean = false): BackupSnapshot {
        val manager = SettingsManager.get(context)
        return Database.database.withTransaction {
            val imported = ImportedDao().queryAll()
            BackupSnapshot(imported = imported, pending = PendingDao().queryAll(),
                selections = SelectionDao().queryAll(), activeProfile = ServiceStore(context).activeProfile
                    ?.takeIf { active -> imported.any { it.uuid == active } }?.toString(),
                settings = if (recovery) manager.exportRecoverySettings() else manager.exportPortableSettings(),
                override = overrideFile(context).takeIf(File::isFile)?.let { readLimited(it, BackupLimits.METADATA) } ?: "{}")
        }
    }

    suspend fun create(context: Context, work: File): File = withContext(Dispatchers.IO) {
        val stage = File(work, "snapshot").apply { deleteRecursively(); mkdirs() }
        DataMaintenanceCoordinator.exclusive(context) {
            DataMaintenanceCoordinator.requireNoWriters()
            BackupBridge.beginSnapshot()
            try {
                val data = snapshot(context)
                val body = json.encodeToString(data)
                require(body.toByteArray().size <= BackupLimits.METADATA) { "Backup metadata exceeds 8 MiB" }
                File(stage, "snapshot.json").writeText(body)
                var copied = body.toByteArray().size.toLong()
                var entries = 1
                for (kind in listOf("imported", "pending")) {
                    val records = if (kind == "imported") data.imported.map { Triple(it.uuid, it.ageSecretKey, false) }
                        else data.pending.map { Triple(it.uuid, it.ageSecretKey, true) }
                    for ((uuid, key, draft) in records) {
                        val source = File(context.filesDir, "$kind/$uuid")
                        require(source.isDirectory) { "Missing profile files for $uuid" }
                        val config = File(source, "config.yaml")
                        require(config.isFile) { "Missing configuration for $uuid" }
                        val remotePaths = if (draft && config.length() == 0L) emptySet() else {
                            val parsed = json.parseToJsonElement(BackupBridge.providerInventory(config.path, key ?: "", data.override)) as JsonObject
                            require("error" !in parsed) { parsed["error"]?.jsonPrimitive?.content ?: "Invalid provider inventory" }
                            val local = parsed["localPaths"]!!.jsonArray.map { it.jsonPrimitive.content }
                            local.forEach { require(File(source, it).isFile) { "Missing local provider $it" } }
                            parsed["remotePaths"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
                        }
                        for (file in source.walkTopDown().filter(File::isFile)) {
                            val relative = file.relativeTo(source).invariantSeparatorsPath
                            if (!includeProfileFile(relative, remotePaths)) continue
                            // /data/user/0 itself may resolve through an Android system symlink.
                            // Compare relative to the canonical profile root instead of /data/user/0.
                            val expected = File(source.canonicalFile, relative).absolutePath
                            require(file.canonicalPath == expected) { "Symbolic links cannot be backed up" }
                            require(++entries <= BackupLimits.ENTRIES) { "Too many backup files" }
                            val target = File(stage, "$kind/$uuid/$relative").apply { parentFile?.mkdirs() }
                            copied += copyLimited(file, target, BackupLimits.FILE)
                            require(copied <= BackupLimits.EXPANDED) { "Backup exceeds 512 MiB expanded size" }
                        }
                    }
                }
            } finally { BackupBridge.endSnapshot() }
        }
        val entries = stage.walkTopDown().filter(File::isFile).associate {
            it.relativeTo(stage).invariantSeparatorsPath to ArchiveEntry(it.length(), digest(it))
        }
        val manifest = json.encodeToString(ArchiveManifest(entries = entries))
        require(manifest.toByteArray().size <= BackupLimits.METADATA) { "Backup manifest too large" }
        val archive = File(work, "archive.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray()); zip.closeEntry()
            for (name in entries.keys.sorted()) {
                currentCoroutineContext().ensureActive()
                zip.putNextEntry(ZipEntry(name)); File(stage, name).inputStream().use { it.copyTo(zip, 64 * 1024) }; zip.closeEntry()
            }
        }
        require(archive.length() <= BackupLimits.TRANSFER) { "Compressed backup exceeds 256 MiB" }
        stage.deleteRecursively()
        archive
    }

    suspend fun prepare(context: Context, archive: File, work: File): RestoreInfo = withContext(Dispatchers.IO) {
        require(archive.length() <= BackupLimits.TRANSFER) { "Archive exceeds 256 MiB" }
        val stage = File(work, "restore").apply { deleteRecursively(); mkdirs() }
        val names = hashSetOf<String>(); var total = 0L
        ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(names.add(name) && names.size <= BackupLimits.ENTRIES) { "Duplicate entry or excessive backup entries" }
                require(name.length <= 1024 && '\\' !in name && !name.startsWith('/') && name.split('/').filter(String::isNotEmpty).none { it == "." || it == ".." }) { "Unsafe archive path" }
                if (entry.isDirectory) { zip.closeEntry(); continue }
                require(name.split('/').none(String::isEmpty)) { "Unsafe archive path" }
                val file = File(stage, name).canonicalFile
                require(file.path.startsWith(stage.canonicalPath + File.separator)) { "Unsafe archive path" }
                file.parentFile?.mkdirs()
                file.outputStream().use { out ->
                    var size = 0L; val buffer = ByteArray(64 * 1024)
                    while (true) { val n = zip.read(buffer); if (n < 0) break
                        size += n; total += n
                        require(size <= BackupLimits.FILE && total <= BackupLimits.EXPANDED) { "Archive exceeds expanded size limits" }
                        out.write(buffer, 0, n)
                    }
                    out.fd.sync()
                }
                zip.closeEntry()
            }
        }
        val manifestFile = File(stage, "manifest.json")
        if (!manifestFile.isFile) {
            require(names.any { it.startsWith("shared_prefs/") || it == "clash/override.json" }) { "Unrecognized legacy backup" }
            File(work, "legacy").writeText("1")
            // Parse only an allowlist. XML is never installed as SharedPreferences.
            legacySettings(context, stage)
            File(stage, "clash/override.json").takeIf(File::isFile)?.let { validateOverride(readLimited(it, BackupLimits.METADATA)) }
            return@withContext RestoreInfo(0, 0, true)
        }
        val manifest = json.decodeFromString<ArchiveManifest>(readLimited(manifestFile, BackupLimits.METADATA))
        require(manifest.version == 1 && manifest.entries.size < BackupLimits.ENTRIES) { "Unsupported backup version" }
        val actual = stage.walkTopDown().filter(File::isFile).map { it.relativeTo(stage).invariantSeparatorsPath }.toSet() - "manifest.json"
        require(actual == manifest.entries.keys) { "Backup manifest does not match archive files" }
        for ((name, expected) in manifest.entries) {
            val file = File(stage, name)
            require(file.length() == expected.size && digest(file) == expected.sha256) { "Backup integrity check failed" }
        }
        val data = readSnapshot(File(stage, "snapshot.json"))
        validateSnapshot(context, data, stage)
        RestoreInfo(data.imported.size, data.pending.size, false)
    }

    private fun validateSnapshot(context: Context, data: BackupSnapshot, stage: File) {
        require(data.version == 1) { "Unsupported snapshot version" }
        require(data.imported.map { it.uuid }.distinct().size == data.imported.size && data.pending.map { it.uuid }.distinct().size == data.pending.size) { "Duplicate profile IDs" }
        val ids = data.imported.map { it.uuid }.toSet()
        require(data.selections.all { it.uuid in ids } && data.selections.map { it.uuid to it.proxy }.distinct().size == data.selections.size) { "Invalid proxy selections" }
        require(data.activeProfile == null || UUID.fromString(data.activeProfile) in ids) { "Invalid active profile" }
        val validPrefixes = data.imported.map { "imported/${it.uuid}/" } + data.pending.map { "pending/${it.uuid}/" }
        stage.walkTopDown().filter(File::isFile).forEach { file ->
            val name = file.relativeTo(stage).invariantSeparatorsPath
            require(name == "manifest.json" || name == "snapshot.json" || validPrefixes.any { prefix ->
                name == prefix + "config.yaml" || name.startsWith(prefix + "providers/") }) { "Unexpected backup entry" }
        }
        for (prefix in validPrefixes) require(File(stage, prefix + "config.yaml").isFile) { "Missing profile configuration" }
        require(data.imported.all { it.name.isNotBlank() && it.interval >= 0 } && data.pending.all { it.name.isNotBlank() && it.interval >= 0 }) { "Invalid profile metadata" }
        SettingsManager.get(context).validatePortableSettings(data.settings)
        validateOverride(data.override)
        val profiles = data.imported.map { Triple("imported/${it.uuid}", it.ageSecretKey, false) } +
            data.pending.map { Triple("pending/${it.uuid}", it.ageSecretKey, true) }
        for ((prefix, key, draft) in profiles) {
            val config = File(stage, "$prefix/config.yaml")
            if (draft && config.length() == 0L) continue
            val inventory = json.parseToJsonElement(BackupBridge.providerInventory(config.path, key ?: "", data.override)) as JsonObject
            require("error" !in inventory) { "Invalid profile provider configuration" }
            val restricted = if (draft) data.pending.any { "pending/${it.uuid}" == prefix && it.sourceRestricted }
                else data.imported.any { "imported/${it.uuid}" == prefix && it.sourceRestricted }
            if (restricted) com.github.kr328.clash.service.security.ExternalProfileDownload.validateProviderInventory(inventory)
            val local = inventory["localPaths"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
            local.forEach { require(File(stage, "$prefix/$it").isFile) { "Missing local provider $it" } }
            val remote = inventory["remotePaths"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
            val providers = File(stage, "$prefix/providers")
            providers.walkTopDown().filter(File::isFile).forEach {
                val relative = it.relativeTo(File(stage, prefix)).invariantSeparatorsPath
                require(includeProfileFile(relative, remote)) { "Backup contains a downloadable provider cache" }
            }
        }
    }

    suspend fun commit(context: Context, work: File) = withContext(Dispatchers.IO) {
        DataMaintenanceCoordinator.exclusive(context) {
            DataMaintenanceCoordinator.requireNoWriters()
            val stage = File(work, "restore")
            val legacy = File(work, "legacy").exists()
            // Revalidate staged data before stopping VPN or mutating live state.
            val incoming = if (legacy) null else readSnapshot(File(stage, "snapshot.json")).also { validateSnapshot(context, it, stage) }
            val legacyPortable = if (legacy) legacySettings(context, stage) else null
            val old = snapshot(context, recovery = true)
            val rollback = File(root(context), "rollback").apply { deleteRecursively(); mkdirs() }
            atomicWrite(File(rollback, "snapshot.json"), json.encodeToString(old))
            val journal = RestoreJournal("Prepared", File(context.filesDir, "imported").exists(), File(context.filesDir, "pending").exists(), overrideFile(context).exists())
            DataMaintenanceCoordinator.restoreInProgress = true
            try {
                withContext(Dispatchers.Main) {
                    context.sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_STOP))
                    context.stopService(Intent(context, TunService::class.java))
                    context.stopService(Intent(context, ClashService::class.java))
                }
                withTimeout(30_000) { awaitClashRuntimeStopped() }
                // No cancellation once the write-ahead journal is durable.
                withContext(NonCancellable) {
                    atomicWrite(File(root(context), "restore-journal.json"), json.encodeToString(journal))
                    if (!legacy) {
                        for (kind in listOf("imported", "pending")) {
                            val live = File(context.filesDir, kind)
                            if (live.exists()) check(live.renameTo(File(rollback, kind))) { "Cannot preserve current profile files" }
                            val replacement = File(stage, kind).apply { mkdirs() }
                            check(replacement.renameTo(live)) { "Cannot install restored profile files" }
                        }
                        replaceRows(incoming!!)
                        SettingsManager.get(context).importPortableSettings(incoming.settings)
                        commitActive(context, incoming.activeProfile)
                        atomicWrite(overrideFile(context), incoming.override)
                    } else {
                        SettingsManager.get(context).importPortableSettings(legacyPortable!!)
                        File(stage, "clash/override.json").takeIf(File::isFile)?.let { atomicWrite(overrideFile(context), readLimited(it, BackupLimits.METADATA)) }
                    }
                    atomicWrite(File(root(context), "restore-journal.json"), json.encodeToString(journal.copy(phase = "Committed")))
                    rescheduleRestoredProfiles(context, old)
                    check(File(root(context), "restore-journal.json").delete()) { "Cannot finish restore recovery" }
                    rollback.deleteRecursively()
                }
            } catch (e: Throwable) {
                withContext(NonCancellable) {
                    recover(context)
                    rollback.deleteRecursively()
                }
                throw e
            } finally { DataMaintenanceCoordinator.restoreInProgress = false }
            ServiceStore(context).activeProfile?.let { context.sendProfileChanged(it) }
        }
    }

    /** Called under maintenance gate before any normal data entrypoint. */
    suspend fun recover(context: Context) {
        val journalFile = File(root(context), "restore-journal.json")
        if (!journalFile.exists() && !File(journalFile.path + ".bak").exists()) return
        DataMaintenanceCoordinator.restoreInProgress = true
        try {
            val journal = json.decodeFromString<RestoreJournal>(readLimited(journalFile, BackupLimits.METADATA))
            val rollback = File(root(context), "rollback")
            val old = readSnapshot(File(rollback, "snapshot.json"))
            if (journal.phase != "Committed") {
                for ((kind, existed) in listOf("imported" to journal.importedExisted, "pending" to journal.pendingExisted)) {
                    val preserved = File(rollback, kind); val live = File(context.filesDir, kind)
                    if (preserved.exists()) { check(!live.exists() || live.deleteRecursively()); check(preserved.renameTo(live)) { "Cannot roll back profile files" } }
                    else if (!existed) check(!live.exists() || live.deleteRecursively())
                }
                replaceRows(old)
                SettingsManager.get(context).importRecoverySettings(old.settings)
                commitActive(context, old.activeProfile)
                if (journal.overrideExisted) atomicWrite(overrideFile(context), old.override) else overrideFile(context).delete()
            }
            rescheduleRestoredProfiles(context, old)
            check(journalFile.delete()) { "Cannot finish restore recovery" }
            rollback.deleteRecursively()
        } finally { DataMaintenanceCoordinator.restoreInProgress = false }
    }

    private suspend fun rescheduleRestoredProfiles(context: Context, old: BackupSnapshot) {
        rescheduleProfiles(old.imported, ImportedDao().queryAll(),
            { ProfileReceiver.cancelNext(context, it) }, { ProfileReceiver.scheduleNext(context, it) })
    }

    private suspend fun replaceRows(data: BackupSnapshot) = Database.database.withTransaction {
        SelectionDao().clearAllRows(); PendingDao().clearAllRows(); ImportedDao().clearAllRows()
        ImportedDao().insertAll(data.imported); PendingDao().insertAll(data.pending); SelectionDao().insertAll(data.selections)
    }

    private suspend fun legacySettings(context: Context, stage: File): String {
        var total = 0L
        val files = File(stage, "shared_prefs").listFiles()?.filter { it.isFile && it.extension == "xml" }?.associate { file ->
            total += file.length()
            require(total <= BackupLimits.METADATA) { "Legacy preferences exceed 8 MiB" }
            file.name to readLimited(file, BackupLimits.METADATA)
        } ?: emptyMap()
        return SettingsManager.get(context).migrateLegacyPreferences(files)
    }
    private fun validateOverride(value: String) { require(json.parseToJsonElement(value) is JsonObject) { "Invalid persistent override" } }
    private fun commitActive(context: Context, value: String?) {
        check(context.getSharedPreferences("service", Context.MODE_PRIVATE).edit().putString("active_profile", value ?: "").commit()) { "Cannot save active profile" }
    }
    private fun readSnapshot(file: File) = json.decodeFromString<BackupSnapshot>(readLimited(file, BackupLimits.METADATA))
    private fun readLimited(file: File, max: Long): String { require(file.length() <= max) { "Metadata is too large" }; return AtomicFile(file).openRead().use { BoundedInputStream(it, max).reader().readText() } }
    private fun digest(file: File): String { val md = MessageDigest.getInstance("SHA-256"); file.inputStream().use { input -> val b = ByteArray(64 * 1024); while(true) { val n = input.read(b); if(n<0)break; md.update(b,0,n) } }; return md.digest().joinToString("") { "%02x".format(it) } }
    private fun copyLimited(source: File, target: File, max: Long): Long {
        require(source.length() <= max) { "Backup file exceeds 64 MiB" }
        var count = 0L
        source.inputStream().use { input -> target.outputStream().use { out -> val b = ByteArray(64 * 1024); while(true) { val n=input.read(b);if(n<0)break;count+=n;require(count<=max);out.write(b,0,n) }; out.fd.sync() } }
        return count
    }
    internal fun atomicWrite(file: File, value: String) {
        file.parentFile?.mkdirs(); val atomic = AtomicFile(file); val out = atomic.startWrite()
        try { out.write(value.toByteArray()); atomic.finishWrite(out) } catch(e: Throwable) { atomic.failWrite(out); throw e }
    }
}
