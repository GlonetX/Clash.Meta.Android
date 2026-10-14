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
    @SerialName("ipqsFraudScore") val ipqsFraudScore: String? = null,
    @SerialName("ipqsProxy") val ipqsProxy: String? = null,
    @SerialName("ipqsVpn") val ipqsVpn: String? = null,
    @SerialName("abuseConfidence") val abuseConfidence: String? = null,
    @SerialName("abuseUsageType") val abuseUsageType: String? = null,
    @SerialName("ip2locationProxy") val ip2locationProxy: String? = null,
    @SerialName("ip2locationUsage") val ip2locationUsage: String? = null,
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
        parcel.writeString(ipqsFraudScore)
        parcel.writeString(ipqsProxy)
        parcel.writeString(ipqsVpn)
        parcel.writeString(abuseConfidence)
        parcel.writeString(abuseUsageType)
        parcel.writeString(ip2locationProxy)
        parcel.writeString(ip2locationUsage)
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
