package com.github.kr328.clash.service.remote

import com.github.kr328.clash.service.backup.BackupConfig
import com.github.kr328.clash.service.backup.BackupJob
import com.github.kr328.clash.service.backup.BackupRemoteFile
import com.github.kr328.kaidl.BinderInterface

@BinderInterface
interface IBackupManager {
    suspend fun getConfig(): BackupConfig
    suspend fun saveConfig(config: BackupConfig, password: String?)
    suspend fun listRemote(): List<BackupRemoteFile>
    suspend fun startBackup(passphrase: ByteArray): String
    suspend fun prepareRestore(remoteName: String, passphrase: ByteArray): String
    suspend fun commitRestore(jobId: String)
    suspend fun getJob(jobId: String): BackupJob?
    suspend fun getLatestJob(): BackupJob?
    suspend fun cancel(jobId: String)
}
