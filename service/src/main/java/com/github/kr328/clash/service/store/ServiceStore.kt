package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.model.AccessControlMode
import java.util.*
import com.github.kr328.clash.service.settings.RuntimeSettings
import com.github.kr328.clash.service.settings.Ipv6Mode
import com.github.kr328.clash.service.settings.SettingsManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ServiceStore(private val context: Context) {
    private val preferences = PreferenceProvider.createSharedPreferencesFromContext(context)
    private val store = Store(preferences.asStoreProvider())
    private fun runtime(): RuntimeSettings? = preferences.getString(SettingsManager.KEY_RUNTIME, null)?.let {
        runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<RuntimeSettings>(it) }.getOrNull()
    }
    val enableVpn: Boolean get() = resolveVpnEnabled(runtime()?.enableVpn) {
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("enable_vpn", true)
    }
    val ipv6Mode: Ipv6Mode get() = runtime()?.ipv6Mode ?: if (legacyAllowIpv6) Ipv6Mode.Proxy else Ipv6Mode.Direct

    var activeProfile: UUID? by store.typedString(
        key = "active_profile",
        from = { if (it.isBlank()) null else UUID.fromString(it) },
        to = { it?.toString() ?: "" }
    )

    private var legacyBypassPrivateNetwork: Boolean by store.boolean(
        key = "bypass_private_network",
        defaultValue = true
    )

    private var legacyAccessControlMode: AccessControlMode by store.enum(
        key = "access_control_mode",
        defaultValue = AccessControlMode.AcceptAll,
        values = AccessControlMode.values()
    )

    private var legacyAccessControlPackages by store.stringSet(
        key = "access_control_packages",
        defaultValue = emptySet()
    )

    private var legacyDnsHijacking by store.boolean(
        key = "dns_hijacking",
        defaultValue = true
    )

    private var legacySystemProxy by store.boolean(
        key = "system_proxy",
        defaultValue = true
    )

    private var legacyAllowBypass by store.boolean(
        key = "allow_bypass",
        defaultValue = true
    )

    private var legacyAllowIpv6 by store.boolean(
        key = "allow_ipv6",
        defaultValue = false
    )

    private var legacyTunStackMode by store.string(
        key = "tun_stack_mode",
        defaultValue = "mips"
    )

    private var legacyTunMtu: Int by store.int(
        key = "tun_mtu",
        defaultValue = 9000
    )

    private var legacyDynamicNotification by store.boolean(
        key = "dynamic_notification",
        defaultValue = true
    )
    @Deprecated("Use ISettingsManager.update for writes")
    var bypassPrivateNetwork: Boolean
        get() = runtime()?.bypassPrivateNetwork ?: legacyBypassPrivateNetwork
        set(value) { legacyBypassPrivateNetwork = value }
    @Deprecated("Use ISettingsManager.update for writes")
    var accessControlMode: AccessControlMode
        get() = runtime()?.accessControlMode?.let { runCatching { AccessControlMode.valueOf(it) }.getOrNull() } ?: legacyAccessControlMode
        set(value) { legacyAccessControlMode = value }
    @Deprecated("Use ISettingsManager.update for writes")
    var accessControlPackages: Set<String>
        get() = runtime()?.accessControlPackages ?: legacyAccessControlPackages
        set(value) { legacyAccessControlPackages = value }
    @Deprecated("Use ISettingsManager.update for writes")
    var dnsHijacking: Boolean
        get() = runtime()?.dnsHijacking ?: legacyDnsHijacking
        set(value) { legacyDnsHijacking = value }
    @Deprecated("Use ISettingsManager.update for writes")
    var systemProxy: Boolean
        get() = runtime()?.systemProxy ?: legacySystemProxy
        set(value) { legacySystemProxy = value }
    @Deprecated("Use ISettingsManager.update for writes")
    var allowBypass: Boolean
        get() = runtime()?.allowBypass ?: legacyAllowBypass
        set(value) { legacyAllowBypass = value }
    @Deprecated("Use ISettingsManager.update for writes")
    var allowIpv6: Boolean
        get() = ipv6Mode == Ipv6Mode.Proxy
        set(value) { legacyAllowIpv6 = value }
    @Deprecated("Use ISettingsManager.update for writes")
    var tunStackMode: String
        get() = runtime()?.tunStackMode ?: legacyTunStackMode
        set(value) { legacyTunStackMode = value }
    @Deprecated("Use ISettingsManager.update for writes")
    var tunMtu: Int
        get() = runtime()?.tunMtu ?: legacyTunMtu
        set(value) { legacyTunMtu = value }
    @Deprecated("Use ISettingsManager.update for writes")
    var dynamicNotification: Boolean
        get() = runtime()?.dynamicNotification ?: legacyDynamicNotification
        set(value) { legacyDynamicNotification = value }
}

internal fun resolveVpnEnabled(migrated: Boolean?, legacy: () -> Boolean): Boolean = migrated ?: legacy()
