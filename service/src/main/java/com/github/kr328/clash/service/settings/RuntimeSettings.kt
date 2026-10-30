package com.github.kr328.clash.service.settings

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

@Serializable
data class RuntimeSettings(
    val autoRestart: Boolean = false,
    val enableVpn: Boolean = true,
    val ipv6Mode: Ipv6Mode = Ipv6Mode.Block,
    val allowBypass: Boolean = false,
    val bypassPrivateNetwork: Boolean = true,
    val accessControlMode: String = "AcceptAll",
    val accessControlPackages: Set<String> = emptySet(),
    val dnsHijacking: Boolean = true,
    val systemProxy: Boolean = true,
    val tunStackMode: String = "mips",
    val tunMtu: Int = 9000,
    val dynamicNotification: Boolean = true,
    val notificationStyle: NotificationStyle = NotificationStyle.Default,
    val stopOnNetworkLost: Boolean = false,
    val stopOnNetworkLostInterval: Int = 30,
    val stopOnNetworkLostBypassInterfaces: Set<String> = emptySet(),
    val keepAliveEnabled: Boolean = false,
    val keepAliveInterval: Int = 15,
    val keepAliveIdle: Int = 1800,
    val storeFakeIp: Boolean = true,
    val logRetentionDays: Int = 7,
    val logMaxSizeMb: Int = 20,
    val logRealtimeBuffer: Int = 200,
    val wifiNetworkAction: WifiNetworkAction = WifiNetworkAction.Disabled,
    val wifiSsidList: Set<String> = emptySet(),
    val globalUserAgent: String = "",
    val updateSubscriptionsOnStart: Boolean = false,
    val externalControlEnabled: Boolean = false,
) : Parcelable {
    override fun describeContents(): Int = 0
    override fun writeToParcel(parcel: Parcel, flags: Int) = Parcelizer.encodeToParcel(serializer(), parcel, this)
    companion object CREATOR : Parcelable.Creator<RuntimeSettings> {
        override fun createFromParcel(parcel: Parcel): RuntimeSettings = Parcelizer.decodeFromParcel(serializer(), parcel)
        override fun newArray(size: Int): Array<RuntimeSettings?> = arrayOfNulls(size)
    }
}
