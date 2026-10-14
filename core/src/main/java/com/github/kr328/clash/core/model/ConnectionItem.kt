package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionItem(
    val id: String,
    val network: String,
    val type: String,
    val host: String,
    val destination: String,
    val source: String,
    val process: String? = null,
    val rule: String? = null,
    val chains: String? = null,
    val upload: Long,
    val download: Long,
    val start: Long,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(network)
        parcel.writeString(type)
        parcel.writeString(host)
        parcel.writeString(destination)
        parcel.writeString(source)
        parcel.writeString(process)
        parcel.writeString(rule)
        parcel.writeString(chains)
        parcel.writeLong(upload)
        parcel.writeLong(download)
        parcel.writeLong(start)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ConnectionItem> {
        override fun createFromParcel(parcel: Parcel): ConnectionItem {
            return ConnectionItem(parcel)
        }

        override fun newArray(size: Int): Array<ConnectionItem?> {
            return arrayOfNulls(size)
        }
    }
}
