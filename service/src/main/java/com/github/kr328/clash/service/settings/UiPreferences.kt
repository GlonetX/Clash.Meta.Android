package com.github.kr328.clash.service.settings

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

@Serializable
data class UiPreferences(
    val darkMode: String = "Auto",
    val dynamicColor: Boolean = true,
    val textScale: Int = 100,
    val appLanguage: String = "",
    val lightweightMode: Boolean = false,
    val hideAppIcon: Boolean = false,
    val hideFromRecents: Boolean = false,
    val appUpdateAutoCheck: Boolean = true,
    val appUpdateChannel: String = "Stable",
    val proxyExcludeNotSelectable: Boolean = false,
    val proxyLine: Int = 2,
    val proxySort: String = "Default",
    val proxyLastGroup: String = "",
    val proxyDensity: String = "Standard",
    val proxyIconStyle: String = "None",
    val connectionsSort: String = "Size",
    val connectionsSortReversed: Boolean = false,
    val connectionsGroupByProcess: Boolean = false,
    val connectionsCollapsedGroups: Set<String> = emptySet(),
    val dashboardWidgetOrder: List<String> = emptyList(),
    val dashboardWidgetSizes: Map<String, String> = emptyMap(),
    val dashboardCardHidden: Set<String> = emptySet(),
    val probeServiceOrder: List<String> = emptyList(),
    val probeServiceEnabled: Set<String> = emptySet(),
    val accessControlSort: String = "Label",
    val accessControlReverse: Boolean = false,
    val accessControlSystemApp: Boolean = false,
    val subscriptionMetadata: Map<String, SubscriptionAnnotation> = emptyMap(),
) : Parcelable {
    override fun describeContents(): Int = 0
    override fun writeToParcel(parcel: Parcel, flags: Int) = Parcelizer.encodeToParcel(serializer(), parcel, this)
    companion object CREATOR : Parcelable.Creator<UiPreferences> {
        override fun createFromParcel(parcel: Parcel): UiPreferences = Parcelizer.decodeFromParcel(serializer(), parcel)
        override fun newArray(size: Int): Array<UiPreferences?> = arrayOfNulls(size)
    }
}
