package com.github.kr328.clash.service.clash.module

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficUpload
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.settings.SettingsManager
import com.github.kr328.clash.service.settings.NotificationStyle
import com.github.kr328.clash.service.settings.TrafficPolicy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit

class DynamicNotificationModule(service: Service) : Module<Unit>(service) {
    private val builder = NotificationCompat.Builder(service, StaticNotificationModule.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_logo_service)
        .setOngoing(true)
        .setColor(service.getColorCompat(R.color.color_clash))
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setContentTitle(service.getString(R.string.loading))
        .addAction(0, service.getString(R.string.network_policy_stop), PendingIntent.getBroadcast(
            service, R.id.nf_clash_status, Intent(Intents.ACTION_CLASH_REQUEST_STOP).setPackage(service.packageName),
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)))
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(
            PendingIntent.getActivity(
                service,
                R.id.nf_clash_status,
                Intent().setComponent(Components.MAIN_ACTIVITY)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
            )
        )

    private val settingsManager = SettingsManager.get(service)
    private val notificationManager = NotificationManagerCompat.from(service)

    /** Text of the last posted notification, used to skip no-op updates. */
    private var lastSignature: String? = null

    private fun update() {
        val state = settingsManager.current()
        val settings = state.desired
        val title = StatusProvider.currentProfile ?: service.getString(R.string.loading)
        val dynamic = settings.dynamicNotification && settings.notificationStyle != NotificationStyle.Minimal
        val now = if (dynamic) Clash.queryTrafficNow() else 0L
        val total = if (dynamic && settings.notificationStyle == NotificationStyle.Default) Clash.queryTrafficTotal() else 0L
        val text = when (state.trafficPolicy) {
            TrafficPolicy.Paused -> service.getString(R.string.network_policy_paused)
            TrafficPolicy.Direct -> service.getString(R.string.network_policy_direct)
            else -> if (dynamic) service.getString(R.string.clash_notification_content,
                "${now.trafficUpload()}/s", "${now.trafficDownload()}/s") else service.getString(R.string.running)
        }
        val subText = if (dynamic && settings.notificationStyle == NotificationStyle.Default)
            service.getString(R.string.clash_notification_content, total.trafficUpload(), total.trafficDownload()) else null
        val signature = "$title|$text|$subText|${settings.notificationStyle}"
        if (signature == lastSignature) return
        notificationManager.notify(R.id.nf_clash_status,
            builder.setContentTitle(title).setContentText(text).setSubText(subText).build())
        lastSignature = signature
    }

    override suspend fun run() = coroutineScope {
        var shouldUpdate = service.getSystemService<PowerManager>()?.isInteractive ?: true

        val screenToggle = receiveBroadcast(false, Channel.CONFLATED) {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        val profileLoaded = receiveBroadcast(capacity = Channel.CONFLATED) {
            addAction(Intents.ACTION_PROFILE_LOADED)
        }

        val settingsChanged = Channel<Unit>(Channel.CONFLATED)
        launch { settingsManager.state.collect { settingsChanged.trySend(Unit) } }
        val ticker = ticker(TimeUnit.SECONDS.toMillis(2))

        while (true) {
            select<Unit> {
                settingsChanged.onReceive { update() }
                screenToggle.onReceive {
                    when (it.action) {
                        Intent.ACTION_SCREEN_ON ->
                            shouldUpdate = true
                        Intent.ACTION_SCREEN_OFF ->
                            shouldUpdate = false
                    }
                }
                profileLoaded.onReceive {
                    builder.setContentTitle(StatusProvider.currentProfile ?: "Not selected")

                    // The title is part of the posted notification; force the
                    // next tick to publish it even if the traffic is unchanged.
                    lastSignature = null
                    update()
                }
                if (shouldUpdate) {
                    ticker.onReceive {
                        update()
                    }
                }
            }
        }
    }
}