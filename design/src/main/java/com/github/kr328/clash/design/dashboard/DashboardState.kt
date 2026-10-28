package com.github.kr328.clash.design.dashboard

import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.model.ServiceAvailability

/**
 * One immutable snapshot of every value the dashboard can show.
 *
 * The dashboard used to be driven by fourteen `MainDesign.setXxx()` suspend
 * functions, each assigning a single DataBinding variable. That does not
 * survive view recycling: a recycled ViewHolder gets whichever value was
 * assigned last, not the one for its position. Pushing one immutable value
 * per tick — and letting the adapter read exactly the fields it needs — fixes
 * that by construction.
 *
 * Every field is a primitive or an immutable value, so `==` is meaningful and
 * `DiffUtil` can tell "changed" from "unchanged".
 */
data class DashboardState(
    /** Is the core running? Gates most widgets. */
    val clashRunning: Boolean = false,

    /**
     * The user's last toggle intent: true from the moment the start request
     * is issued until the service actually reports running (or the request
     * fails). Drives the hero card's `starting` state — the cross-process
     * startup gap otherwise reads as "still stopped".
     */
    val coreStarted: Boolean = false,

    /** Human readable total traffic forwarded since the core started. */
    val forwarded: String = "",

    /** Name of the active profile. */
    val profileName: String? = null,

    /** Trailing detail under the profile name. */
    val profileSummary: String? = null,

    /** Are there providers to show? Gates the Providers entry link. */
    val hasProviders: Boolean = false,

    /** The providers themselves, for the dashboard providers card. */
    val providers: List<com.github.kr328.clash.core.model.Provider> = emptyList(),

    /** Current outbound mode. */
    val mode: TunnelState.Mode = TunnelState.Mode.Rule,

    /** Formatted upload rate, e.g. `12.34 KiB/s`. */
    val speedUp: String = "",

    /** Formatted download rate, e.g. `98.70 KiB/s`. */
    val speedDown: String = "",

    /** Raw memory footprint in bytes, from `queryMemory()`. */
    val memoryBytes: Long = 0,

    /** Raw bytes uploaded since the core started. */
    val trafficTotalUp: Long = 0,

    /** Raw bytes downloaded since the core started. */
    val trafficTotalDown: Long = 0,

    /** Transient IP lookup status, or null when the card shows the result. */
    val ipStatus: String? = null,

    val exitIP: String? = null,
    val exitIPDisplay: String? = null,
    val exitIPISP: String? = null,
    val directIP: String? = null,
    val directIPDisplay: String? = null,

    /** `QueryConnections().size`. */
    val connectionCount: Int = 0,

    /** Rows whose metadata type looks like an HTTP-ish request. */
    val requestCount: Int = 0,

    /** Rows whose metadata type is `DNS`. */
    val dnsCount: Int = 0,

    /** Milliseconds since the core service started, or 0 when unknown. */
    val uptimeMillis: Long = 0,

    /** Streaming service probe results, one entry per probed service. */
    val services: List<ServiceProbe> = emptyList(),
) {
    /**
     * True when the providers card has anything to show.
     *
     * Inline providers are filtered out by the card, so an all-inline list
     * still renders as an empty card. The dashboard drops the widget
     * entirely instead of leaving a blank cell behind.
     */
    val hasRemoteProviders: Boolean
        get() = providers.any {
            it.vehicleType != com.github.kr328.clash.core.model.Provider.VehicleType.Inline
        }
}

/**
 * An immutable result of one service probe.
 *
 * Deliberately not [com.github.kr328.clash.design.model.ServiceStatus]: that
 * class is mutable and is owned by the latency screen, and a mutable element
 * inside [DashboardState] would break `DiffUtil` equality.
 */
data class ServiceProbe(
    val name: String,
    val availability: ServiceAvailability,
    val latencyMillis: Long = -1,
)