package com.github.kr328.clash.service.remote

import com.github.kr328.clash.service.settings.SettingsSnapshot
import com.github.kr328.kaidl.BinderInterface

@BinderInterface
interface ISettingsObserver {
    fun onChanged(snapshot: SettingsSnapshot)
}
