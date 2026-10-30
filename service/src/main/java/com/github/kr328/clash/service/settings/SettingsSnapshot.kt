package com.github.kr328.clash.service.settings

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

@Serializable
data class SettingsSnapshot(
    val revision: Long = 0,
    val appliedRevision: Long? = null,
    val runtimeGeneration: Long = 0,
    val phase: RuntimePhase = RuntimePhase.Stopped,
    val desired: RuntimeSettings = RuntimeSettings(),
    val ui: UiPreferences = UiPreferences(),
    val pendingRestartFields: List<String> = emptyList(),
    val trafficPolicy: TrafficPolicy = TrafficPolicy.Normal,
    val policyReason: String? = null,
    val lastError: String? = null,
) : Parcelable {
    override fun describeContents(): Int = 0
    override fun writeToParcel(parcel: Parcel, flags: Int) = Parcelizer.encodeToParcel(serializer(), parcel, this)
    companion object CREATOR : Parcelable.Creator<SettingsSnapshot> {
        override fun createFromParcel(parcel: Parcel): SettingsSnapshot = Parcelizer.decodeFromParcel(serializer(), parcel)
        override fun newArray(size: Int): Array<SettingsSnapshot?> = arrayOfNulls(size)
    }
}
