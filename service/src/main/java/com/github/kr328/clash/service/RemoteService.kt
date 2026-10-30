package com.github.kr328.clash.service

import android.content.Intent
import android.os.IBinder
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.remote.IRemoteService
import com.github.kr328.clash.service.remote.IProfileManager
import com.github.kr328.clash.service.remote.wrap
import com.github.kr328.clash.service.util.cancelAndJoinBlocking

class RemoteService : BaseService(), IRemoteService {
    private val binder = this.wrap()

    private var clash: ClashManager? = null
    private var profile: ProfileManager? = null
    private var clashBinder: IClashManager? = null
    private var profileBinder: IProfileManager? = null

    override fun onCreate() {
        super.onCreate()

        clash = ClashManager(this)
        profile = ProfileManager(this)
        clashBinder = clash?.wrap() as IClashManager?
        profileBinder = profile?.wrap() as IProfileManager?
    }

    override fun onDestroy() {
        super.onDestroy()

        clash?.cancelAndJoinBlocking()
        profile?.cancelAndJoinBlocking()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun clash(): IClashManager {
        return clashBinder!!
    }

    override fun settings(): com.github.kr328.clash.service.remote.ISettingsManager =
        com.github.kr328.clash.service.settings.SettingsManager.get(this).wrap() as com.github.kr328.clash.service.remote.ISettingsManager

    override fun backup(): com.github.kr328.clash.service.remote.IBackupManager =
        com.github.kr328.clash.service.backup.BackupManager.get(this).wrap() as com.github.kr328.clash.service.remote.IBackupManager

    override fun profile(): IProfileManager {
        return profileBinder!!
    }
}