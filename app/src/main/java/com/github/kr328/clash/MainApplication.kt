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
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@Suppress("unused")
class MainApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        Global.init(this)
    }

    override fun onCreate() {
        super.onCreate()

        val processName = currentProcessName
        extractGeoFiles()

        Log.d("Process $processName started")

        if (processName == packageName) {
            applyDynamicColors()

            registerLaunchCallbacks()

            Remote.launch()
        } else {
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
                (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES
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
        val geoipFile = File(clashDir, "geoip.metadb")
        if (geoipFile.exists() && geoipFile.lastModified() < updateDate) {
            geoipFile.delete()
        }
        if (!geoipFile.exists()) {
            FileOutputStream(geoipFile).use {
                assets.open("geoip.metadb").copyTo(it)
            }
        }

        val geositeFile = File(clashDir, "geosite.dat")
        if (geositeFile.exists() && geositeFile.lastModified() < updateDate) {
            geositeFile.delete()
        }
        if (!geositeFile.exists()) {
            FileOutputStream(geositeFile).use {
                assets.open("geosite.dat").copyTo(it)
            }
        }

        val asnFile = File(clashDir, "ASN.mmdb")
        if (asnFile.exists() && asnFile.lastModified() < updateDate) {
            asnFile.delete()
        }
        if (!asnFile.exists()) {
            FileOutputStream(asnFile).use {
                assets.open("ASN.mmdb").copyTo(it)
            }
        }

        val bundleMRSFile = File(clashDir, "BundleMRS.7z")
        if (bundleMRSFile.exists() && bundleMRSFile.lastModified() < updateDate) {
            bundleMRSFile.delete()
        }
        if (!bundleMRSFile.exists()) {
            FileOutputStream(bundleMRSFile).use {
                assets.open("BundleMRS.7z").copyTo(it)
            }
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
