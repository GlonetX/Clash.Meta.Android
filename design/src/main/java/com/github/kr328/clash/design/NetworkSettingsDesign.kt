package com.github.kr328.clash.design

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.settings.SettingGroup
import com.github.kr328.clash.design.settings.SettingItem
import com.github.kr328.clash.design.settings.SettingPage
import com.github.kr328.clash.design.settings.SettingRow
import com.github.kr328.clash.design.settings.SettingsStore
import com.github.kr328.clash.design.settings.TextAdapter
import com.github.kr328.clash.design.settings.WifiNetworkAction
import com.github.kr328.clash.design.settings.editSettingText
import com.github.kr328.clash.design.settings.editSettingTextList
import com.github.kr328.clash.design.settings.selectSetting
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The "Network" settings page: VpnService options, the mihomo core knobs that
 * had no UI at all, log retention, and the named-Wi-Fi behaviour.
 */
class NetworkSettingsDesign(
    context: Context,
    private val uiStore: UiStore,
    private val srvStore: ServiceStore,
    private val settings: SettingsStore,
    private val running: Boolean,
    private val scrollToKey: String? = null,
) : Design<NetworkSettingsDesign.Request>(context) {
    enum class Request {
        StartAccessControlList,
        RequestLocationPermission,
        OpenApplicationSettings,
    }

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val page = SettingPage(binding.recyclerList, binding.activityBarLayout)

    init {
        binding.surface = surface

        binding.activityBarLayout.applyFrom(context)

        refresh()
    }

    fun refresh() {
        page.render(buildItems())

        if (pendingScrollKey != null) {
            page.scrollTo(pendingScrollKey!!)
            pendingScrollKey = null
        }
    }

    private var pendingScrollKey: String? = scrollToKey

    /**
     * While the core is running the VpnService options are locked: changing
     * them under a live tunnel has no effect until the next start, so the rows
     * now say so in their own subtitle instead of swallowing the tap (which is
     * what the old implementation did, alongside an indefinite snackbar).
     */
    private fun buildItems(): List<SettingItem> = listOf(
        vpnGroup(),
        coreGroup(),
        loggingGroup(),
        wifiGroup(),
    )

    // ------------------------------------------------------------------ vpn

    private fun vpnGroup(): SettingGroup {
        val locked = running

        // while the core runs, the locked rows explain themselves here instead
        // of dropping to a single screen-wide toast
        fun summaryOf(@androidx.annotation.StringRes normal: Int): CharSequence =
            if (locked) context.getText(R.string.settings_disabled_while_running)
            else context.getText(normal)

        val dependent = uiStore.enableVpn && !locked

        val rows = mutableListOf<SettingRow>(
            SettingRow.Switch(
                id = "enable_vpn",
                title = R.string.route_system_traffic,
                summary = context.getText(R.string.routing_via_vpn_service),
                icon = R.drawable.ic_baseline_vpn_lock,
                enabled = !locked,
                checked = uiStore.enableVpn,
                onToggle = { checked ->
                    uiStore.enableVpn = checked

                    refresh()
                },
            ),
            SettingRow.Switch(
                id = "bypass_private_network",
                title = R.string.bypass_private_network,
                summary = summaryOf(R.string.bypass_private_network_summary),
                enabled = dependent,
                checked = srvStore.bypassPrivateNetwork,
                onToggle = { checked ->
                    srvStore.bypassPrivateNetwork = checked

                    refresh()
                },
            ),
            SettingRow.Switch(
                id = "dns_hijacking",
                title = R.string.dns_hijacking,
                summary = summaryOf(R.string.dns_hijacking_summary),
                enabled = dependent,
                checked = srvStore.dnsHijacking,
                onToggle = { checked ->
                    srvStore.dnsHijacking = checked

                    refresh()
                },
            ),
            SettingRow.Switch(
                id = "allow_bypass",
                title = R.string.allow_bypass,
                summary = summaryOf(R.string.allow_bypass_summary),
                enabled = dependent,
                checked = srvStore.allowBypass,
                onToggle = { checked ->
                    srvStore.allowBypass = checked

                    refresh()
                },
            ),
            SettingRow.Switch(
                id = "allow_ipv6",
                title = R.string.allow_ipv6,
                summary = summaryOf(R.string.allow_ipv6_summary),
                enabled = dependent,
                checked = srvStore.allowIpv6,
                onToggle = { checked ->
                    srvStore.allowIpv6 = checked

                    refresh()
                },
            ),
        )

        if (Build.VERSION.SDK_INT >= 29) {
            rows.add(
                SettingRow.Switch(
                    id = "system_proxy",
                    title = R.string.system_proxy,
                    summary = summaryOf(R.string.system_proxy_summary),
                    enabled = dependent,
                    checked = srvStore.systemProxy,
                    onToggle = { checked ->
                        srvStore.systemProxy = checked

                        refresh()
                    },
                )
            )
        }

        rows.add(
            SettingRow.Action(
                id = "tun_stack_mode",
                title = R.string.tun_stack_mode,
                summary = if (locked)
                    context.getText(R.string.settings_disabled_while_running)
                else
                    context.getText(tunStackText(srvStore.tunStackMode)),
                enabled = dependent,
                onClick = { anchor -> selectTunStackMode(anchor) },
            )
        )

        rows.add(
            SettingRow.Action(
                id = "tun_mtu",
                title = R.string.tun_mtu,
                summary = if (locked)
                    context.getText(R.string.settings_disabled_while_running)
                else
                    context.getString(R.string.tun_mtu_value, srvStore.tunMtu),
                enabled = dependent,
                onClick = { anchor -> selectTunMtu(anchor) },
            )
        )

        rows.add(
            SettingRow.Action(
                id = "access_control_mode",
                title = R.string.access_control_mode,
                summary = if (locked)
                    context.getText(R.string.settings_disabled_while_running)
                else
                    context.getText(accessControlModeText(srvStore.accessControlMode)),
                enabled = dependent,
                onClick = { anchor -> selectAccessControlMode(anchor) },
            )
        )

        rows.add(
            SettingRow.Action(
                id = "access_control_packages",
                title = R.string.access_control_packages,
                summary = if (locked)
                    context.getText(R.string.settings_disabled_while_running)
                else
                    context.getText(R.string.access_control_packages_summary),
                enabled = dependent,
                onClick = {
                    requests.trySend(Request.StartAccessControlList)
                },
            )
        )

        return SettingGroup(R.string.vpn_service_options, rows)
    }

    // ----------------------------------------------------------------- core

    private fun coreGroup(): SettingGroup = SettingGroup(
        R.string.settings_group_core,
        listOf(
            SettingRow.Switch(
                id = "stop_on_network_lost",
                title = R.string.settings_stop_on_network_lost,
                summary = context.getText(R.string.settings_stop_on_network_lost_summary),
                icon = R.drawable.ic_settings_wifi,
                checked = settings.stopOnNetworkLost,
                onToggle = { checked ->
                    settings.stopOnNetworkLost = checked

                    refresh()
                },
            ),
            SettingRow.Action(
                id = "stop_on_network_lost_interval",
                title = R.string.settings_stop_on_network_lost_interval,
                enabled = settings.stopOnNetworkLost,
                summary = context.getString(
                    R.string.settings_format_seconds,
                    settings.stopOnNetworkLostInterval,
                ),
                onClick = { editSeconds(SecondsKey.StopInterval) },
            ),
            SettingRow.Action(
                id = "stop_on_network_lost_bypass",
                title = R.string.settings_stop_on_network_lost_bypass,
                summary = context.getText(R.string.settings_stop_on_network_lost_bypass_summary),
                enabled = settings.stopOnNetworkLost,
                onClick = { editBypassedInterfaces() },
            ),
            SettingRow.Switch(
                id = "keep_alive",
                title = R.string.settings_keep_alive,
                summary = context.getText(R.string.settings_keep_alive_summary),
                icon = R.drawable.ic_baseline_refresh,
                checked = settings.keepAliveEnabled,
                onToggle = { checked ->
                    settings.keepAliveEnabled = checked

                    refresh()
                },
            ),
            SettingRow.Action(
                id = "keep_alive_interval",
                title = R.string.settings_keep_alive_interval,
                enabled = settings.keepAliveEnabled,
                summary = context.getString(
                    R.string.settings_format_seconds,
                    settings.keepAliveInterval,
                ),
                onClick = { editSeconds(SecondsKey.KeepAliveInterval) },
            ),
            SettingRow.Action(
                id = "keep_alive_idle",
                title = R.string.settings_keep_alive_idle,
                enabled = settings.keepAliveEnabled,
                summary = context.getString(
                    R.string.settings_format_seconds,
                    settings.keepAliveIdle,
                ),
                onClick = { editSeconds(SecondsKey.KeepAliveIdle) },
            ),
            SettingRow.Switch(
                id = "store_fake_ip",
                title = R.string.settings_store_fake_ip,
                summary = context.getText(R.string.settings_store_fake_ip_summary),
                icon = R.drawable.ic_baseline_dns,
                checked = settings.storeFakeIp,
                onToggle = { checked ->
                    settings.storeFakeIp = checked

                    refresh()
                },
            ),
        ),
    )

    // -------------------------------------------------------------- logging

    private fun loggingGroup(): SettingGroup = SettingGroup(
        R.string.settings_group_logging,
        listOf(
            SettingRow.Action(
                id = "log_retention_days",
                title = R.string.settings_log_retention_days,
                icon = R.drawable.ic_settings_article,
                summary = context.getString(
                    R.string.settings_format_days,
                    settings.logRetentionDays,
                ),
                onClick = { editNumber(NumberKey.LogRetentionDays) },
            ),
            SettingRow.Action(
                id = "log_max_size_mb",
                title = R.string.settings_log_max_size,
                icon = R.drawable.ic_baseline_save,
                summary = context.getString(
                    R.string.settings_format_mb,
                    settings.logMaxSizeMb,
                ),
                onClick = { editNumber(NumberKey.LogMaxSizeMb) },
            ),
            SettingRow.Action(
                id = "log_realtime_buffer",
                title = R.string.settings_log_realtime_buffer,
                icon = R.drawable.ic_baseline_stack,
                summary = context.getString(
                    R.string.settings_format_entries,
                    settings.logRealtimeBuffer,
                ),
                onClick = { editNumber(NumberKey.LogRealtimeBuffer) },
            ),
        ),
    )

    // ----------------------------------------------------------------- wifi

    private fun wifiGroup(): SettingGroup = SettingGroup(
        R.string.settings_group_wifi,
        listOf(
            SettingRow.Action(
                id = "wifi_action",
                title = R.string.settings_wifi_action,
                icon = R.drawable.ic_settings_wifi,
                summary = context.getText(wifiActionText(settings.wifiNetworkAction)),
                onClick = { anchor -> selectWifiAction(anchor) },
            ),
            SettingRow.Action(
                id = "wifi_networks",
                title = R.string.settings_wifi_networks,
                enabled = settings.wifiNetworkAction != WifiNetworkAction.Disabled,
                summary = if (settings.wifiSsidList.isEmpty())
                    context.getString(R.string.settings_wifi_networks_empty)
                else
                    context.getString(
                        R.string.settings_format_entries,
                        settings.wifiSsidList.size,
                    ),
                onClick = { editWifiNetworks() },
            ),
            locationPermissionRow(),
        ),
    )

    /**
     * Reading the current SSID needs a location permission on Android 8.1+, and
     * the system stops offering it once the user has denied it twice, so the row
     * has to offer the Settings deep link rather than a request that can only
     * ever fail.
     */
    private fun locationPermissionRow(): SettingRow.Action {
        val granted = hasLocationPermission()

        val summary = when {
            granted -> context.getText(R.string.settings_permission_location_granted)
            !settings.wifiPermissionRequested ->
                context.getText(R.string.settings_permission_location_required)
            canRequestLocationPermission ->
                context.getText(R.string.settings_permission_location_denied)
            else -> context.getString(
                R.string.settings_permission_location_denied_permanently
            )
        }

        return SettingRow.Action(
            id = "location_permission",
            title = R.string.settings_permission_location,
            icon = R.drawable.ic_baseline_my_location,
            enabled = !granted,
            showChevron = !granted,
            summary = summary,
            onClick = {
                requests.trySend(
                    if (canRequestLocationPermission)
                        Request.RequestLocationPermission
                    else
                        Request.OpenApplicationSettings
                )
            },
        )
    }

    // -------------------------------------------------------------- actions

    private enum class SecondsKey(val titleRes: Int, val min: Int, val max: Int) {
        StopInterval(R.string.settings_stop_on_network_lost_interval, 5, 3600),
        KeepAliveInterval(R.string.settings_keep_alive_interval, 5, 3600),
        KeepAliveIdle(R.string.settings_keep_alive_idle, 30, 86400),
    }

    private enum class NumberKey(val titleRes: Int, val min: Int, val max: Int) {
        LogRetentionDays(R.string.settings_log_retention_days, 1, 365),
        LogMaxSizeMb(R.string.settings_log_max_size, 1, 1024),
        LogRealtimeBuffer(R.string.settings_log_realtime_buffer, 0, 10000),
    }

    private fun readSeconds(key: SecondsKey): Int = when (key) {
        SecondsKey.StopInterval -> settings.stopOnNetworkLostInterval
        SecondsKey.KeepAliveInterval -> settings.keepAliveInterval
        SecondsKey.KeepAliveIdle -> settings.keepAliveIdle
    }

    private fun writeSeconds(key: SecondsKey, value: Int) {
        when (key) {
            SecondsKey.StopInterval -> settings.stopOnNetworkLostInterval = value
            SecondsKey.KeepAliveInterval -> settings.keepAliveInterval = value
            SecondsKey.KeepAliveIdle -> settings.keepAliveIdle = value
        }
    }

    private fun readNumber(key: NumberKey): Int = when (key) {
        NumberKey.LogRetentionDays -> settings.logRetentionDays
        NumberKey.LogMaxSizeMb -> settings.logMaxSizeMb
        NumberKey.LogRealtimeBuffer -> settings.logRealtimeBuffer
    }

    private fun writeNumber(key: NumberKey, value: Int) {
        when (key) {
            NumberKey.LogRetentionDays -> settings.logRetentionDays = value
            NumberKey.LogMaxSizeMb -> settings.logMaxSizeMb = value
            NumberKey.LogRealtimeBuffer -> settings.logRealtimeBuffer = value
        }
    }

    private fun editSeconds(key: SecondsKey) {
        launch(Dispatchers.Main) {
            val text = context.editSettingText(
                initial = readSeconds(key).toString(),
                title = context.getText(key.titleRes),
                allowReset = false,
                error = context.getString(R.string.settings_invalid_number, key.min, key.max),
                validator = numberValidator(key.min, key.max),
            ) ?: return@launch

            val parsed = text.trim().toIntOrNull() ?: return@launch

            writeSeconds(key, parsed)

            refresh()
        }
    }

    private fun editNumber(key: NumberKey) {
        launch(Dispatchers.Main) {
            val text = context.editSettingText(
                initial = readNumber(key).toString(),
                title = context.getText(key.titleRes),
                allowReset = false,
                error = context.getString(R.string.settings_invalid_number, key.min, key.max),
                validator = numberValidator(key.min, key.max),
            ) ?: return@launch

            val parsed = text.trim().toIntOrNull() ?: return@launch

            writeNumber(key, parsed)

            refresh()
        }
    }

    private fun editBypassedInterfaces() {
        launch(Dispatchers.Main) {
            val current = settings.stopOnNetworkLostBypassInterfaces
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val edited = context.editSettingTextList(
                initial = current,
                title = context.getText(R.string.settings_stop_on_network_lost_bypass),
                adapter = TextAdapter.String,
            ) {
                context.editSettingText(
                    initial = "",
                    title = context.getText(R.string.settings_stop_on_network_lost_bypass),
                    allowReset = false,
                    error = context.getString(R.string.settings_invalid_not_blank),
                    validator = { it.isNotBlank() },
                )?.trim()
            } ?: return@launch

            settings.stopOnNetworkLostBypassInterfaces = edited.joinToString(",")

            refresh()
        }
    }

    private fun editWifiNetworks() {
        launch(Dispatchers.Main) {
            val edited = context.editSettingTextList(
                initial = settings.wifiSsidList.toList(),
                title = context.getText(R.string.settings_wifi_networks),
                adapter = TextAdapter.String,
            ) {
                context.editSettingText(
                    initial = "",
                    title = context.getText(R.string.settings_wifi_networks),
                    hint = context.getText(R.string.settings_wifi_networks_placeholder),
                    allowReset = false,
                    error = context.getString(R.string.settings_invalid_not_blank),
                    validator = { it.isNotBlank() },
                )?.trim()
            } ?: return@launch

            settings.wifiSsidList = edited.toSet()

            refresh()
        }
    }

    private fun selectTunStackMode(anchor: View) {
        val values = arrayOf("system", "gvisor", "mixed", "mips")
        val labels = listOf(
            R.string.tun_stack_system,
            R.string.tun_stack_gvisor,
            R.string.tun_stack_mixed,
            R.string.tun_stack_mips,
        )

        val current = values.indexOf(srvStore.tunStackMode).coerceAtLeast(0)

        launch(Dispatchers.Main) {
            val index = context.selectSetting(
                anchor = anchor,
                texts = labels.map { context.getText(it) },
                selected = current,
            )

            if (index != current) {
                srvStore.tunStackMode = values[index]

                refresh()
            }
        }
    }

    private fun selectTunMtu(anchor: View) {
        val values = intArrayOf(1500, 9000)
        val labels = listOf(
            R.string.tun_mtu_1500,
            R.string.tun_mtu_9000,
        )

        val current = values.indexOf(srvStore.tunMtu).coerceAtLeast(0)

        launch(Dispatchers.Main) {
            val index = context.selectSetting(
                anchor = anchor,
                texts = labels.map { context.getText(it) },
                selected = current,
            )

            if (index != current) {
                srvStore.tunMtu = values[index]

                refresh()
            }
        }
    }

    private fun selectAccessControlMode(anchor: View) {
        val values = AccessControlMode.values()
        val labels = listOf(
            R.string.allow_all_apps,
            R.string.allow_selected_apps,
            R.string.deny_selected_apps,
        )

        val current = values.indexOf(srvStore.accessControlMode).coerceAtLeast(0)

        launch(Dispatchers.Main) {
            val index = context.selectSetting(
                anchor = anchor,
                texts = labels.map { context.getText(it) },
                selected = current,
            )

            if (index != current) {
                srvStore.accessControlMode = values[index]

                refresh()
            }
        }
    }

    private fun selectWifiAction(anchor: View) {
        val values = WifiNetworkAction.values()

        val current = values.indexOf(settings.wifiNetworkAction).coerceAtLeast(0)

        launch(Dispatchers.Main) {
            val index = context.selectSetting(
                anchor = anchor,
                texts = values.map { context.getText(wifiActionText(it)) },
                selected = current,
            )

            if (index != current) {
                settings.wifiNetworkAction = values[index]

                refresh()
            }
        }
    }

    // -------------------------------------------------------------- helpers

    private fun numberValidator(min: Int, max: Int): (String) -> Boolean = { text ->
        val value = text.trim().toIntOrNull()

        value != null && value in min..max
    }

    private fun tunStackText(value: String): Int = when (value) {
        "system" -> R.string.tun_stack_system
        "gvisor" -> R.string.tun_stack_gvisor
        "mixed" -> R.string.tun_stack_mixed
        else -> R.string.tun_stack_mips
    }

    private fun accessControlModeText(mode: AccessControlMode): Int = when (mode) {
        AccessControlMode.AcceptAll -> R.string.allow_all_apps
        AccessControlMode.AcceptSelected -> R.string.allow_selected_apps
        AccessControlMode.DenySelected -> R.string.deny_selected_apps
    }

    private fun wifiActionText(action: WifiNetworkAction): Int = when (action) {
        WifiNetworkAction.Disabled -> R.string.settings_wifi_action_disabled
        WifiNetworkAction.Pause -> R.string.settings_wifi_action_pause
        WifiNetworkAction.Direct -> R.string.settings_wifi_action_direct
    }

    private fun hasLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 23)
            return true

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * True when a request could still succeed: the user has not been asked yet,
     * or has denied once (the system still shows the prompt after that).
     */
    private val canRequestLocationPermission: Boolean
        get() {
            if (!settings.wifiPermissionRequested)
                return true

            val activity = context as? Activity ?: return false

            return activity.shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
}
