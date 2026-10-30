package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.content.Context
import android.net.*
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.getSystemService
import com.github.kr328.clash.service.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.selects.select
import java.util.concurrent.ConcurrentHashMap

/** Network automation owns a temporary policy, never the user's saved core mode. */
class NetworkPolicyModule(service: Service, private val generation: Long) : Module<String>(service) {
    val firstEvaluation = CompletableDeferred<Unit>()
    private val manager = SettingsManager.get(service)
    private val connectivity = service.getSystemService<ConnectivityManager>()!!
    private val capabilities = ConcurrentHashMap<Network, NetworkCapabilities>()
    private val events = Channel<Unit>(Channel.CONFLATED)
    private fun changed(network: Network, value: NetworkCapabilities?) {
        if (value == null) capabilities.remove(network) else capabilities[network] = value
        events.trySend(Unit)
    }
    private val callback: ConnectivityManager.NetworkCallback = if (Build.VERSION.SDK_INT >= 31) {
        object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
            override fun onAvailable(network: Network) { events.trySend(Unit) }
            override fun onLost(network: Network) { changed(network, null) }
            override fun onCapabilitiesChanged(network: Network, value: NetworkCapabilities) { changed(network, value) }
            override fun onLinkPropertiesChanged(network: Network, value: LinkProperties) { events.trySend(Unit) }
        }
    } else {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { events.trySend(Unit) }
            override fun onLost(network: Network) { changed(network, null) }
            override fun onCapabilitiesChanged(network: Network, value: NetworkCapabilities) { changed(network, value) }
            override fun onLinkPropertiesChanged(network: Network, value: LinkProperties) { events.trySend(Unit) }
        }
    }

    override suspend fun run() = coroutineScope {
        var registration = false
        var settingsJob: Job? = null
        try {
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivity.registerNetworkCallback(request, callback)
            registration = true
            settingsJob = launch { manager.state.collect { events.trySend(Unit) } }
            var missingSince: Long? = null
            var lastAction = WifiNetworkAction.Disabled
            // Initial evaluation precedes TUN establishment; following changes debounce bursts.
            while (isActive) {
                val settings = manager.current().desired
                val networks = connectivity.allNetworks.mapNotNull { network ->
                    val caps = capabilities[network] ?: connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) null
                    else network to caps
                }
                val selected = networks.minByOrNull { (_, caps) -> when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 3
                    else -> 2
                } }
                val isWifi = selected?.second?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val ssid = if (isWifi) readSsid(selected!!.second) else null
                val current = manager.current()
                val matched = ssid != null && ssid in settings.wifiSsidList
                val policy = when {
                    settings.wifiNetworkAction == WifiNetworkAction.Disabled || !isWifi -> TrafficPolicy.Normal
                    matched && settings.wifiNetworkAction == WifiNetworkAction.Pause -> TrafficPolicy.Paused
                    matched && settings.wifiNetworkAction == WifiNetworkAction.Direct -> TrafficPolicy.Direct
                    ssid == null && settings.wifiNetworkAction == WifiNetworkAction.Pause &&
                        current.trafficPolicy == TrafficPolicy.Paused && lastAction == WifiNetworkAction.Pause -> TrafficPolicy.Paused
                    else -> TrafficPolicy.Normal
                }
                val reason = when {
                    isWifi && ssid == null && settings.wifiNetworkAction != WifiNetworkAction.Disabled -> "wifi_identity_unavailable"
                    policy == TrafficPolicy.Paused -> "wifi_pause"
                    policy == TrafficPolicy.Direct -> "wifi_direct"
                    else -> null
                }
                manager.applyTrafficPolicy(generation, policy, reason)
                lastAction = settings.wifiNetworkAction
                if (!firstEvaluation.isCompleted) firstEvaluation.complete(Unit)

                val exempt = networks.any { (network, _) ->
                    connectivity.getLinkProperties(network)?.interfaceName in settings.stopOnNetworkLostBypassInterfaces
                }
                if (settings.stopOnNetworkLost && networks.isEmpty() && !exempt) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (missingSince == null) missingSince = now
                    if (now - missingSince!! >= settings.stopOnNetworkLostInterval * 1000L) {
                        enqueueEvent("network_lost")
                        break
                    }
                } else missingSince = null
                withTimeoutOrNull(1000L) { events.receive() }
                if (events.tryReceive().isSuccess) delay(150L)
            }
            settingsJob?.cancel()
        } catch (e: CancellationException) {
            if (!firstEvaluation.isCompleted) firstEvaluation.cancel(e)
            throw e
        } catch (e: Exception) {
            if (!firstEvaluation.isCompleted) firstEvaluation.completeExceptionally(e)
            enqueueEvent(e.message ?: "Network policy failed")
        } finally {
            settingsJob?.cancel()
            if (registration) runCatching { connectivity.unregisterNetworkCallback(callback) }
        }
        Unit
    }

    private fun readSsid(caps: NetworkCapabilities): String? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 29) caps.transportInfo as? WifiInfo else null
        @Suppress("DEPRECATION")
        val value = info?.ssid ?: (service.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo?.ssid
        value?.removeSurrounding("\"")?.takeUnless { it.isBlank() || it == WifiManager.UNKNOWN_SSID || it == "<unknown ssid>" }
    }.getOrNull()
}
