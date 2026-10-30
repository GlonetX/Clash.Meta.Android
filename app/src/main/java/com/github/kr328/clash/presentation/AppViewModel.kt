package com.github.kr328.clash.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.data.ClientRepository
import com.github.kr328.clash.data.SettingsRepository
import com.github.kr328.clash.service.settings.*
import com.github.kr328.clash.core.model.*
import com.github.kr328.clash.core.util.trafficDownloadValue
import com.github.kr328.clash.core.util.trafficUploadValue
import com.github.kr328.clash.remote.Broadcasts
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class TrafficPoint(val upload: Long, val download: Long, val sampledAt: Long = android.os.SystemClock.elapsedRealtime())
data class HomeState(
    val connected: Boolean = false, val loading: Boolean = true, val error: String? = null,
    val active: Profile? = null, val mode: TunnelState.Mode = TunnelState.Mode.Rule,
    val upload: Long = 0, val download: Long = 0, val totalUpload: Long = 0, val totalDownload: Long = 0,
    val memory: Long = 0, val connections: Int = 0, val startedAt: Long = 0,
    val history: List<TrafficPoint> = emptyList(), val ip: ExternalIP? = null,
    val providerCount: Int = 0,
)

class AppViewModel(application: Application) : AndroidViewModel(application), Broadcasts.Observer {
    val repository = ClientRepository(application)
    val settingsRepository = SettingsRepository()
    private val _settings = MutableStateFlow<SettingsSnapshot?>(null)
    val settings = _settings.asStateFlow()
    private var settingsJob: Job? = null
    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages = _messages.asSharedFlow()
    private var polling: Job? = null
    private var refreshJob: Job? = null
    @Volatile private var homeVisible = true
    private var historyIntervalMs = 0L
    init { Remote.broadcasts.addObserver(this) }
    fun setVisible(visible: Boolean) {
        polling?.cancel()
        settingsJob?.cancel()
        if(visible) settingsJob = viewModelScope.launch { settingsRepository.observe().collect { snapshot ->
            _settings.value = snapshot
        } }
        polling = if(visible) viewModelScope.launch {
            refresh()
            while(isActive) {
                val interval = if (_settings.value?.ui?.lightweightMode == true) 5_000L else 1_000L
                delay(if (homeVisible) interval else 1_000L)
                if (homeVisible) tick(interval)
            }
        } else null
    }
    fun setHomeVisible(visible: Boolean) { homeVisible = visible }
    fun updateUi(transform: (UiPreferences) -> UiPreferences) = launchAction {
        val snapshot = settingsRepository.current()
        accept(settingsRepository.update(snapshot, SettingsUpdate(ui = transform(snapshot.ui))))
    }
    fun updateRuntime(transform: (RuntimeSettings) -> RuntimeSettings) = launchAction {
        val snapshot = settingsRepository.current()
        accept(settingsRepository.update(snapshot, SettingsUpdate(runtime = transform(snapshot.desired))))
    }
    private fun accept(result: SettingsUpdateResult) {
        _settings.value = result.snapshot
        if(result.status in listOf(UpdateStatus.Invalid, UpdateStatus.Conflict, UpdateStatus.Failed)) message(result.error ?: result.status.name)
    }
    fun message(value: String) { _messages.tryEmit(value) }
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val active = repository.activeProfile()
                val running = Remote.broadcasts.clashRunning
                val mode = repository.tunnel().mode
                val providers = if(running) repository.providers().size else 0
                _home.update { it.copy(active = active, connected = running, mode = mode, providerCount = providers,
                    loading = false, error = null, startedAt = if(running) it.startedAt.takeIf { t -> t > 0 } ?: android.os.SystemClock.elapsedRealtime() else 0) }
            } catch(e: CancellationException) { throw e } catch(e: Exception) {
                _home.update { it.copy(loading = false, error = e.message ?: "Service unavailable") }
            }
        }
    }
    private suspend fun tick(interval: Long) {
        if(!_home.value.connected) return
        try {
            val traffic = repository.traffic(); val total = repository.totals()
            val memory = repository.memory(); val connections = repository.connectionStats().total
            val resetHistory = historyIntervalMs != interval
            historyIntervalMs = interval
            val now = android.os.SystemClock.elapsedRealtime()
            _home.update { it.copy(upload = traffic.trafficUploadValue(), download = traffic.trafficDownloadValue(),
                totalUpload = total.trafficUploadValue(), totalDownload = total.trafficDownloadValue(), memory = memory, connections = connections,
                history = ((if (resetHistory) emptyList() else it.history.filter { point -> now - point.sampledAt < if (interval == 5_000L) 300_000L else 900_000L }) + TrafficPoint(traffic.trafficUploadValue(), traffic.trafficDownloadValue(), now)).takeLast(if (interval == 5_000L) 60 else 900), error = null) }
        } catch(e: CancellationException) { throw e } catch(e: Exception) { _home.update { it.copy(error = e.message) } }
    }
    fun setMode(mode: TunnelState.Mode) = launchAction { repository.mode(mode); refresh() }
    fun refreshIp() = launchAction { val ip = repository.ip(); _home.update { it.copy(ip = ip) } }
    fun launchAction(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) { message(e.message ?: "Operation failed") }
    }
    override fun onServiceRecreated() { _home.update { it.copy(connected = false) }; refresh() }
    override fun onStarted() { refresh() }
    override fun onStopped(cause: String?) { _home.update { it.copy(connected = false, startedAt = 0, upload = 0, download = 0) }; cause?.let(::message); refresh() }
    override fun onProfileChanged() { refresh() }
    override fun onProfileUpdateCompleted(uuid: UUID?) { refresh() }
    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) { reason?.let(::message) }
    override fun onProfileLoaded() { refresh() }
    override fun onCleared() { Remote.broadcasts.removeObserver(this); super.onCleared() }
}
