package com.github.kr328.clash.service.remote

import com.github.kr328.clash.service.settings.*
import com.github.kr328.kaidl.BinderInterface

@BinderInterface
interface ISettingsManager {
    suspend fun getControlToken(): String
    suspend fun getSnapshot(): SettingsSnapshot
    suspend fun update(expectedRevision: Long, update: SettingsUpdate): SettingsUpdateResult
    fun addObserver(observer: ISettingsObserver)
    fun removeObserver(observer: ISettingsObserver)
}
