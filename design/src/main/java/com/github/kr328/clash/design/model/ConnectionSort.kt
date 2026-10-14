package com.github.kr328.clash.design.model

/**
 * User-selectable ordering for the connections list.
 *
 * Replaces the hardcoded `.sortedByDescending { it.download }` that used to
 * run on every poll. Persisted through [com.github.kr328.clash.design.store.UiStore]
 * so the choice survives process death.
 */
enum class ConnectionSort {
    /** Total bytes moved (download + upload), biggest first. */
    Size,

    /** Oldest connection first. */
    Duration,

    /** Process/app name, then host as a tie breaker. */
    Process,

    /** Matched rule, then host as a tie breaker. */
    Rule,

    /** Host, then destination as a tie breaker. */
    Host,
}