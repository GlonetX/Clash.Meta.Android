package com.github.kr328.clash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.ProfileWorker
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

@Suppress("unused")
class MainApplication : Application() {

    /** Application-lifetime scope for fire-and-forget disk work. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        Global.init(this)
    }

    override fun onCreate() {
        super.onCreate()

        val processName = currentProcessName

        Log.d("Process $processName started")

        if (processName == packageName) {
            // Warm the geo assets off the main thread. The :background process
            // re-checks them synchronously before the core loads, so a slow
            // warm-up can only cost a duplicate check, never a missing file.
            // Configuration files and core assets are owned by the background process.

            Remote.launch()
            ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) updateDueProfilesOnLaunch()
            })
        } else {
            // Classify legacy data before any service or Room database is initialized.
            com.github.kr328.clash.service.settings.SettingsManager.get(this)
            // Only the :background process hosts the core, so the geo assets
            // are extracted here instead of blocking the UI process.
            extractGeoFiles()

            sendServiceRecreated()
        }
    }

    /**
     * Refreshes imported subscription profiles whose own update interval
     * has elapsed, honouring the user's "update profiles on launch"
     * preference (default on). Uses the same mechanism as the scheduled
     * updates: ACTION_PROFILE_REQUEST_UPDATE routed to ProfileWorker,
     * which also reschedules the next alarm after a successful update.
     */
    private fun updateDueProfilesOnLaunch() {
        ioScope.launch {
            try {
                com.github.kr328.clash.util.withProfile { updateOnLaunch() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Update profiles on launch failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun extractGeoFiles() {
        clashDir.mkdirs()

        val updateDate = packageManager.getPackageInfo(packageName, 0).lastUpdateTime

        extractAssetIfNeeded("geoip.metadb", File(clashDir, "geoip.metadb"), updateDate)
        extractAssetIfNeeded("geosite.dat", File(clashDir, "geosite.dat"), updateDate)
        extractAssetIfNeeded("ASN.mmdb", File(clashDir, "ASN.mmdb"), updateDate)
        extractAssetIfNeeded("BundleMRS.7z", File(clashDir, "BundleMRS.7z"), updateDate)
    }

    /**
     * Copies [assetName] to [target] when the file is missing, truncated or
     * older than the app install. The copy goes to a sibling `.tmp` file
     * first and is renamed into place, so a crash mid-copy can never leave a
     * corrupt asset behind (which would then be considered valid forever).
     */
    private fun extractAssetIfNeeded(assetName: String, target: File, updateDate: Long) {
        var tmp: File? = null

        try {
            val assetLength = assets.open(assetName).use { it.available().toLong() }

            val needsExtract = !target.exists() ||
                    target.length() != assetLength ||
                    target.lastModified() < updateDate

            if (!needsExtract)
                return

            // A unique temp name lets the UI and :background processes warm the
            // same asset concurrently without truncating each other's output.
            tmp = File.createTempFile("${target.name}.", ".tmp", target.parentFile)

            assets.open(assetName).use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output)

                    output.fd.sync()
                }
            }

            if (!tmp.renameTo(target)) {
                // Some filesystems refuse to rename over an existing file.
                target.delete()

                if (!tmp.renameTo(target))
                    throw IOException("Failed to replace $target")
            }

            tmp = null
        } catch (e: Exception) {
            tmp?.delete()

            Log.w("Extract $assetName failed: ${e.message}")
        }
    }

    fun finalize() {
        Global.destroy()
    }

    companion object {
        /** Matches the minimum scheduling interval in ProfileReceiver.scheduleNext. */
        private val MIN_UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(15)
    }
}
