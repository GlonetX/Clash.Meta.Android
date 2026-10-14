package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IPQuality(
    val ip: String,
    val asn: String? = null,
    @SerialName("scamalyticsScore") val scamalyticsScore: String? = null,
    @SerialName("scamalyticsRisk") val scamalyticsRisk: String? = null,
    @SerialName("proxyCheckRisk") val proxyCheckRisk: String? = null,
    @SerialName("proxyCheckProxy") val proxyCheckProxy: String? = null,
    @SerialName("proxyCheckType") val proxyCheckType: String? = null,
    @SerialName("ipApiProxy") val ipApiProxy: String? = null,
    @SerialName("ipApiHosting") val ipApiHosting: String? = null,
    @SerialName("ipApiMobile") val ipApiMobile: String? = null,
    @SerialName("ipApiISP") val ipApiISP: String? = null,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(ip)
        parcel.writeString(asn)
        parcel.writeString(scamalyticsScore)
        parcel.writeString(scamalyticsRisk)
        parcel.writeString(proxyCheckRisk)
        parcel.writeString(proxyCheckProxy)
        parcel.writeString(proxyCheckType)
        parcel.writeString(ipApiProxy)
        parcel.writeString(ipApiHosting)
        parcel.writeString(ipApiMobile)
        parcel.writeString(ipApiISP)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<IPQuality> {
        override fun createFromParcel(parcel: Parcel): IPQuality {
            return IPQuality(parcel)
        }

        override fun newArray(size: Int): Array<IPQuality?> {
            return arrayOfNulls(size)
        }
    }
}
