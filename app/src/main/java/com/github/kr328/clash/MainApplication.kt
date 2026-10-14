package com.github.kr328.clash

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.DynamicColorsStore
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import com.google.android.material.color.DynamicColors
import java.io.File
import java.io.FileOutputStream

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
     * palette. The callback registered below therefore re-applies a
     * dynamic colour overlay from onActivityCreated — after applyDayNight()
     * but still before the first view inflation, because designs are
     * inflated from a coroutine posted to the main queue during onCreate.
     *
     * The AMOLED "Pure Black" mode re-asserts its #FF000000 surfaces after
     * the dynamic overlay, which otherwise replaces them with tinted ones.
     */
    private fun applyDynamicColors() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return

        if (DynamicColorsStore(this).enabled) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }

        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
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

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
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
}
