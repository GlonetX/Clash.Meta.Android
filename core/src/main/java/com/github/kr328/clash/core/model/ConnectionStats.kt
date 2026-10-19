package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

/**
 * Aggregate connection counters for the dashboard.
 *
 * The dashboard only needs these three numbers, so returning them directly
 * avoids serialising the whole connection list across the binder once per
 * tick (which is O(connections / parcel slice) transactions).
 */
@Serializable
data class ConnectionStats(
    val total: Int,
    val requests: Int,
    val dns: Int,
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ConnectionStats> {
        override fun createFromParcel(parcel: Parcel): ConnectionStats {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<ConnectionStats?> {
            return arrayOfNulls(size)
        }
    }
}
