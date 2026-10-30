package com.github.kr328.clash.service.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Started, non-exported foreground owner; independent of Activity bindings. */
class BackupWorker : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Backups", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("GlonetX backup").setContentText("Processing backup or restore")
            .setSmallIcon(android.R.drawable.stat_sys_upload).setOnlyAlertOnce(true).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION, notification)
        val id = intent?.getStringExtra(EXTRA_JOB)
        if (id == null) stopSelf(startId)
        else BackupEngine.get(this).execute(id, scope) { stopSelf(startId) }
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        const val EXTRA_JOB = "backup_job"
        private const val CHANNEL = "backup_operations"
        private const val NOTIFICATION = 0x475842
    }
}
