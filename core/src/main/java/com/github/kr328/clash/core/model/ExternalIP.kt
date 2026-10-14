package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExternalIP(
    val ip: String,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val city: String? = null,
    val isp: String? = null,
    val asn: String? = null,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(ip)
        parcel.writeString(country)
        parcel.writeString(countryCode)
        parcel.writeString(city)
        parcel.writeString(isp)
        parcel.writeString(asn)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ExternalIP> {
        override fun createFromParcel(parcel: Parcel): ExternalIP {
            return ExternalIP(parcel)
        }

        override fun newArray(size: Int): Array<ExternalIP?> {
            return arrayOfNulls(size)
        }
    }
}
