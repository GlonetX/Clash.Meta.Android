package com.github.kr328.clash.service.settings

import android.content.Context
import android.content.SharedPreferences

/** Reads the old files once; the version marker and new values are committed together. */
internal object LegacySettingsMigration {
    fun read(context: Context, service: SharedPreferences): SettingsSnapshot {
        val old = context.getSharedPreferences("settings", Context.MODE_PRIVATE).all
        val ui = context.getSharedPreferences("ui", Context.MODE_PRIVATE).all
        // The background Application initializes settings before Room is opened.
        // Thus a pre-existing profiles database is evidence of a legacy install;
        // after user data is cleared it is absent, even if package update times remain.
        val existing = isLegacyInstall(old, ui, service.all, context.getDatabasePath("profiles").exists())
        val migrated = readMaps(old, ui, service.all, existing)
        val component = android.content.ComponentName(context.packageName, "com.github.kr328.clash.RestartReceiver")
        val enabled = context.packageManager.getComponentEnabledSetting(component) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        return migrated.copy(desired = migrated.desired.copy(autoRestart = enabled))
    }

    internal fun isLegacyInstall(old: Map<String, *>, ui: Map<String, *>, service: Map<String, *>, profilesDatabaseExists: Boolean): Boolean =
        old.isNotEmpty() || ui.isNotEmpty() || service.isNotEmpty() || profilesDatabaseExists

    fun readMaps(old: Map<String, *>, ui: Map<String, *>, service: Map<String, *>, existing: Boolean = true): SettingsSnapshot {
        val runtime = RuntimeSettings(
            enableVpn = ui.bool("enable_vpn", true),
            ipv6Mode = if (!existing) Ipv6Mode.Block else if (service.bool("allow_ipv6", false)) Ipv6Mode.Proxy else Ipv6Mode.Direct,
            allowBypass = service.bool("allow_bypass", existing),
            bypassPrivateNetwork = service.bool("bypass_private_network", true),
            accessControlMode = service.string("access_control_mode", "AcceptAll"),
            accessControlPackages = service.strings("access_control_packages"),
            dnsHijacking = service.bool("dns_hijacking", true),
            systemProxy = service.bool("system_proxy", true),
            tunStackMode = service.string("tun_stack_mode", "mips"),
            tunMtu = service.number("tun_mtu", 9000).coerceIn(1280, 9000),
            dynamicNotification = service.bool("dynamic_notification", true),
            notificationStyle = enumValue(old.string("notification_style", "Default"), NotificationStyle.Default),
            stopOnNetworkLost = old.bool("core_stop_on_network_lost", false),
            stopOnNetworkLostInterval = old.number("core_stop_on_network_lost_interval", 30).coerceIn(5, 3600),
            stopOnNetworkLostBypassInterfaces = old.string("core_stop_on_network_lost_bypass_interfaces", "").csv().toSet(),
            keepAliveEnabled = old.bool("core_keep_alive_enabled", false),
            keepAliveInterval = old.number("core_keep_alive_interval", 15).coerceIn(5, 3600),
            keepAliveIdle = old.number("core_keep_alive_idle", 1800).coerceIn(30, 86400),
            storeFakeIp = old.bool("core_store_fake_ip", true),
            logRetentionDays = old.number("log_retention_days", 7).coerceIn(1, 365),
            logMaxSizeMb = old.number("log_max_size_mb", 20).coerceIn(1, 1024),
            logRealtimeBuffer = old.number("log_realtime_buffer", 200).coerceIn(0, 10000),
            wifiNetworkAction = enumValue(old.string("wifi_network_action", "Disabled"), WifiNetworkAction.Disabled),
            wifiSsidList = old.strings("wifi_ssid_list"),
            globalUserAgent = old.string("global_user_agent", "").filter { it >= ' ' && it != '\u007f' }.take(512),
            updateSubscriptionsOnStart = if (old.containsKey("update_subscriptions_on_start")) old.bool("update_subscriptions_on_start", false)
                else ui.bool("profiles_update_on_launch", existing),
            externalControlEnabled = old.bool("external_control_enabled", false),
        )
        val order = old.string("dashboard_widget_order", "").ifEmpty { ui.string("dashboard_card_order", "") }.csv()
        val sizes = old.string("dashboard_widget_size", "").csv().mapNotNull { value ->
            val index = value.indexOf('=')
            if (index <= 0) null else value.substring(0, index) to value.substring(index + 1)
        }.toMap()
        val annotations = old.string("subscription_meta", "").split('\u0001').mapNotNull { row ->
            val f = row.split('\u001F')
            if (f.size == 3) f[0] to SubscriptionAnnotation(f[1].take(256), f[2].take(4096)) else null
        }.toMap()
        return SettingsSnapshot(revision = 1, desired = runtime, ui = UiPreferences(
            darkMode = ui.string("dark_mode", "Auto"), dynamicColor = ui.bool("dynamic_colors_enabled", true),
            textScale = old.number("text_scale", 100).coerceIn(85, 130), appLanguage = old.string("app_language", ""),
            lightweightMode = old.bool("lightweight_mode", false), hideAppIcon = ui.bool("hide_app_icon", false),
            hideFromRecents = ui.bool("hide_from_recents", false), appUpdateAutoCheck = old.bool("app_update_auto_check", true),
            appUpdateChannel = old.string("app_update_channel", "Stable"),
            proxyExcludeNotSelectable = ui.bool("proxy_exclude_not_selectable", false), proxyLine = ui.number("proxy_line", 2).coerceIn(1, 12),
            proxySort = ui.string("proxy_sort", "Default"), proxyLastGroup = ui.string("proxy_last_group", ""),
            proxyDensity = ui.string("proxy_density", "Standard"), proxyIconStyle = ui.string("proxy_icon_style", "None"),
            connectionsSort = ui.string("connections_sort", "Size"), connectionsSortReversed = ui.bool("connections_sort_reversed", false),
            connectionsGroupByProcess = ui.bool("connections_group_by_process", false), connectionsCollapsedGroups = ui.strings("connections_collapsed_groups"),
            dashboardWidgetOrder = order, dashboardWidgetSizes = sizes, dashboardCardHidden = ui.strings("dashboard_card_hidden"),
            probeServiceOrder = old.string("probe_service_order", "").csv(), probeServiceEnabled = old.strings("probe_service_enabled"),
            accessControlSort = ui.string("access_control_sort", "Label"), accessControlReverse = ui.bool("access_control_reverse", false),
            accessControlSystemApp = ui.bool("access_control_system_app", false), subscriptionMetadata = annotations,
        ))
    }
    private fun Map<String, *>.bool(key: String, fallback: Boolean) = get(key) as? Boolean ?: fallback
    private fun Map<String, *>.number(key: String, fallback: Int) = (get(key) as? Number)?.toInt() ?: fallback
    private fun Map<String, *>.string(key: String, fallback: String) = get(key) as? String ?: fallback
    private fun Map<String, *>.strings(key: String) = (get(key) as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
    private fun String.csv() = split(',').map(String::trim).filter(String::isNotEmpty)
    private inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T = enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
