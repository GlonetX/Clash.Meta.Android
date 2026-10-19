package com.github.kr328.clash.design.settings

import android.content.Context
import android.content.SharedPreferences
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.design.store.DynamicColorsStore
import java.security.SecureRandom

enum class UpdateChannel { Stable, Prerelease }

enum class NotificationStyle { Default, Compact, Minimal }

/** What the core should do while connected to one of the named Wi-Fi networks. */
enum class WifiNetworkAction { Disabled, Pause, Direct }

/** Dashboard widget occupancy: one span, half a span, or gone. */
enum class WidgetSize { Large, Small, Hidden }

/** A user annotation attached to one subscription, keyed by profile UUID. */
data class SubscriptionMeta(
    val uuid: String,
    val tag: String = "",
    val note: String = "",
)

/**
 * Persistence for the settings that have no home in [com.github.kr328.clash.design.store.UiStore]
 * or the service store — the mihomo config knobs, the appearance choices and the
 * backup credentials.
 *
 * Backed by its own `SharedPreferences` file so that adding a setting here can
 * never collide with a worktree that is editing `UiStore`/`ServiceStore`.
 * Every property below is a real, immediately-persisted value; nothing is
 * cached in memory only.
 *
 * NOTE FOR OTHER WORKTREES: the service module cannot read this file. Keys
 * that the *core* has to honour (keep-alive, log retention, Wi-Fi pause, …)
 * need to be mirrored into `ServiceStore` before the service can act on them.
 * See OVERHAUL notes; the mapping is one-to-one and listed in the phase-B
 * report.
 */
class SettingsStore(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    private val store = Store(prefs.asStoreProvider())

    // ------------------------------------------------------------------ core

    var stopOnNetworkLost: Boolean by store.boolean(
        key = "core_stop_on_network_lost",
        defaultValue = false,
    )

    /** Seconds between "is the network still there?" probes. */
    var stopOnNetworkLostInterval: Int by store.int(
        key = "core_stop_on_network_lost_interval",
        defaultValue = 30,
    )

    /** Comma separated interface names that never trigger a stop. */
    var stopOnNetworkLostBypassInterfaces: String by store.string(
        key = "core_stop_on_network_lost_bypass_interfaces",
        defaultValue = "",
    )

    var keepAliveEnabled: Boolean by store.boolean(
        key = "core_keep_alive_enabled",
        defaultValue = false,
    )

    var keepAliveInterval: Int by store.int(
        key = "core_keep_alive_interval",
        defaultValue = 15,
    )

    var keepAliveIdle: Int by store.int(
        key = "core_keep_alive_idle",
        defaultValue = 1800,
    )

    /** mihomo `profile.store-fake-ip`. */
    var storeFakeIp: Boolean by store.boolean(
        key = "core_store_fake_ip",
        defaultValue = true,
    )

    // ------------------------------------------------------------------ logs

    var logRetentionDays: Int by store.int(
        key = "log_retention_days",
        defaultValue = 7,
    )

    var logMaxSizeMb: Int by store.int(
        key = "log_max_size_mb",
        defaultValue = 20,
    )

    var logRealtimeBuffer: Int by store.int(
        key = "log_realtime_buffer",
        defaultValue = 200,
    )

    // ------------------------------------------------------------------ wifi

    var wifiNetworkAction: WifiNetworkAction by store.enum(
        key = "wifi_network_action",
        defaultValue = WifiNetworkAction.Disabled,
        values = WifiNetworkAction.values(),
    )

    var wifiSsidList: Set<String> by store.stringSet(
        key = "wifi_ssid_list",
        defaultValue = emptySet(),
    )

    /**
     * Whether the location permission has ever been asked for. Combined with
     * `shouldShowRequestPermissionRationale` this distinguishes "not asked yet"
     * from "denied once" from "denied permanently".
     */
    var wifiPermissionRequested: Boolean by store.boolean(
        key = "wifi_permission_requested",
        defaultValue = false,
    )

    // ------------------------------------------------------------- behaviour

    /**
     * Allow other apps / browser deep links to start, stop or toggle the VPN and
     * to import a profile via `clash://install-config`. Off by default: an
     * exported control surface must be opt-in.
     */
    var externalControlEnabled: Boolean by store.boolean(
        key = "external_control_enabled",
        defaultValue = false,
    )

    /**
     * Random per-install token embedded in the app's own dynamic shortcuts so
     * they keep working while the external control surface is disabled. It
     * lives in this app-private preference file, so other apps cannot read it.
     */
    var externalControlToken: String by store.string(
        key = "external_control_token",
        defaultValue = "",
    )

    /** Returns the existing shortcut token, creating one on first use. */
    fun ensureExternalControlToken(): String {
        val current = externalControlToken

        if (current.isNotBlank())
            return current

        val bytes = ByteArray(16)

        SecureRandom().nextBytes(bytes)

        val token = bytes.joinToString("") { "%02x".format(it) }

        externalControlToken = token

        return token
    }

    var appUpdateAutoCheck: Boolean by store.boolean(
        key = "app_update_auto_check",
        defaultValue = true,
    )

    var appUpdateChannel: UpdateChannel by store.enum(
        key = "app_update_channel",
        defaultValue = UpdateChannel.Stable,
        values = UpdateChannel.values(),
    )

    var notificationStyle: NotificationStyle by store.enum(
        key = "notification_style",
        defaultValue = NotificationStyle.Default,
        values = NotificationStyle.values(),
    )

    var lightweightMode: Boolean by store.boolean(
        key = "lightweight_mode",
        defaultValue = false,
    )

    /** Empty string means "follow the system locale". */
    var appLanguage: String by store.string(
        key = "app_language",
        defaultValue = "",
    )

    // ------------------------------------------------------------ appearance

    /**
     * Material You dynamic colour. Delegates to [DynamicColorsStore] so the
     * settings-page switch and MainApplication's gate read/write the same
     * `ui`-prefs key (`dynamic_colors_enabled`) instead of drifting apart on
     * two keys in two files.
     */
    var dynamicColor: Boolean
        get() = DynamicColorsStore(context).enabled
        set(value) {
            DynamicColorsStore(context).enabled = value
        }

    /** Percent, clamped to [TEXT_SCALE_MIN]..[TEXT_SCALE_MAX] on read. */
    var textScale: Int by store.int(
        key = "text_scale",
        defaultValue = 100,
    )

    val textScalePercent: Int
        get() = textScale.coerceIn(TEXT_SCALE_MIN, TEXT_SCALE_MAX)

    // ------------------------------------------------------------- dashboard

    private var dashboardWidgetOrderRaw: String by store.string(
        key = "dashboard_widget_order",
        defaultValue = "",
    )

    private var dashboardWidgetSizeRaw: String by store.string(
        key = "dashboard_widget_size",
        defaultValue = "",
    )

    var probeServiceOrderRaw: String by store.string(
        key = "probe_service_order",
        defaultValue = "",
    )

    var probeServiceEnabled: Set<String> by store.stringSet(
        key = "probe_service_enabled",
        defaultValue = emptySet(),
    )

    // ---------------------------------------------------------------- config

    var webDavUrl: String by store.string(key = "webdav_url", defaultValue = "")
    var webDavDirectory: String by store.string(key = "webdav_directory", defaultValue = "clash")
    var webDavUsername: String by store.string(key = "webdav_username", defaultValue = "")
    var webDavPassword: String by store.string(key = "webdav_password", defaultValue = "")
    var webDavLastBackup: Long by store.long(key = "webdav_last_backup", defaultValue = 0L)

    var globalUserAgent: String by store.string(key = "global_user_agent", defaultValue = "")

    var updateSubscriptionsOnStart: Boolean by store.boolean(
        key = "update_subscriptions_on_start",
        defaultValue = false,
    )

    // ------------------------------------------------------------- utilities

    /** Widget order, with unknown ids dropped and missing ids appended. */
    fun dashboardWidgetOrder(catalog: List<String>): List<String> {
        val stored = dashboardWidgetOrderRaw.toCsv()

        val known = stored.filter { it in catalog }

        return known + catalog.filter { it !in known }
    }

    fun setDashboardWidgetOrder(order: List<String>) {
        dashboardWidgetOrderRaw = order.joinToString(",")
    }

    fun dashboardWidgetSize(key: String): WidgetSize {
        val stored = dashboardWidgetSizeRaw
            .split(',')
            .mapNotNull { entry ->
                val idx = entry.indexOf('=')

                if (idx <= 0)
                    null
                else
                    entry.substring(0, idx) to entry.substring(idx + 1)
            }
            .toMap()

        return stored[key]?.let { name ->
            WidgetSize.values().firstOrNull { it.name == name }
        } ?: WidgetSize.Large
    }

    fun dashboardWidgetSizes(): Map<String, WidgetSize> {
        return dashboardWidgetSizeRaw
            .split(',')
            .mapNotNull { entry ->
                val idx = entry.indexOf('=')

                if (idx <= 0)
                    null
                else
                    entry.substring(0, idx) to entry.substring(idx + 1)
            }
            .mapNotNull { (key, name) ->
                WidgetSize.values().firstOrNull { it.name == name }?.let { key to it }
            }
            .toMap()
    }

    fun setDashboardWidgetSize(key: String, size: WidgetSize) {
        val sizes = dashboardWidgetSizes().toMutableMap()

        sizes[key] = size

        dashboardWidgetSizeRaw = sizes.entries.joinToString(",") { "${it.key}=${it.value.name}" }
    }

    fun probeServiceOrder(catalog: List<String>): List<String> {
        val stored = probeServiceOrderRaw.toCsv()

        val known = stored.filter { it in catalog }

        return known + catalog.filter { it !in known }
    }

    fun setProbeServiceOrder(order: List<String>) {
        probeServiceOrderRaw = order.joinToString(",")
    }

    // ------------------------------------------------- subscription metadata

    fun subscriptionMeta(): Map<String, SubscriptionMeta> {
        val raw = prefs.getString(KEY_SUBSCRIPTION_META, "").orEmpty()

        if (raw.isEmpty())
            return emptyMap()

        return raw.split(RECORD_SEPARATOR)
            .mapNotNull { record ->
                val fields = record.split(FIELD_SEPARATOR)

                if (fields.size != 3)
                    null
                else
                    SubscriptionMeta(fields[0], fields[1], fields[2])
            }
            .associateBy { it.uuid }
    }

    fun setSubscriptionMeta(uuid: String, tag: String, note: String) {
        val all = subscriptionMeta().toMutableMap()

        if (tag.isEmpty() && note.isEmpty()) {
            all.remove(uuid)
        } else {
            all[uuid] = SubscriptionMeta(uuid, tag, note)
        }

        val encoded = all.values
            .joinToString(RECORD_SEPARATOR) { meta ->
                listOf(meta.uuid, meta.tag, meta.note)
                    .joinToString(FIELD_SEPARATOR) { it.replaceControlChars() }
            }

        prefs.edit().putString(KEY_SUBSCRIPTION_META, encoded).apply()
    }

    companion object {
        private const val PREFERENCE_NAME = "settings"

        private const val KEY_SUBSCRIPTION_META = "subscription_meta"

        private const val RECORD_SEPARATOR = "\u0001"
        private const val FIELD_SEPARATOR = "\u001F"

        const val TEXT_SCALE_MIN = 85
        const val TEXT_SCALE_MAX = 130
        const val TEXT_SCALE_STEP = 5

        private fun String.toCsv(): List<String> =
            split(',').map { it.trim() }.filter { it.isNotEmpty() }

        private fun String.replaceControlChars(): String =
            replace(RECORD_SEPARATOR, " ").replace(FIELD_SEPARATOR, " ")
    }
}
