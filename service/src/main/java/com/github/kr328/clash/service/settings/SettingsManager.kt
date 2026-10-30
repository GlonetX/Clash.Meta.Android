package com.github.kr328.clash.service.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.ComponentName
import android.content.pm.PackageManager
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.service.backup.DataMaintenanceCoordinator
import com.github.kr328.clash.service.remote.ISettingsManager
import com.github.kr328.clash.service.remote.ISettingsObserver
import com.github.kr328.clash.service.security.ProfileCredentialsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.CopyOnWriteArraySet

/** The sole writer of portable settings. Construct only in the background process. */
class SettingsManager private constructor(private val context: Context) : ISettingsManager {
    private val prefs = context.getSharedPreferences("service", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val observers = CopyOnWriteArraySet<ISettingsObserver>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initial = loadOrMigrate().let { snapshot ->
        try { applyAutoRestart(snapshot.desired.autoRestart); snapshot }
        catch (e: Exception) { snapshot.copy(lastError = e.message ?: "Could not apply auto-restart setting") }
    }
    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<SettingsSnapshot> get() = mutableState
    @Volatile private var runningSettings: RuntimeSettings? = null
    @Volatile private var startingRevision: Long? = null

    fun current(): SettingsSnapshot = mutableState.value
    fun executionSettings(): RuntimeSettings = runningSettings ?: current().desired

    override suspend fun getControlToken(): String {
        DataMaintenanceCoordinator.ensureReady(context)
        return mutex.withLock {
            val current = prefs.getString("control_token", null)
            if (current != null) {
                if (ProfileCredentialsRepository.isProtected(current)) {
                    ProfileCredentialsRepository.decrypt(current, "control", "token")!!
                } else {
                    saveControlToken(current)
                    current
                }
            } else {
                val legacy = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val token = legacy.getString("external_control_token", null)?.takeIf { it.isNotBlank() }
                if (token == null) rotateControlToken() else {
                    saveControlToken(token)
                    check(legacy.edit().remove("external_control_token").commit())
                    token
                }
            }
        }
    }

    private fun saveControlToken(token: String) {
        val encrypted = ProfileCredentialsRepository.encrypt(token, "control", "token")
        check(prefs.edit().putString("control_token", encrypted).commit()) { "Could not persist control token" }
    }

    private fun rotateControlToken(): String {
        val token = java.security.SecureRandom().let { random -> ByteArray(32).also(random::nextBytes) }
            .joinToString("") { "%02x".format(it) }
        saveControlToken(token)
        return token
    }

    override suspend fun getSnapshot(): SettingsSnapshot {
        DataMaintenanceCoordinator.ensureReady(context)
        return current()
    }

    override suspend fun update(expectedRevision: Long, update: SettingsUpdate): SettingsUpdateResult =
        DataMaintenanceCoordinator.exclusive(context) {
            mutex.withLock {
                val old = current()
                if (old.revision != expectedRevision)
                    return@withLock SettingsUpdateResult(UpdateStatus.Conflict, old, "Settings changed; refresh and retry")
                val runtime = update.runtime ?: old.desired
                val ui = update.ui ?: old.ui
                try { validate(runtime, ui) } catch (e: IllegalArgumentException) {
                    return@withLock SettingsUpdateResult(UpdateStatus.Invalid, old, e.message)
                }
                if (runtime == old.desired && ui == old.ui)
                    return@withLock SettingsUpdateResult(UpdateStatus.Applied, old)
                val next = old.copy(revision = old.revision + 1, desired = runtime, ui = ui, lastError = null)
                try {
                    withContext(Dispatchers.IO) { persist(next) }
                    applyAutoRestart(runtime.autoRestart)
                    // Subscription requests use this immediately; core settings are latched at connection start.
                    Bridge.nativeSetRuntimeSettings(Json.encodeToString(runtimeForNative(runtime, downloadOnly = true)))
                    publish(withApplicationState(next))
                    SettingsUpdateResult(if (current().pendingRestartFields.isEmpty()) UpdateStatus.Applied else UpdateStatus.Saved, current())
                } catch (e: Exception) {
                    // A persistence error never advances the revision. A native error after persistence is explicit.
                    val persisted = prefs.getLong(KEY_REVISION, old.revision) == next.revision
                    if (persisted) publish(withApplicationState(next.copy(lastError = e.message ?: "Apply failed")))
                    SettingsUpdateResult(UpdateStatus.Failed, current(), e.message ?: "Could not save settings")
                }
            }
        }

    override fun addObserver(observer: ISettingsObserver) {
        observers.add(observer)
        scope.launch { runCatching { observer.onChanged(getSnapshot()) }.onFailure { observers.remove(observer) } }
    }

    override fun removeObserver(observer: ISettingsObserver) { observers.remove(observer) }

    /** Called under the maintenance lock by backup; does not acquire that lock again. */
    suspend fun exportPortableSettings(): String = mutex.withLock {
        val s = current()
        Json.encodeToString(PortableSettings(runtime = portableRuntime(s.desired), ui = portableUi(s.ui)))
    }

    suspend fun exportRecoverySettings(): String = mutex.withLock {
        val s = current()
        Json.encodeToString(RecoverySettings(s.desired, s.ui, prefs.getString("control_token", null)))
    }

    suspend fun importRecoverySettings(value: String) = mutex.withLock {
        val recovery = Json.decodeFromString<RecoverySettings>(value)
        validate(recovery.runtime, recovery.ui)
        val next = current().copy(revision = current().revision + 1, desired = recovery.runtime, ui = recovery.ui, lastError = null)
        persist(next)
        applyAutoRestart(next.desired.autoRestart)
        check(prefs.edit().apply {
            if (recovery.controlToken == null) remove("control_token") else putString("control_token", recovery.controlToken)
        }.commit())
        Bridge.nativeSetRuntimeSettings(Json.encodeToString(runtimeForNative(next.desired, downloadOnly = true)))
        publish(withApplicationState(next))
    }

    fun migrateLegacyPreferences(xmlFiles: Map<String, String>): String = LegacyPreferencesXml.toPortable(xmlFiles)

    fun validatePortableSettings(value: String) {
        val portable = Json.decodeFromString<PortableSettings>(value)
        require(portable.schemaVersion == 1) { "Unsupported settings backup version" }
        validate(portable.runtime, portable.ui)
    }

    /** Caller holds DataMaintenanceCoordinator; importing never restores credentials/device policy. */
    suspend fun importPortableSettings(value: String) = mutex.withLock {
        validatePortableSettings(value)
        val p = Json.decodeFromString<PortableSettings>(value)
        val old = current()
        val runtime = p.runtime.copy(
            autoRestart = old.desired.autoRestart,
            enableVpn = old.desired.enableVpn,
            accessControlMode = old.desired.accessControlMode,
            accessControlPackages = old.desired.accessControlPackages,
            wifiNetworkAction = old.desired.wifiNetworkAction,
            wifiSsidList = old.desired.wifiSsidList,
            stopOnNetworkLostBypassInterfaces = old.desired.stopOnNetworkLostBypassInterfaces,
            externalControlEnabled = false,
        )
        val ui = p.ui.copy(hideAppIcon = old.ui.hideAppIcon, hideFromRecents = old.ui.hideFromRecents,
            connectionsCollapsedGroups = emptySet(), proxyLastGroup = "")
        val next = old.copy(revision = old.revision + 1, desired = runtime, ui = ui, lastError = null)
        persist(next)
        rotateControlToken()
        Bridge.nativeSetRuntimeSettings(Json.encodeToString(runtimeForNative(runtime, downloadOnly = true)))
        publish(withApplicationState(next))
    }

    suspend fun beginRuntime(generation: Long): RuntimeSettings = mutex.withLock {
        val s = current()
        runningSettings = s.desired
        startingRevision = s.revision
        Bridge.nativeSetRuntimeSettings(Json.encodeToString(runtimeForNative(s.desired)))
        // A new service evaluates its Wi-Fi policy before establishing TUN.
        Bridge.nativeSetTrafficPolicy(TrafficPolicy.Normal.ordinal)
        publish(s.copy(runtimeGeneration = generation, phase = RuntimePhase.Starting,
            appliedRevision = null, pendingRestartFields = emptyList(), lastError = null,
            trafficPolicy = TrafficPolicy.Normal, policyReason = null))
        s.desired
    }

    suspend fun markRunning(generation: Long) = mutex.withLock {
        if (current().runtimeGeneration != generation) return@withLock
        publish(withApplicationState(current().copy(phase = RuntimePhase.Running, appliedRevision = startingRevision)))
    }

    fun markStopping(generation: Long) {
        scope.launch { mutex.withLock {
            if (current().runtimeGeneration == generation)
                publish(current().copy(phase = RuntimePhase.Stopping))
        } }
    }

    fun markStopped(generation: Long, error: String?) {
        scope.launch { mutex.withLock {
            if (current().runtimeGeneration != generation) return@withLock
            runningSettings = null
            startingRevision = null
            publish(current().copy(phase = if (error == null) RuntimePhase.Stopped else RuntimePhase.Failed,
                appliedRevision = null, pendingRestartFields = emptyList(), trafficPolicy = TrafficPolicy.Normal,
                policyReason = null, lastError = error))
        } }
    }

    suspend fun applyTrafficPolicy(generation: Long, policy: TrafficPolicy, reason: String?) = mutex.withLock {
        val old = current()
        if (old.runtimeGeneration != generation || old.phase !in listOf(RuntimePhase.Starting, RuntimePhase.Running)) return@withLock
        if (old.trafficPolicy != policy) withContext(Dispatchers.IO) { Bridge.nativeSetTrafficPolicy(policy.ordinal) }
        if (old.trafficPolicy != policy || old.policyReason != reason)
            publish(old.copy(trafficPolicy = policy, policyReason = reason))
    }

    private fun withApplicationState(s: SettingsSnapshot): SettingsSnapshot {
        val applied = runningSettings ?: return s.copy(appliedRevision = null, pendingRestartFields = emptyList())
        val pending = restartFields(applied, s.desired)
        return s.copy(pendingRestartFields = pending,
            appliedRevision = if (pending.isEmpty() && s.phase == RuntimePhase.Running) s.revision else s.appliedRevision)
    }

    private fun publish(snapshot: SettingsSnapshot) {
        mutableState.value = snapshot
        scope.launch {
            observers.forEach { observer ->
                runCatching { observer.onChanged(snapshot) }.onFailure { observers.remove(observer) }
            }
        }
    }

    private fun persist(snapshot: SettingsSnapshot) {
        check(prefs.edit().putString(KEY_RUNTIME, storageJson.encodeToString(snapshot.desired))
            .putString(KEY_UI, Json.encodeToString(snapshot.ui)).putLong(KEY_REVISION, snapshot.revision)
            .putInt(KEY_SCHEMA, 1).commit()) { "Settings storage write failed" }
    }

    private fun applyAutoRestart(enabled: Boolean) {
        val component = ComponentName(context.packageName, "com.github.kr328.clash.RestartReceiver")
        val target = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (context.packageManager.getComponentEnabledSetting(component) != target)
            context.packageManager.setComponentEnabledSetting(component, target, PackageManager.DONT_KILL_APP)
    }

    private fun loadOrMigrate(): SettingsSnapshot {
        if (prefs.getInt(KEY_SCHEMA, 0) == 1) {
            val encoded = prefs.getString(KEY_RUNTIME, null) ?: error("Missing runtime settings")
            var desired = json.decodeFromString<RuntimeSettings>(encoded)
            if (!json.parseToJsonElement(encoded).jsonObject.containsKey("autoRestart")) {
                val component = ComponentName(context.packageName, "com.github.kr328.clash.RestartReceiver")
                desired = desired.copy(autoRestart = context.packageManager.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            }
            return SettingsSnapshot(revision = prefs.getLong(KEY_REVISION, 1), desired = desired,
                ui = json.decodeFromString(prefs.getString(KEY_UI, null) ?: error("Missing UI preferences")))
        }
        val result = LegacySettingsMigration.read(context, prefs)
        persist(result)
        return result
    }

    companion object {
        const val KEY_RUNTIME = "runtime_settings"
        const val KEY_UI = "ui_preferences"
        const val KEY_REVISION = "settings_revision"
        const val KEY_SCHEMA = "settings_schema"
        private val json = Json { ignoreUnknownKeys = true }
        private val storageJson = Json { encodeDefaults = true }
        @Volatile private var instance: SettingsManager? = null
        fun get(context: Context): SettingsManager = instance ?: synchronized(this) {
            instance ?: SettingsManager(context.applicationContext).also { instance = it }
        }
        fun validate(r: RuntimeSettings, u: UiPreferences) {
            require(r.accessControlMode in setOf("AcceptAll", "AcceptSelected", "DenySelected")) { "Invalid application access mode" }
            require(r.tunMtu in 1280..9000) { "MTU must be 1280–9000" }
            require(r.tunStackMode.lowercase() in setOf("mips", "system", "gvisor", "mixed")) { "Unknown TUN stack" }
            require(r.stopOnNetworkLostInterval in 5..3600) { "Network timeout must be 5–3600 seconds" }
            require(r.keepAliveInterval in 5..3600 && r.keepAliveIdle in 30..86400) { "Invalid keep-alive interval" }
            require(r.logRetentionDays in 1..365 && r.logMaxSizeMb in 1..1024 && r.logRealtimeBuffer in 0..10000) { "Invalid log limits" }
            require(r.globalUserAgent.length <= 512 && r.globalUserAgent.none { it < ' ' || it == '\u007f' }) { "User-Agent must be a single printable line" }
            require(r.wifiSsidList.size <= 128 && r.wifiSsidList.all { it.isNotEmpty() && it.toByteArray().size <= 32 }) { "Invalid Wi-Fi name" }
            require(r.accessControlPackages.size <= 4096 && r.stopOnNetworkLostBypassInterfaces.size <= 128) { "Too many entries" }
            require(u.textScale in 85..130 && u.proxyLine in 1..12) { "Invalid display size" }
            require(u.darkMode in setOf("Auto", "ForceLight", "ForceDark", "ForceBlack")) { "Unknown theme" }
            require(u.appUpdateChannel in setOf("Stable", "Prerelease")) { "Unknown update channel" }
            require(u.appLanguage.length <= 35 && u.dashboardWidgetOrder.size <= 128 && u.dashboardWidgetSizes.size <= 128) { "Invalid UI settings" }
            require(u.subscriptionMetadata.size <= 4096 && u.subscriptionMetadata.values.all { it.tag.length <= 256 && it.note.length <= 4096 }) { "Invalid subscription metadata" }
        }
        private fun portableRuntime(r: RuntimeSettings) = r.copy(autoRestart = false, enableVpn = true, accessControlMode = "AcceptAll",
            accessControlPackages = emptySet(), wifiNetworkAction = WifiNetworkAction.Disabled, wifiSsidList = emptySet(),
            stopOnNetworkLostBypassInterfaces = emptySet(), externalControlEnabled = false)
        private fun portableUi(u: UiPreferences) = u.copy(hideAppIcon = false, hideFromRecents = false,
            connectionsCollapsedGroups = emptySet(), proxyLastGroup = "")
        private fun restartFields(a: RuntimeSettings, b: RuntimeSettings): List<String> = buildList {
            if (a.enableVpn != b.enableVpn) add("enableVpn")
            if (a.ipv6Mode != b.ipv6Mode) add("ipv6Mode")
            if (a.allowBypass != b.allowBypass) add("allowBypass")
            if (a.bypassPrivateNetwork != b.bypassPrivateNetwork) add("bypassPrivateNetwork")
            if (a.accessControlMode != b.accessControlMode || a.accessControlPackages != b.accessControlPackages) add("accessControl")
            if (a.dnsHijacking != b.dnsHijacking) add("dnsHijacking")
            if (a.systemProxy != b.systemProxy) add("systemProxy")
            if (a.tunStackMode != b.tunStackMode) add("tunStackMode")
            if (a.tunMtu != b.tunMtu) add("tunMtu")
            if (a.keepAliveEnabled != b.keepAliveEnabled || a.keepAliveInterval != b.keepAliveInterval || a.keepAliveIdle != b.keepAliveIdle) add("keepAlive")
            if (a.storeFakeIp != b.storeFakeIp) add("storeFakeIp")
        }
        private fun runtimeForNative(r: RuntimeSettings, downloadOnly: Boolean = false): NativeRuntimeSettings =
            NativeRuntimeSettings(r.keepAliveEnabled, r.keepAliveInterval, r.keepAliveIdle, r.storeFakeIp, r.globalUserAgent, downloadOnly)
    }
}

@kotlinx.serialization.Serializable
private data class NativeRuntimeSettings(
    val keepAliveEnabled: Boolean, val keepAliveInterval: Int, val keepAliveIdle: Int,
    val storeFakeIp: Boolean, val globalUserAgent: String, val downloadOnly: Boolean = false,
)

@kotlinx.serialization.Serializable
private data class RecoverySettings(val runtime: RuntimeSettings, val ui: UiPreferences, val controlToken: String?)
