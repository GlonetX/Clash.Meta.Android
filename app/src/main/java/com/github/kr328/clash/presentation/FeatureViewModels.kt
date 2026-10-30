package com.github.kr328.clash.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.data.ClientRepository
import com.github.kr328.clash.core.model.*
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Each navigation entry owns its own instance; no Activity or View reference survives here. */
class FeatureViewModel(application: Application, val saved: SavedStateHandle) : AndroidViewModel(application) {
    val repository = ClientRepository(application)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val profiles = MutableStateFlow<List<Profile>>(emptyList())
    val providers = MutableStateFlow<List<Provider>>(emptyList())
    val groups = MutableStateFlow<List<String>>(emptyList())
    val group = MutableStateFlow<ProxyGroup?>(null)
    val connections = MutableStateFlow<List<ConnectionItem>>(emptyList())
    val profile = MutableStateFlow<Profile?>(null)
    val committed = MutableStateFlow(false)
    val progress = MutableStateFlow<String?>(null)
    val files = MutableStateFlow<List<com.github.kr328.clash.design.model.File>>(emptyList())
    val ip = MutableStateFlow<ExternalIP?>(null)
    val directIp = MutableStateFlow<ExternalIP?>(null)
    val quality = MutableStateFlow<IPQuality?>(null)
    val probes = MutableStateFlow<Map<String, ProbeResult?>>(emptyMap())
    val probeRunning = MutableStateFlow(false)
    var loadedProfile: Profile? = null
        private set
    fun action(block: suspend () -> Unit): Job = viewModelScope.launch {
        busy.value=true; error.value=null
        try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) { error.value=e.message ?: "Operation failed" }
        finally { busy.value=false }
    }
    fun loadProfiles() = action { profiles.value=repository.profiles() }
    fun loadProviders() = action { providers.value=repository.providers() }
    fun loadGroups(exclude: Boolean) = action { groups.value=repository.groups(exclude) }
    fun loadGroup(name: String, sort: ProxySort) = action { group.value=repository.group(name, sort) }
    fun loadConnections() = action { connections.value=repository.connections() }
    fun loadProfile(id: String) = action {
        if(loadedProfile?.uuid?.toString()==id) return@action
        val original=repository.profile(id) ?: error("Profile no longer exists")
        loadedProfile=original
        profile.value=original.copy(name=saved["profile.name"] ?: original.name, source=saved["profile.source"] ?: original.source, interval=saved["profile.interval"] ?: original.interval)
    }
    fun editProfile(transform: (Profile) -> Profile) {
        val updated=profile.value?.let(transform) ?: return
        profile.value=updated
        saved["profile.name"]=updated.name; saved["profile.source"]=updated.source; saved["profile.interval"]=updated.interval
    }
    fun saveProfile() = action {
        val p=profile.value ?: return@action
        require(p.name.isNotBlank()) { "Name is required" }
        require(p.type==Profile.Type.File || p.source.isNotBlank()) { "URL is required" }
        repository.save(p) { progress.value=it.toString() }
        committed.value=true; progress.value=null
    }
    fun discardProfile(onDone: () -> Unit) = action { profile.value?.let { repository.release(it.uuid.toString()) }; onDone() }
    fun loadFiles(parent: String) = action { files.value=repository.files(parent) }
    fun loadQuality(address: String = "") = action {
        ip.value=repository.ip(); directIp.value=repository.ip("DIRECT")
        val target=address.ifBlank { ip.value?.ip.orEmpty() }
        require(target.isNotBlank()) { "Unable to resolve exit IP" }
        quality.value=repository.quality(target)
    }
    fun probe(services: List<Pair<String,String>>) = action {
        probeRunning.value=true; probes.value=emptyMap()
        try { coroutineScope { val gate=kotlinx.coroutines.sync.Semaphore(3)
            services.map { (name,url) -> async { gate.acquire(); try { val result=repository.probe(url); probes.update { it+(name to result) } } finally { gate.release() } } }.awaitAll()
        } } finally { probeRunning.value=false }
    }
}
