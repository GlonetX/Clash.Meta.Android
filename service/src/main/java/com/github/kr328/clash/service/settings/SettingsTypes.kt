package com.github.kr328.clash.service.settings

import kotlinx.serialization.Serializable

@Serializable enum class Ipv6Mode { Block, Proxy, Direct }
@Serializable enum class NotificationStyle { Default, Compact, Minimal }
@Serializable enum class WifiNetworkAction { Disabled, Pause, Direct }
@Serializable enum class RuntimePhase { Stopped, Starting, Running, Stopping, Failed }
@Serializable enum class TrafficPolicy { Normal, Paused, Direct }
@Serializable enum class UpdateStatus { Applied, Saved, Conflict, Invalid, Failed }
@Serializable data class SubscriptionAnnotation(val tag: String = "", val note: String = "")
@Serializable data class PortableSettings(val schemaVersion: Int = 1, val runtime: RuntimeSettings, val ui: UiPreferences)
