package com.github.kr328.clash.design.model

/**
 * One entry in the connections list.
 *
 * The list is either a flat sequence of [Item]s or a sequence of [Group]
 * headers each followed by the [Item]s that belong to them. A collapsed group
 * keeps its [Item]s in [ConnectionRow.Item.collapsed] state instead of dropping
 * them, so expanding never needs another core round trip.
 */
sealed class ConnectionRow {
    /** Stable identity, used for diffing and for the persisted collapse set. */
    abstract val key: String

    data class Item(
        val item: ConnectionItemDisplay,
        val collapsed: Boolean = false,
    ) : ConnectionRow() {
        override val key: String
            get() = item.id
    }

    data class Group(
        /** Process name, or the reserved key for unattributed connections. */
        override val key: String,
        val title: String,
        val count: Int,
        /** `count` rendered with the right plural form, resolved by the caller. */
        val countText: String,
        /** Aggregate `↓ up` traffic for the whole group. */
        val traffic: String,
        /** Aggregate throughput for the whole group, already suffixed with `/s`. */
        val speed: String,
        val collapsed: Boolean,
    ) : ConnectionRow()

    companion object {
        /** Group key used when the core reported no process for a connection. */
        const val UNKNOWN_PROCESS = ""
    }
}