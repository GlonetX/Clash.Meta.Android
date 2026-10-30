package com.github.kr328.clash.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.service.settings.*

/** Pure presentation. Every committed value comes back from the background settings snapshot. */
@Composable
fun SettingsScreen(
    page: String,
    snapshot: SettingsSnapshot?,
    onRuntime: ((RuntimeSettings) -> RuntimeSettings) -> Unit,
    onUi: ((UiPreferences) -> UiPreferences) -> Unit,
    onNavigate: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onPermission: () -> Unit,
    onBatterySettings: () -> Unit = {},
) {
    if (snapshot == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    val r = snapshot.desired
    val u = snapshot.ui
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (snapshot.pendingRestartFields.isNotEmpty()) item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.cs_pending), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { onNavigate("reconnect") }) { Text(stringResource(R.string.cs_reconnect)) }
                }
            }
        }
        snapshot.lastError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        when(page) {
            "settings" -> item {
                SettingSection(stringResource(R.string.cs_settings)) {
                    SettingAction(stringResource(R.string.cs_application), stringResource(R.string.cs_application_summary)) { onNavigate("app-settings") }
                    SettingAction(stringResource(R.string.cs_network), stringResource(R.string.cs_network_summary)) { onNavigate("network-settings") }
                    SettingAction(stringResource(R.string.cs_override)) { onNavigate("override") }
                    SettingAction(stringResource(R.string.cs_meta)) { onNavigate("meta-feature") }
                    SettingAction(stringResource(R.string.cs_profiles)) { onNavigate("profiles") }
                    SettingAction(stringResource(R.string.cs_backup), stringResource(R.string.cs_backup_summary)) { onNavigate("backup") }
                    SettingAction(stringResource(R.string.cs_logs)) { onNavigate("logs") }
                    SettingAction(stringResource(R.string.cs_help)) { onNavigate("help") }
                    SettingAction(stringResource(R.string.cs_about)) { onNavigate("about") }
                }
            }
            "app-settings" -> {
                item { SettingSection(stringResource(R.string.cs_appearance)) {
                    SettingChoice(stringResource(R.string.cs_theme), u.darkMode, listOf(
                        "Auto" to stringResource(R.string.cs_system), "ForceLight" to stringResource(R.string.cs_light),
                        "ForceDark" to stringResource(R.string.cs_dark), "ForceBlack" to stringResource(R.string.cs_black)
                    )) { value -> onUi { it.copy(darkMode = value) } }
                    SettingSwitch(stringResource(R.string.cs_dynamic), u.dynamicColor) { value -> onUi { it.copy(dynamicColor = value) } }
                    SettingNumber(stringResource(R.string.cs_text_size), u.textScale, 85..130) { value -> onUi { it.copy(textScale = value) } }
                    SettingChoice(stringResource(R.string.cs_language), u.appLanguage, listOf("" to stringResource(R.string.cs_system), "zh" to "简体中文", "en" to "English")) { value -> onUi { it.copy(appLanguage = value) } }
                    SettingSwitch(stringResource(R.string.cs_lightweight), u.lightweightMode, stringResource(R.string.cs_lightweight_summary)) { value -> onUi { it.copy(lightweightMode = value) } }
                } }
                item { SettingSection(stringResource(R.string.cs_behaviour)) {
                    SettingSwitch(text("Start after device restart", "设备重启后启动"), r.autoRestart) { value -> onRuntime { it.copy(autoRestart = value) } }
                    SettingAction(text("Battery optimization settings", "电池优化设置"), onClick = onBatterySettings)
                    SettingSwitch(stringResource(R.string.cs_hide_recent), u.hideFromRecents) { value -> onUi { it.copy(hideFromRecents = value) } }
                    SettingSwitch(stringResource(R.string.cs_hide_icon), u.hideAppIcon, stringResource(R.string.cs_hide_icon_summary)) { value -> onUi { it.copy(hideAppIcon = value) } }
                    SettingSwitch(stringResource(R.string.cs_external), r.externalControlEnabled, stringResource(R.string.cs_external_summary)) { value -> onRuntime { it.copy(externalControlEnabled = value) } }
                    SettingSwitch(stringResource(R.string.cs_update_profiles), r.updateSubscriptionsOnStart) { value -> onRuntime { it.copy(updateSubscriptionsOnStart = value) } }
                    SettingChoice(stringResource(R.string.cs_notification), r.notificationStyle, listOf(
                        NotificationStyle.Default to stringResource(R.string.cs_standard), NotificationStyle.Compact to stringResource(R.string.cs_compact), NotificationStyle.Minimal to stringResource(R.string.cs_minimal)
                    )) { value -> onRuntime { it.copy(notificationStyle = value) } }
                    SettingSwitch(stringResource(R.string.cs_dynamic_notification), r.dynamicNotification) { value -> onRuntime { it.copy(dynamicNotification = value) } }
                } }
                item { SettingSection(stringResource(R.string.cs_updates)) {
                    SettingSwitch(stringResource(R.string.cs_auto_update), u.appUpdateAutoCheck) { value -> onUi { it.copy(appUpdateAutoCheck = value) } }
                    SettingChoice(stringResource(R.string.cs_update_channel), u.appUpdateChannel, listOf("Stable" to stringResource(R.string.cs_stable), "Prerelease" to stringResource(R.string.cs_prerelease))) { value -> onUi { it.copy(appUpdateChannel = value) } }
                    SettingAction(stringResource(R.string.cs_check_update), onClick = onCheckUpdate)
                    SettingAction(stringResource(R.string.cs_backup), stringResource(R.string.cs_backup_summary)) { onNavigate("backup") }
                } }
            }
            "network-settings" -> {
                item { SettingSection(stringResource(R.string.cs_vpn)) {
                    SettingSwitch(stringResource(R.string.cs_vpn_mode), r.enableVpn, stringResource(R.string.cs_vpn_summary)) { value -> onRuntime { it.copy(enableVpn = value) } }
                    SettingChoice(stringResource(R.string.cs_ipv6), r.ipv6Mode, listOf(Ipv6Mode.Block to stringResource(R.string.cs_block), Ipv6Mode.Proxy to stringResource(R.string.cs_proxy), Ipv6Mode.Direct to stringResource(R.string.cs_direct))) { value -> onRuntime { it.copy(ipv6Mode = value) } }
                    SettingSwitch(stringResource(R.string.cs_bypass), r.allowBypass, stringResource(R.string.cs_bypass_summary)) { value -> onRuntime { it.copy(allowBypass = value) } }
                    SettingSwitch(stringResource(R.string.cs_lan), r.bypassPrivateNetwork) { value -> onRuntime { it.copy(bypassPrivateNetwork = value) } }
                    SettingSwitch(stringResource(R.string.cs_dns), r.dnsHijacking) { value -> onRuntime { it.copy(dnsHijacking = value) } }
                    SettingSwitch(stringResource(R.string.cs_system_proxy), r.systemProxy) { value -> onRuntime { it.copy(systemProxy = value) } }
                    SettingChoice(stringResource(R.string.cs_stack), r.tunStackMode, listOf("system", "gvisor", "mixed", "mips").map { it to it }) { value -> onRuntime { it.copy(tunStackMode = value) } }
                    SettingNumber("MTU", r.tunMtu, 1280..9000) { value -> onRuntime { it.copy(tunMtu = value) } }
                    SettingAction(stringResource(R.string.cs_apps)) { onNavigate("access") }
                } }
                item { SettingSection(stringResource(R.string.cs_core)) {
                    SettingSwitch(stringResource(R.string.cs_keepalive), r.keepAliveEnabled) { value -> onRuntime { it.copy(keepAliveEnabled = value) } }
                    SettingNumber(stringResource(R.string.cs_keepalive_interval), r.keepAliveInterval, 5..3600) { value -> onRuntime { it.copy(keepAliveInterval = value) } }
                    SettingNumber(stringResource(R.string.cs_keepalive_idle), r.keepAliveIdle, 30..86400) { value -> onRuntime { it.copy(keepAliveIdle = value) } }
                    SettingSwitch(stringResource(R.string.cs_fake_ip), r.storeFakeIp) { value -> onRuntime { it.copy(storeFakeIp = value) } }
                    SettingText(stringResource(R.string.cs_user_agent), r.globalUserAgent, maxLength = 512, singleLine = true) { value -> onRuntime { it.copy(globalUserAgent = value) } }
                    SettingSwitch(stringResource(R.string.cs_stop_lost), r.stopOnNetworkLost, stringResource(R.string.cs_stop_lost_summary)) { value -> onRuntime { it.copy(stopOnNetworkLost = value) } }
                    SettingNumber(stringResource(R.string.cs_stop_delay), r.stopOnNetworkLostInterval, 5..3600) { value -> onRuntime { it.copy(stopOnNetworkLostInterval = value) } }
                    SettingText(stringResource(R.string.cs_interface_exceptions), r.stopOnNetworkLostBypassInterfaces.joinToString("\n")) { value -> onRuntime { it.copy(stopOnNetworkLostBypassInterfaces = value.lines().map(String::trim).filter(String::isNotEmpty).toSet()) } }
                } }
                item { SettingSection(stringResource(R.string.cs_wifi)) {
                    SettingChoice(stringResource(R.string.cs_wifi_action), r.wifiNetworkAction, listOf(WifiNetworkAction.Disabled to stringResource(R.string.cs_disabled), WifiNetworkAction.Pause to stringResource(R.string.cs_pause), WifiNetworkAction.Direct to stringResource(R.string.cs_direct))) { value ->
                        if(value != WifiNetworkAction.Disabled) onPermission()
                        onRuntime { it.copy(wifiNetworkAction = value) }
                    }
                    Text(stringResource(R.string.cs_wifi_summary), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingText(stringResource(R.string.cs_ssids), r.wifiSsidList.joinToString("\n")) { value -> onRuntime { it.copy(wifiSsidList = value.lines().map(String::trim).filter(String::isNotEmpty).toSet()) } }
                    snapshot.policyReason?.let { Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
                    SettingAction(stringResource(R.string.cs_permissions), onClick = onPermission)
                } }
                item { SettingSection(stringResource(R.string.cs_logging)) {
                    SettingNumber(stringResource(R.string.cs_retention), r.logRetentionDays, 1..365) { value -> onRuntime { it.copy(logRetentionDays = value) } }
                    SettingNumber(stringResource(R.string.cs_log_size), r.logMaxSizeMb, 1..1024) { value -> onRuntime { it.copy(logMaxSizeMb = value) } }
                    SettingNumber(stringResource(R.string.cs_log_buffer), r.logRealtimeBuffer, 0..10000) { value -> onRuntime { it.copy(logRealtimeBuffer = value) } }
                } }
            }
        }
    }
}

@Composable
fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(vertical = 4.dp), content = content) }
    }
}

@Composable
fun SettingAction(title: String, summary: String? = null, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = summary?.let { { Text(it) } }, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
fun SettingSwitch(title: String, checked: Boolean, summary: String? = null, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) })
}

@Composable
fun <T> SettingChoice(title: String, selected: T, choices: List<Pair<T, String>>, onChange: (T) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingAction(title, choices.firstOrNull { it.first == selected }?.second) { open = true }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) },
        text = { Column { choices.forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable { open = false; onChange(value) }.padding(vertical = 4.dp)) {
                RadioButton(selected == value, onClick = { open = false; onChange(value) })
                Text(label, Modifier.padding(top = 12.dp))
            }
        } } }, confirmButton = {}, dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cs_cancel)) } })
}

@Composable
fun SettingNumber(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    SettingText(title, value.toString(), singleLine = true,
        validate = { it.toIntOrNull()?.let(range::contains) == true }, hint = "${range.first}–${range.last}") { onChange(it.toInt()) }
}

@Composable
fun SettingText(title: String, value: String, maxLength: Int = 4096, singleLine: Boolean = false,
                validate: (String) -> Boolean = { true }, hint: String = "", onChange: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(value) }
    SettingAction(title, value.ifBlank { stringResource(R.string.cs_not_set) }) { draft = value; open = true }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = {
        OutlinedTextField(value = draft, onValueChange = { if(it.length <= maxLength) draft = it },
            singleLine = singleLine, minLines = if(singleLine) 1 else 3, maxLines = 6,
            supportingText = { Text(hint) }, isError = !validate(draft))
    }, confirmButton = { TextButton(onClick = { open = false; onChange(draft) }, enabled = validate(draft)) { Text(stringResource(R.string.cs_save)) } },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cs_cancel)) } })
}
