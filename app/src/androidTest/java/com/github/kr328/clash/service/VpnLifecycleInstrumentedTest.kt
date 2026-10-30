package com.github.kr328.clash.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.kr328.clash.MainActivity
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IRemoteService
import com.github.kr328.clash.service.remote.unwrap
import com.github.kr328.clash.service.settings.Ipv6Mode
import com.github.kr328.clash.service.settings.RuntimePhase
import com.github.kr328.clash.service.settings.SettingsUpdate
import com.github.kr328.clash.service.util.sendBroadcastSelf
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual service, Binder, Go core and Android VPN on a synthetic local profile. */
@RunWith(AndroidJUnit4::class)
class VpnLifecycleInstrumentedTest {
    @Test(timeout = 120000) fun syntheticProfileStartsAndStopsVpn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} ACTIVATE_VPN allow").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
        assertNull("The emulator must grant VPN consent for this isolated test", VpnService.prepare(context))
        val activity = ActivityScenario.launch(MainActivity::class.java)
        val ready = CountDownLatch(1)
        var remote: IRemoteService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                remote = binder.unwrap(IRemoteService::class)
                ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { remote = null }
        }
        var bound = false
        var id: UUID? = null
        var originalSettings: com.github.kr328.clash.service.settings.RuntimeSettings? = null
        try {
            bound = context.bindService(Intent(context, RemoteService::class.java), connection, Context.BIND_AUTO_CREATE)
            assertTrue("Service bind failed", bound)
            assertTrue("Service did not connect", ready.await(45, TimeUnit.SECONDS))
            val client = requireNotNull(remote)
            val settings = client.settings()
            val before = settings.getSnapshot()
            originalSettings = before.desired
            val configured = before.desired.copy(enableVpn = true, ipv6Mode = Ipv6Mode.Block,
                allowBypass = false, bypassPrivateNetwork = false)
            val result = settings.update(before.revision, SettingsUpdate(runtime = configured))
            assertTrue("Could not save VPN policy: ${result.error}", result.status.name in setOf("Applied", "Saved"))
            val profiles = client.profile()
            val profileId = profiles.create(Profile.Type.File, "VPN integration test")
            id = profileId
            File(context.filesDir, "pending/$profileId/config.yaml").writeText("""
                proxies:
                  - name: synthetic
                    type: ss
                    server: 127.0.0.1
                    port: 1081
                    cipher: aes-128-gcm
                    password: synthetic-test-password
                rules:
                  - MATCH,DIRECT
            """.trimIndent())
            profiles.commit(profileId)
            profiles.setActive(requireNotNull(profiles.queryByUUID(profileId)))
            val intent = Intent(context, TunService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            withTimeout(60_000) {
                while (true) {
                    val snapshot = settings.getSnapshot()
                    if (snapshot.phase == RuntimePhase.Running) break
                    if (snapshot.phase == RuntimePhase.Failed) error("VPN failed to start: ${snapshot.lastError}")
                    delay(250)
                }
            }
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            withTimeout(15_000) {
                while (!connectivity.allNetworks.any {
                    connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }) delay(250)
            }
            context.sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_STOP))
            withTimeout(30_000) {
                while (settings.getSnapshot().phase != RuntimePhase.Stopped) delay(250)
            }
        } finally {
            context.sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_STOP))
            val client = remote
            if (client != null) {
                id?.let { runCatching { client.profile().delete(it) } }
                originalSettings?.let { original -> runCatching {
                    val current = client.settings().getSnapshot()
                    client.settings().update(current.revision, SettingsUpdate(runtime = original))
                } }
            }
            if (bound) context.unbindService(connection)
            activity.close()
        }
    }
}
