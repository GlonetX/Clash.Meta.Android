package com.github.kr328.clash.service.backup

import android.content.Context
import android.content.Intent
import android.os.Build
import com.github.kr328.clash.core.bridge.BackupBridge
import com.github.kr328.clash.service.remote.IBackupManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Binder facade. Work belongs to BackupWorker, not to the bound UI service. */
class BackupManager(context: Context) : IBackupManager {
    companion object {
        @Volatile private var instance: BackupManager? = null
        fun get(context: Context): BackupManager = instance ?: synchronized(this) {
            instance ?: BackupManager(context.applicationContext).also { instance = it }
        }
    }
    private val engine = BackupEngine.get(context)
    override suspend fun getConfig(): BackupConfig = withContext(Dispatchers.IO) { engine.credentials.config() }
    override suspend fun saveConfig(config: BackupConfig, password: String?) = withContext(Dispatchers.IO) { engine.credentials.save(config, password) }
    override suspend fun listRemote(): List<BackupRemoteFile> = withContext(Dispatchers.IO) { engine.client().list() }
    override suspend fun startBackup(passphrase: ByteArray): String = engine.enqueue("Backup", "", passphrase)
    override suspend fun prepareRestore(remoteName: String, passphrase: ByteArray): String = engine.enqueue("Restore", remoteName, passphrase)
    override suspend fun commitRestore(jobId: String) = engine.commit(jobId)
    override suspend fun getJob(jobId: String): BackupJob? = withContext(Dispatchers.IO) { engine.read(jobId) }
    override suspend fun getLatestJob(): BackupJob? = withContext(Dispatchers.IO) { engine.latest() }
    override suspend fun cancel(jobId: String) = engine.cancel(jobId)
}

internal class BackupEngine private constructor(private val context: Context) {
    val credentials = BackupCredentials(context)
    private val admission = Mutex()
    private val secrets = ConcurrentHashMap<String, ByteArray>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val clients = ConcurrentHashMap<String, WebDavClient>()
    private val json = Json { encodeDefaults = true }
    private var active: String? = null

    init {
        // Never resume a password operation or leave a plaintext stage after a
        // process restart. Persistent records remain available to the UI.
        File(BackupArchive.root(context), "jobs").listFiles()?.filter(File::isDirectory)?.forEach { dir ->
            val id = dir.name
            runCatching { read(id) }.getOrNull()?.let { previous ->
                if (!previous.terminal) write(previous.copy(phase = "Failed", message = "Interrupted by process restart; enter the backup password and retry"))
                cleanData(dir)
            }
        }
    }
    fun client() = WebDavClient(credentials.config(), credentials.password())

    suspend fun enqueue(operation: String, name: String, password: ByteArray): String = withContext(Dispatchers.IO) {
        admission.withLock {
            require(active == null) { "A backup or restore is already active" }
            WebDavClient.validate(credentials.config())
            if (operation == "Restore") require(name.endsWith(".gxbackup.age") || name.endsWith(".zip")) { "Unsupported backup file" }
            val legacy = name.endsWith(".zip")
            if (!legacy) {
                val value = password.toString(Charsets.UTF_8)
                require(value.codePointCount(0, value.length) >= 16 && password.size <= 1024) { "Use at least 16 characters for the backup password" }
            }
            val id = UUID.randomUUID().toString()
            write(BackupJob(id, operation, remoteName = name, legacy = legacy))
            secrets[id] = password.copyOf()
            active = id
            try { startWorker(id) } catch (e: Exception) {
                secrets.remove(id)?.fill(0); active = null
                update(id, "Failed", message = "Could not start background backup")
                throw e
            }
            id
        }
    }

    suspend fun commit(id: String) = withContext(Dispatchers.IO) {
        // Ready becomes visible before the preparation worker's finally block
        // releases its job. Wait outside the admission lock so that cleanup can
        // finish before a second foreground-service start is dispatched.
        jobs[id]?.join()
        admission.withLock {
            val job = read(id) ?: error("Backup job not found")
            require(active == id && job.operation == "Restore" && job.phase == "Ready") { "Restore is not ready" }
            update(id, "Committing", 90, "Restoring data; VPN will remain stopped")
            try { startWorker(id) } catch (e: Exception) { update(id, "Ready", 80, "Could not start restore; retry confirmation"); throw e }
        }
    }

    suspend fun cancel(id: String) = withContext(Dispatchers.IO) {
        admission.withLock {
            val job = read(id) ?: return@withLock
            require(job.phase != "Committing") { "Restore commit cannot be cancelled" }
            if (job.terminal) return@withLock
            clients[id]?.cancel()
            val running = jobs[id]
            if (running != null) running.cancel()
            else {
                secrets.remove(id)?.fill(0)
                update(id, "Cancelled", message = "Cancelled")
                cleanData(BackupArchive.jobDirectory(context, id))
                if (active == id) active = null
            }
        }
    }

    /** Called once by the started foreground worker for a persisted job. */
    fun execute(id: String, scope: CoroutineScope, completed: () -> Unit) {
        if (jobs.containsKey(id)) { completed(); return }
        val task = scope.launch(start = CoroutineStart.LAZY) {
            val work = BackupArchive.jobDirectory(context, id)
            var password = secrets.remove(id)
            try {
                val record = read(id) ?: error("Backup job not found")
                DataMaintenanceCoordinator.ensureReady(context)
                if (record.phase == "Committing") {
                    BackupArchive.commit(context, work)
                    update(id, "Completed", 100, if (record.legacy) "Legacy settings imported; subscriptions were preserved. VPN is stopped." else "Backup restored. VPN is stopped; reconnect when ready.")
                } else if (record.operation == "Backup") {
                    val secret = password ?: error("Backup password is no longer available; retry")
                    update(id, "Snapshotting", 10, "Creating a consistent snapshot without interrupting VPN")
                    val archive = BackupArchive.create(context, work)
                    currentCoroutineContext().ensureActive()
                    update(id, "Encrypting", 50, "Encrypting backup")
                    val encrypted = File(work, "backup.gxbackup.age")
                    BackupBridge.cryptFile(archive.path, encrypted.path, secret, false)?.let { error(it) }
                    secret.fill(0); password = null; archive.delete()
                    currentCoroutineContext().ensureActive()
                    update(id, "Uploading", 75, "Uploading encrypted backup")
                    val remote = "GlonetX-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())}-${id.take(8)}.gxbackup.age"
                    val client = client(); clients[id] = client
                    client.upload(remote, encrypted)
                    currentCoroutineContext().ensureActive()
                    write(read(id)!!.copy(phase = "Completed", progress = 100, message = "Encrypted backup uploaded", remoteName = remote))
                } else {
                    update(id, "Downloading", 10, "Downloading backup")
                    val download = File(work, "download")
                    val client = client(); clients[id] = client
                    client.download(record.remoteName, download)
                    val archive = if (record.legacy) download else {
                        update(id, "Decrypting", 40, "Authenticating encrypted backup")
                        val file = File(work, "decrypted.zip")
                        BackupBridge.cryptFile(download.path, file.path, password ?: error("Enter the backup password again"), true)?.let { error(it) }
                        file
                    }
                    password?.fill(0); password = null
                    currentCoroutineContext().ensureActive()
                    update(id, "Validating", 60, "Validating files and settings")
                    val info = BackupArchive.prepare(context, archive, work)
                    archive.delete(); download.delete()
                    write(read(id)!!.copy(phase = "Ready", progress = 80, importedCount = info.imported, pendingCount = info.pending, legacy = info.legacy,
                        message = if (info.legacy) "Legacy backup: settings only; existing subscriptions will be preserved" else "Ready to replace subscriptions and settings; confirmation will stop VPN"))
                }
            } catch (e: CancellationException) {
                update(id, "Cancelled", message = "Cancelled")
            } catch (e: Throwable) {
                // Avoid URLs/passwords in persisted diagnostic strings.
                val safe = when (e) {
                    is IllegalArgumentException, is IllegalStateException -> e.message?.take(250) ?: "Backup validation failed"
                    else -> "Backup operation failed (${e.javaClass.simpleName}); check storage and WebDAV connection"
                }
                update(id, "Failed", message = safe)
            } finally {
                password?.fill(0); secrets.remove(id)?.fill(0); clients.remove(id)?.cancel(); jobs.remove(id)
                withContext(NonCancellable) { admission.withLock {
                    if (read(id)?.phase != "Ready") { cleanData(work); if (active == id) active = null }
                } }
                completed()
            }
        }
        jobs[id] = task
        task.start()
    }

    private fun startWorker(id: String) {
        val intent = Intent(context, BackupWorker::class.java).putExtra(BackupWorker.EXTRA_JOB, id)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }
    @Synchronized fun read(id: String): BackupJob? {
        val file = File(BackupArchive.jobDirectory(context, id), "job.json")
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        return android.util.AtomicFile(file).openRead().bufferedReader().use { json.decodeFromString<BackupJob>(it.readText()) }
    }
    @Synchronized private fun write(job: BackupJob) = BackupArchive.atomicWrite(File(BackupArchive.jobDirectory(context, job.id), "job.json"), json.encodeToString(job))
    private fun update(id: String, phase: String, progress: Int = 0, message: String = "") { read(id)?.let { write(it.copy(phase = phase, progress = progress, message = message)) } }
    fun latest(): BackupJob? = File(BackupArchive.root(context), "jobs").listFiles()?.mapNotNull { runCatching { read(it.name) }.getOrNull() }?.maxByOrNull { it.createdAt }
    private fun cleanData(work: File) { work.listFiles()?.filter { it.name != "job.json" && it.name != "job.json.bak" }?.forEach(File::deleteRecursively) }
    companion object {
        @Volatile private var instance: BackupEngine? = null
        fun get(context: Context): BackupEngine = instance ?: synchronized(this) { instance ?: BackupEngine(context.applicationContext).also { instance = it } }
    }
}
