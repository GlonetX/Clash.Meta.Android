package com.github.kr328.clash.service.backup

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

@Serializable
data class BackupConfig(
    val url: String = "",
    val directory: String = "GlonetX",
    val username: String = "",
    val hasPassword: Boolean = false,
) : Parcelable {
    override fun describeContents() = 0
    override fun writeToParcel(parcel: Parcel, flags: Int) = Parcelizer.encodeToParcel(serializer(), parcel, this)
    companion object CREATOR : Parcelable.Creator<BackupConfig> {
        override fun createFromParcel(parcel: Parcel) = Parcelizer.decodeFromParcel(serializer(), parcel)
        override fun newArray(size: Int): Array<BackupConfig?> = arrayOfNulls(size)
    }
}

@Serializable
data class BackupRemoteFile(val name: String, val size: Long = -1, val modified: String = "") : Parcelable {
    override fun describeContents() = 0
    override fun writeToParcel(parcel: Parcel, flags: Int) = Parcelizer.encodeToParcel(serializer(), parcel, this)
    companion object CREATOR : Parcelable.Creator<BackupRemoteFile> {
        override fun createFromParcel(parcel: Parcel) = Parcelizer.decodeFromParcel(serializer(), parcel)
        override fun newArray(size: Int): Array<BackupRemoteFile?> = arrayOfNulls(size)
    }
}

@Serializable
data class BackupJob(
    val id: String,
    val operation: String,
    val phase: String = "Queued",
    val progress: Int = 0,
    val message: String = "",
    val remoteName: String = "",
    val importedCount: Int = 0,
    val pendingCount: Int = 0,
    val legacy: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) : Parcelable {
    val terminal: Boolean get() = phase in setOf("Completed", "Failed", "Cancelled")
    override fun describeContents() = 0
    override fun writeToParcel(parcel: Parcel, flags: Int) = Parcelizer.encodeToParcel(serializer(), parcel, this)
    companion object CREATOR : Parcelable.Creator<BackupJob> {
        override fun createFromParcel(parcel: Parcel) = Parcelizer.decodeFromParcel(serializer(), parcel)
        override fun newArray(size: Int): Array<BackupJob?> = arrayOfNulls(size)
    }
}

internal object BackupLimits {
    const val TRANSFER = 256L * 1024 * 1024
    const val EXPANDED = 512L * 1024 * 1024
    const val FILE = 64L * 1024 * 1024
    const val METADATA = 8L * 1024 * 1024
    const val ENTRIES = 4096
}
