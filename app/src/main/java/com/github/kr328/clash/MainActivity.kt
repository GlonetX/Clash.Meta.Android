package com.github.kr328.clash

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.util.toDateStr
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.AppUpdate
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R as DesignR

class MainActivity : BaseActivity<MainDesign>() {
    companion object {
        private const val IP_REFRESH_INTERVAL = 5 * 60 * 1000L
    }

    private var settingsDesign: SettingsDesign? = null
    private var proxyDesign: ProxyDesign? = null
    private var proxyGroupNames: List<String> = emptyList()
    private var proxyStates: List<com.github.kr328.clash.design.model.ProxyState> = emptyList()
    private val proxyReloadLock = Semaphore(10)

    override fun onDestroy() {
        settingsDesign?.cancel()
        proxyDesign?.cancel()

        super.onDestroy()
    }

    override fun onBackPressed() {
        if (design?.navigateBackToDashboard() == true)
            return

        super.onBackPressed()
    }

    override suspend fun main() {
        val design = MainDesign(this)
        val settingsDesign = SettingsDesign(this)

        this.settingsDesign = settingsDesign

        design.setPage(2, settingsDesign.root, getString(DesignR.string.settings))

        setContentDesign(design)

        design.fetch()
        design.fetchProxyPage()

        if (AppUpdate.isAutoCheckDue(this)) {
            AppUpdate.markChecked(this)

            launch {
                val release = AppUpdate.checkUpdate(this@MainActivity)

                if (release != null)
                    design.showUpdateDialog(release)
            }
        }

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        var lastIPRefresh = 0L

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop, Event.ClashStart,
                        Event.ProfileLoaded, Event.ProfileChanged -> {
                            design.fetch()
                            design.fetchProxyPage()

                            if (clashRunning && System.currentTimeMillis() - lastIPRefresh > IP_REFRESH_INTERVAL) {
                                lastIPRefresh = System.currentTimeMillis()

                                design.fetchIPInfo(force = false)
                            }
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        MainDesign.Request.ToggleStatus -> {
                            if (clashRunning)
                                stopClashService()
                            else
                                design.startClash()
                        }
                        MainDesign.Request.OpenProxy ->
                            design.showProxyPage()
                        MainDesign.Request.OpenProfiles ->
                            startActivity(ProfilesActivity::class.intent)
                        MainDesign.Request.OpenProviders ->
                            startActivity(ProvidersActivity::class.intent)
                        MainDesign.Request.OpenLogs -> {
                            if (LogcatService.running) {
                                startActivity(LogcatActivity::class.intent)
                            } else {
                                startActivity(LogsActivity::class.intent)
                            }
                        }
                        MainDesign.Request.OpenSettings ->
                            design.showSettingsPage()
                        MainDesign.Request.OpenHelp ->
                            startActivity(HelpActivity::class.intent)
                        MainDesign.Request.OpenAbout ->
                            design.showAbout(queryAppVersionName())
                        MainDesign.Request.RefreshIP -> {
                            lastIPRefresh = System.currentTimeMillis()

                            design.fetchIPInfo(force = true)
                        }
                        MainDesign.Request.OpenIPQuality ->
                            startActivity(IPQualityActivity::class.intent)
                        MainDesign.Request.OpenLatency ->
                            startActivity(LatencyActivity::class.intent)
                        MainDesign.Request.PatchMode -> {
                            val mode = design.checkedOutboundMode()

                            withClash {
                                val o = queryOverride(Clash.OverrideSlot.Session)

                                o.mode = mode

                                patchOverride(Clash.OverrideSlot.Session, o)
                            }

                            design.setOutboundMode(mode)
                        }
                    }
                }
                proxyDesign?.let { current ->
                    current.requests.onReceive {
                        when (it) {
                            ProxyDesign.Request.ReLaunch ->
                                design.fetchProxyPage(force = true)
                            ProxyDesign.Request.ReloadAll -> {
                                proxyGroupNames.indices.forEach { idx ->
                                    current.requests.trySend(ProxyDesign.Request.Reload(idx))
                                }
                            }
                            is ProxyDesign.Request.Reload -> {
                                launch {
                                    val group = proxyReloadLock.withPermit {
                                        withClash {
                                            queryProxyGroup(proxyGroupNames[it.index], uiStore.proxySort)
                                        }
                                    }

                                    val state = proxyStates[it.index]

                                    state.now = group.now

                                    proxyDesign?.updateGroup(
                                        it.index,
                                        group.proxies,
                                        group.type == "Selector",
                                        state,
                                        proxyStates.mapIndexed { idx, s -> proxyGroupNames[idx] to s }.toMap()
                                    )
                                }
                            }
                            is ProxyDesign.Request.Select -> {
                                withClash {
                                    patchSelector(proxyGroupNames[it.index], it.name)
                                }

                                proxyStates[it.index].now = it.name

                                proxyDesign?.requestRedrawVisible()
                            }
                            is ProxyDesign.Request.UrlTest -> {
                                launch {
                                    withClash {
                                        healthCheck(proxyGroupNames[it.index])
                                    }

                                    proxyDesign?.requests?.trySend(ProxyDesign.Request.Reload(it.index))
                                }
                            }
                            is ProxyDesign.Request.PatchMode -> {
                                proxyDesign?.showModeSwitchTips()

                                withClash {
                                    val o = queryOverride(Clash.OverrideSlot.Session)

                                    o.mode = it.mode

                                    patchOverride(Clash.OverrideSlot.Session, o)
                                }

                                design.setOutboundMode(it.mode ?: TunnelState.Mode.Rule)
                            }
                        }
                    }
                }
                settingsDesign.requests.onReceive {
                    when (it) {
                        SettingsDesign.Request.StartApp ->
                            startActivity(AppSettingsActivity::class.intent)
                        SettingsDesign.Request.StartNetwork ->
                            startActivity(NetworkSettingsActivity::class.intent)
                        SettingsDesign.Request.StartOverride ->
                            startActivity(OverrideSettingsActivity::class.intent)
                        SettingsDesign.Request.StartMetaFeature ->
                            startActivity(MetaFeatureSettingsActivity::class.intent)
                    }
                }
                if (clashRunning) {
                    ticker.onReceive {
                        design.fetchTraffic()
                    }
                }
            }
        }
    }

    private suspend fun MainDesign.fetch() {
        setClashRunning(clashRunning)

        val state = withClash {
            queryTunnelState()
        }
        val providers = withClash {
            queryProviders()
        }

        setMode(state.mode)
        setOutboundMode(state.mode)
        setHasProviders(providers.isNotEmpty())

        val profile = withProfile {
            queryActive()
        }

        setProfileName(profile?.name)
        setProfileSummary(profile?.summary())
    }

    private suspend fun MainDesign.fetchProxyPage(force: Boolean = false) {
        val names = withClash {
            queryProxyGroupNames(uiStore.proxyExcludeNotSelectable)
        }

        if (!force && names == proxyGroupNames && proxyDesign != null)
            return

        proxyGroupNames = names
        proxyStates = List(names.size) { com.github.kr328.clash.design.model.ProxyState("?") }

        val mode = withClash { queryOverride(Clash.OverrideSlot.Session).mode }

        val old = proxyDesign
        proxyDesign = null

        val newDesign = ProxyDesign(
            this@MainActivity,
            mode,
            names,
            uiStore
        )

        proxyDesign = newDesign

        setPage(1, newDesign.root, getString(DesignR.string.proxy))

        old?.cancel()

        newDesign.requests.trySend(ProxyDesign.Request.ReloadAll)
    }

    private suspend fun MainDesign.fetchTraffic() {
        withClash {
            setForwarded(queryTrafficTotal())
        }

        val traffic = withClash {
            queryTrafficNow()
        }

        setNetworkSpeed(traffic)

        val memory = withClash {
            queryMemory()
        }

        setMemoryUsage(memory)
    }

    private suspend fun MainDesign.fetchIPInfo(force: Boolean) {
        setIPInfoLoading()

        try {
            coroutineScope {
                val exit = async { withClash { queryIPInfo("") } }
                val direct = async { withClash { queryIPInfo("DIRECT") } }

                setIPInfo(exit.await(), direct.await())
            }
        } catch (e: Exception) {
            if (force)
                setIPInfoFailed()
            else
                setIPInfo(null, null)
        }
    }

    private suspend fun MainDesign.startClash() {
        val active = withProfile { queryActive() }

        if (active == null || !active.imported) {
            showToast(DesignR.string.no_profile_selected, ToastDuration.Long) {
                setAction(DesignR.string.profiles) {
                    startActivity(ProfilesActivity::class.intent)
                }
            }

            return
        }

        val vpnRequest = startClashService()

        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )

                if (result.resultCode == RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            showToast(DesignR.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }

    private fun Profile?.summary(): String? {
        if (this == null)
            return null

        val usage = if (total > 0) {
            "${(download + upload).toBytesString()} / ${total.toBytesString()}"
        } else {
            null
        }

        val expiry = if (expire > 0) {
            expire.toDateStr()
        } else {
            null
        }

        return listOfNotNull(usage, expiry).joinToString(separator = " · ").takeIf { it.isNotBlank() }
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        if (uuid == null)
            return

        launch {
            var name: String? = null
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(DesignR.string.toast_profile_updated_complete, name),
                ToastDuration.Long
            )
        }
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if (uuid == null)
            return

        launch {
            var name: String? = null
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(DesignR.string.toast_profile_updated_failed, name, reason),
                ToastDuration.Long
            ) {
                setAction(DesignR.string.edit) {
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
            }
        }
    }

    private suspend fun MainDesign.showUpdateDialog(release: AppUpdate.Release) {
        val result = withContext(Dispatchers.Main) {
            var confirmed = false

            com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(getString(DesignR.string.update_available_title, release.versionName))
                .setMessage(release.notes.take(800).ifBlank { getString(DesignR.string.update_available_summary) })
                .setPositiveButton(android.R.string.ok) { _, _ -> confirmed = true }
                .setNegativeButton(android.R.string.cancel, null)
                .show()

            confirmed
        }

        if (!result)
            return

        showToast(DesignR.string.update_downloading, ToastDuration.Short)

        val file = AppUpdate.downloadApk(this@MainActivity, release) { }

        if (file == null) {
            showToast(DesignR.string.update_download_failed, ToastDuration.Long)

            return
        }

        withContext(Dispatchers.Main) {
            if (!AppUpdate.installApk(this@MainActivity, file))
                showToast(DesignR.string.update_install_failed, ToastDuration.Long)
        }
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" + Bridge.nativeCoreVersion().replace("_", "-")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher =
                registerForActivityResult(RequestPermission()
                ) { isGranted: Boolean ->
                }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setupShortcuts()
    }

    private fun setupShortcuts() {
        // Skip dynamic shortcut setup when the app icon is hidden.
        if (uiStore.hideAppIcon) return

        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            Intent.FLAG_ACTIVITY_NO_ANIMATION

        val toggle = ShortcutInfoCompat.Builder(this, "toggle_clash")
            .setShortLabel(getString(DesignR.string.shortcut_toggle_short))
            .setLongLabel(getString(DesignR.string.shortcut_toggle_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_all))
            .setIntent(
                Intent(Intents.ACTION_TOGGLE_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(0)
            .build()

        val start = ShortcutInfoCompat.Builder(this, "start_clash")
            .setShortLabel(getString(DesignR.string.shortcut_start_short))
            .setLongLabel(getString(DesignR.string.shortcut_start_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_on))
            .setIntent(
                Intent(Intents.ACTION_START_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(1)
            .build()

        val stop = ShortcutInfoCompat.Builder(this, "stop_clash")
            .setShortLabel(getString(DesignR.string.shortcut_stop_short))
            .setLongLabel(getString(DesignR.string.shortcut_stop_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_off))
            .setIntent(
                Intent(Intents.ACTION_STOP_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(2)
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(toggle, start, stop))
    }
}
