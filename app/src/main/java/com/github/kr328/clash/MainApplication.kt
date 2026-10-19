package com.github.kr328.clash

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.DynamicColorsStore
import com.github.kr328.clash.design.store.ProfileUpdateStore
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.ProfileWorker
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import com.google.android.material.color.DynamicColors
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
            ioScope.launch { extractGeoFiles() }

            applyDynamicColors()

            registerLaunchCallbacks()

            Remote.launch()
        } else {
            // Only the :background process hosts the core, so the geo assets
            // are extracted here instead of blocking the UI process.
            extractGeoFiles()

            sendServiceRecreated()
        }
    }

    /**
     * Material You (wallpaper) dynamic colour, gated on a user preference
     * that defaults to on. When the user opts out — or on API < 31 — the
     * hardcoded brand palette in values/themes.xml is used instead.
     *
     * Ordering trap: [DynamicColors.applyToActivitiesIfAvailable] applies
     * Material's overlay in [Application.ActivityLifecycleCallbacks.onActivityPreCreated],
     * i.e. BEFORE Activity.onCreate. BaseActivity.onCreate then calls
     * applyDayNight(), which force-applies the AppTheme{Light,Dark,Black}
     * family and would overwrite the dynamic palette with the brand
     * palette. registerLaunchCallbacks() therefore re-applies a dynamic
     * colour overlay from onActivityCreated — after applyDayNight() but
     * still before the first view inflation, because designs are inflated
     * from a coroutine posted to the main queue during onCreate.
     */
    private fun applyDynamicColors() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return

        if (DynamicColorsStore(this).enabled) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
    }

    /**
     * Always-registered launch callbacks:
     *
     * - onActivityCreated: re-applies the dynamic colour overlay after
     *   BaseActivity.applyDayNight() (see applyDynamicColors) and
     *   re-asserts the AMOLED #FF000000 surfaces for Pure Black mode,
     *   which the dynamic overlay otherwise replaces with tinted ones.
     * - onActivityResumed: once per process, kicks off the launch-time
     *   profile refresh (update-on-launch preference).
     */
    private fun registerLaunchCallbacks() {
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var launchUpdateChecked = false

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                applyDynamicColorsToActivity(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                // Once per process, when the app actually comes to the
                // foreground (starting a foreground service from the
                // background is restricted).
                if (launchUpdateChecked)
                    return

                launchUpdateChecked = true

                updateDueProfilesOnLaunch()
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun applyDynamicColorsToActivity(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return

        if (!DynamicColorsStore(activity).enabled)
            return

        val darkMode = UiStore(activity).darkMode

        val night = when (darkMode) {
            DarkMode.ForceLight -> false
            DarkMode.ForceDark, DarkMode.ForceBlack -> true
            DarkMode.Auto ->
                (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
        }

        val dynamicOverlay = if (night) {
            com.github.kr328.clash.design.R.style.ThemeOverlay_App_DynamicColors_Dark
        } else {
            com.github.kr328.clash.design.R.style.ThemeOverlay_App_DynamicColors_Light
        }

        activity.theme.applyStyle(dynamicOverlay, true)

        if (darkMode == DarkMode.ForceBlack) {
            activity.theme.applyStyle(
                com.github.kr328.clash.design.R.style.ThemeOverlay_App_PureBlack,
                true
            )
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
        Global.launch(Dispatchers.Default) {
            if (!ProfileUpdateStore(this@MainApplication).updateOnLaunch)
                return@launch

            try {
                val due = ImportedDao().queryAllUUIDs()
                    .mapNotNull { ImportedDao().queryByUUID(it) }
                    .filter { it.type != Profile.Type.File && it.interval >= MIN_UPDATE_INTERVAL }
                    .filter { imported ->
                        val last = importedDir
                            .resolve(imported.uuid.toString())
                            .resolve("config.yaml")
                            .lastModified()

                        // Skip never-fetched profiles, like
                        // ProfileReceiver.scheduleNext does.
                        last > 0 && System.currentTimeMillis() - last >= imported.interval
                    }

                due.forEach { imported ->
                    val intent = Intent(Intents.ACTION_PROFILE_REQUEST_UPDATE)
                        .setComponent(ProfileWorker::class.componentName)
                        .setUUID(imported.uuid)

                    startForegroundServiceCompat(intent)
                }

                if (due.isNotEmpty()) {
                    Log.d("Update on launch: ${due.size} profile(s) due")
                }
            } catch (e: Exception) {
                Log.w("Update profiles on launch failed: ${e.message}")
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
