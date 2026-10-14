package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.common.util.createListFromParcelSlice
import com.github.kr328.clash.common.util.writeToParcelSlice

class ConnectionList(data: List<ConnectionItem>) : List<ConnectionItem> by data, Parcelable {
    constructor(parcel: Parcel) : this(ConnectionItem.createListFromParcelSlice(parcel, 0, 20))

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        return writeToParcelSlice(parcel, flags)
    }

    companion object CREATOR : Parcelable.Creator<ConnectionList> {
        override fun createFromParcel(parcel: Parcel): ConnectionList {
            return ConnectionList(parcel)
        }

        override fun newArray(size: Int): Array<ConnectionList?> {
            return arrayOfNulls(size)
        }
    }
}
