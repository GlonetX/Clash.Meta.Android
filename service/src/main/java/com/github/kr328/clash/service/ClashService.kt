package com.github.kr328.clash.service

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.clash.clashRuntime
import com.github.kr328.clash.service.clash.module.*
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.sendClashStarted
import com.github.kr328.clash.service.util.sendClashStopped
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class ClashService : BaseService() {
    private val self: ClashService
        get() = this

    private var reason: String? = null

    /** Id from [StatusProvider.claimService]; 0 until onCreate claims the core. */
    private var serviceId = 0

    /** True while this instance owns the core, i.e. it may claim "running". */
    private var ownsCore = false

    private val runtime = clashRuntime {
        val store = ServiceStore(self)

        val close = install(CloseModule(self))
        val config = install(ConfigurationModule(self))
        val network = install(NetworkObserveModule(self))

        if (store.dynamicNotification)
            install(DynamicNotificationModule(self))
        else
            install(StaticNotificationModule(self))

        install(AppListCacheModule(self))
        install(TimeZoneModule(self))
        install(SuspendModule(self))

        try {
            while (isActive) {
                val quit = select<Boolean> {
                    close.onEvent {
                        true
                    }
                    config.onEvent {
                        reason = it.message

                        true
                    }
                    network.onEvent {
                        false
                    }
                }

                if (quit) break
            }
        } catch (e: Exception) {
            Log.e("Create clash runtime: ${e.message}", e)

            reason = e.message
        } finally {
            // Drop the duplicate-instance claim *before* stopSelf() is
            // dispatched: a start arriving from here on finds no live owner
            // and takes over, instead of being queued behind this instance's
            // onDestroy.
            StatusProvider.abandonService(serviceId)

            withContext(NonCancellable) {
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Another instance is alive *and running*: this start is a duplicate
        // (boot receiver, sticky redelivery) and must not create a second
        // core. An instance that is tearing down has already dropped its
        // claim, so a stop→start takes over instead of being swallowed.
        if (StatusProvider.serviceAlive)
            return stopSelf()

        serviceId = StatusProvider.claimService()
        ownsCore = true

        StaticNotificationModule.createNotificationChannel(this)
        StaticNotificationModule.notifyLoadingNotification(this)

        runtime.launch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // onCreate bailed out above because another instance owns the core.
        // The framework may still deliver this start command, but there is no
        // runtime behind it, so claiming "running" would be a lie.
        if (ownsCore)
            sendClashStarted()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return Binder()
    }

    override fun onDestroy() {
        // A newer instance may have taken over while this one was dying
        // (stop→start inside the teardown window): its state and its
        // "started" broadcast have to survive this instance's exit.
        if (StatusProvider.releaseService(serviceId))
            sendClashStopped(reason)

        // Cancel without joining. The Go-core teardown (Clash.reset() in the
        // runtime's finally) still runs to completion on Dispatchers.Default,
        // and the next start serializes behind it on ClashRuntime.globalLock.
        // Joining here would park the :background main looper for the whole
        // teardown and queue the next service's onCreate/onStartCommand
        // behind it — the "stopped, then start freezes the UI" bug.
        cancel()

        ownsCore = false

        Log.i("ClashService destroyed: ${reason ?: "successfully\"}")

        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        runtime.requestGc()
    }
}