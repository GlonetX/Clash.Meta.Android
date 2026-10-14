package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.Serializable

@Serializable
data class ProbeResult(
    val code: Int,
    val latency: Long,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readLong(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(code)
        parcel.writeLong(latency)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ProbeResult> {
        override fun createFromParcel(parcel: Parcel): ProbeResult {
            return ProbeResult(parcel)
        }

        override fun newArray(size: Int): Array<ProbeResult?> {
            return arrayOfNulls(size)
        }
    }
}
