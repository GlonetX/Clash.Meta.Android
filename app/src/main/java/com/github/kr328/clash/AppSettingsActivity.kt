package com.github.kr328.clash

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.design.AppSettingsDesign
import com.github.kr328.clash.design.model.Behavior
import com.github.kr328.clash.design.settings.SettingsStore
import com.github.kr328.clash.design.settings.SubscriptionInfo
import com.github.kr328.clash.design.store.UiStore.Companion.mainActivityAlias
import com.github.kr328.clash.design.store.ProfileUpdateStore
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.ApplicationObserver
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class AppSettingsActivity : BaseActivity<AppSettingsDesign>(), Behavior {

    override suspend fun main() {
        val design = AppSettingsDesign(
            this,
            uiStore,
            ServiceStore(this),
            SettingsStore(this),
            ProfileUpdateStore(this),
            this,
            clashRunning,
            querySubscriptions(),
            ::onHideIconChange,
            intent.getStringExtra(Intents.EXTRA_SCROLL_TO),
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStart, Event.ClashStop, Event.ServiceRecreated ->
                            recreate()

                        // the battery-optimisation exemption is granted outside
                        // this activity, so the page has to re-read it on resume
                        Event.ActivityStart ->
                            design.refresh()

                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        AppSettingsDesign.Request.ReCreateAllActivities ->
                            ApplicationObserver.createdActivities.forEach {
                                it.recreate()
                            }

                        AppSettingsDesign.Request.OpenBatteryOptimizationSettings ->
                            openBatteryOptimizationSettings()

                        AppSettingsDesign.Request.OpenApplicationSettings ->
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", packageName, null))
                            )
                    }
                }
            }
        }
    }

    /**
     * The per-subscription tag/notes editor only needs each profile's identity
     * and display name. The profile service may not be up on a cold start, so a
     * failure degrades to "no subscriptions" rather than taking the page down.
     */
    private suspend fun querySubscriptions(): List<SubscriptionInfo> = try {
        withProfile { queryAll() }
            .map { SubscriptionInfo(it.uuid.toString(), it.name) }
            .sortedBy { it.name.lowercase() }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` needs a manifest permission
     * that the Play store restricts. Without it the intent resolves to nothing
     * useful on most builds, so fall back to the settings list and let the user
     * pick the app by hand.
     */
    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < 23)
            return

        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", packageName, null),
        )

        if (request.resolveActivity(packageManager) != null) {
            startActivity(request)
        } else {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override var autoRestart: Boolean
        get() {
            val status = packageManager.getComponentEnabledSetting(
                RestartReceiver::class.componentName
            )

            return status == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        set(value) {
            val status = if (value)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            packageManager.setComponentEnabledSetting(
                RestartReceiver::class.componentName,
                status,
                PackageManager.DONT_KILL_APP,
            )
        }

    private fun onHideIconChange(hide: Boolean) {
        val newState = if (hide) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        packageManager.setComponentEnabledSetting(
            mainActivityAlias,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }
}
