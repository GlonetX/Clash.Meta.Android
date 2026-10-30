package com.github.kr328.clash.service.settings

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

@Serializable
data class SettingsUpdate(
    val runtime: RuntimeSettings? = null,
    val ui: UiPreferences? = null,
) : Parcelable {
    override fun describeContents(): Int = 0
    override fun writeToParcel(parcel: Parcel, flags: Int) = Parcelizer.encodeToParcel(serializer(), parcel, this)
    companion object CREATOR : Parcelable.Creator<SettingsUpdate> {
        override fun createFromParcel(parcel: Parcel): SettingsUpdate = Parcelizer.decodeFromParcel(serializer(), parcel)
        override fun newArray(size: Int): Array<SettingsUpdate?> = arrayOfNulls(size)
    }
}
