package com.github.kr328.clash.data

import android.content.Context
import android.net.Uri
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.*
import com.github.kr328.clash.remote.FilesClient
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** UI-facing data access. All Binder and document operations stay off the UI thread. */
class ClientRepository(context: Context) {
    private val files = FilesClient(context.applicationContext)
    suspend fun profiles() = withProfile { queryAll() }
    suspend fun activeProfile() = withProfile { queryActive() }
    suspend fun profile(id: String) = withProfile { queryByUUID(UUID.fromString(id)) }
    suspend fun create(type: Profile.Type, name: String, source: String = "") = withProfile { create(type, name, source) }.toString()
    suspend fun clone(profile: Profile) = withProfile { clone(profile.uuid) }.toString()
    suspend fun delete(profile: Profile) = withProfile { delete(profile.uuid) }
    suspend fun update(profile: Profile) = withProfile { update(profile.uuid) }
    suspend fun activate(profile: Profile) = withProfile { setActive(profile) }
    suspend fun release(id: String) = withProfile { release(UUID.fromString(id)) }
    suspend fun save(profile: Profile, onProgress: (FetchStatus) -> Unit) = withProfile {
        patch(profile.uuid, profile.name, profile.source, profile.interval, profile.ageSecretKey)
        commit(profile.uuid, object: IFetchObserver { override fun updateStatus(status: FetchStatus) { onProgress(status) } })
    }
    suspend fun tunnel() = withClash { queryTunnelState() }
    suspend fun traffic() = withClash { queryTrafficNow() }
    suspend fun totals() = withClash { queryTrafficTotal() }
    suspend fun memory() = withClash { queryMemory() }
    suspend fun connectionStats() = withClash { queryConnectionStats() }
    suspend fun groups(exclude: Boolean = false) = withClash { queryProxyGroupNames(exclude) }
    suspend fun group(name: String, sort: ProxySort = ProxySort.Default) = withClash { queryProxyGroup(name, sort) }
    suspend fun select(group: String, proxy: String) = withClash { patchSelector(group, proxy) }
    suspend fun healthCheck(group: String) = withClash { healthCheck(group) }
    suspend fun providers() = withClash { queryProviders().toList() }
    suspend fun updateProvider(provider: Provider) = withClash { updateProvider(provider.type, provider.name) }
    suspend fun connections() = withClash { queryConnections().toList() }
    suspend fun closeConnection(id: String) = withClash { closeConnection(id) }
    suspend fun ip(proxy: String = "") = withClash { queryIPInfo(proxy) }
    suspend fun quality(ip: String) = withClash { queryIPQuality(ip) }
    suspend fun probe(url: String) = withClash { probeUrl(url) }
    suspend fun latency(url: String) = withClash { testLatency(url) }
    suspend fun override() = withClash { queryOverride(Clash.OverrideSlot.Persist) }
    suspend fun saveOverride(value: ConfigurationOverride) = withClash { patchOverride(Clash.OverrideSlot.Persist, value) }
    suspend fun mode(value: TunnelState.Mode) = withClash {
        val config = queryOverride(Clash.OverrideSlot.Session)
        config.mode = value
        patchOverride(Clash.OverrideSlot.Session, config)
    }
    suspend fun files(parent: String) = files.list(parent)
    suspend fun deleteFile(id: String) = files.deleteDocument(id)
    suspend fun renameFile(id: String, name: String) = files.renameDocument(id, name)
    suspend fun importFile(parent: String, uri: Uri, name: String) = files.importDocument(parent, uri, name)
    suspend fun replaceFile(id: String, uri: Uri) = files.copyDocument(id, uri)
    suspend fun exportFile(id: String, uri: Uri) = files.copyDocument(uri, id)
    fun fileUri(id: String) = files.buildDocumentUri(id)
}
