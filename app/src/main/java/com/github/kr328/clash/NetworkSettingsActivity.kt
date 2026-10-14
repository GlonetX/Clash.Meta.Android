package com.github.kr328.clash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.NetworkSettingsDesign
import com.github.kr328.clash.design.settings.SettingsStore
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class NetworkSettingsActivity : BaseActivity<NetworkSettingsDesign>() {
    override suspend fun main() {
        val settingsStore = SettingsStore(this)

        val design = NetworkSettingsDesign(
            this,
            uiStore,
            ServiceStore(this),
            settingsStore,
            clashRunning,
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStart, Event.ClashStop, Event.ServiceRecreated ->
                            recreate()

                        Event.ActivityStart ->
                            design.refresh()

                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        NetworkSettingsDesign.Request.StartAccessControlList ->
                            startActivity(AccessControlActivity::class.intent)

                        NetworkSettingsDesign.Request.RequestLocationPermission ->
                            requestLocationPermission(settingsStore)

                        NetworkSettingsDesign.Request.OpenApplicationSettings ->
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
     * Reading the current SSID needs a location permission from Android 8.1.
     * Once the user has denied it permanently the system stops showing the
     * prompt, so the page offers the Settings deep link instead; this is only
     * reached while a request could still succeed.
     */
    private suspend fun requestLocationPermission(settingsStore: SettingsStore) {
        settingsStore.wifiPermissionRequested = true

        if (Build.VERSION.SDK_INT < 23)
            return

        val granted = startActivityForResult(
            ActivityResultContracts.RequestPermission(),
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        if (!granted)
            startActivityForResult(
                ActivityResultContracts.RequestPermission(),
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
    }
}
