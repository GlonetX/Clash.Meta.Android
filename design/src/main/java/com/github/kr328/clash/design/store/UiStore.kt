package com.github.kr328.clash.design.store

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.ConnectionSort
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.model.ProxyDensity
import com.github.kr328.clash.design.model.ProxyIconStyle

class UiStore(context: Context) {
    private val store = Store(
        context
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .asStoreProvider()
    )

    var enableVpn: Boolean by store.boolean(
        key = "enable_vpn",
        defaultValue = true
    )

    var darkMode: DarkMode by store.enum(
        key = "dark_mode",
        defaultValue = DarkMode.Auto,
        values = DarkMode.values()
    )

    var hideAppIcon: Boolean by store.boolean(
        key = "hide_app_icon",
        defaultValue = context.packageManager.getComponentEnabledSetting(context.mainActivityAlias)
            .let { state ->
                state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
                        state != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            },
    )

    var hideFromRecents: Boolean by store.boolean(
        key = "hide_from_recents",
        defaultValue = false,
    )

    var proxyExcludeNotSelectable by store.boolean(
        key = "proxy_exclude_not_selectable",
        defaultValue = false,
    )

    var proxyLine: Int by store.int(
        key = "proxy_line",
        defaultValue = 2
    )

    /**
     * Whether the wide-screen 4-column default has been applied once. Guards
     * the upgrade so a user who explicitly picks another column count on a
     * >=600dp screen is never overridden by it again. See
     * [com.github.kr328.clash.design.ProxyDesign].
     */
    var proxyWideDefaultApplied by store.boolean(
        key = "proxy_wide_default_applied",
        defaultValue = false,
    )

    var proxySort: ProxySort by store.enum(
        key = "proxy_sort",
        defaultValue = ProxySort.Default,
        values = ProxySort.values()
    )

    var proxyLastGroup: String by store.string(
        key = "proxy_last_group",
        defaultValue = ""
    )

    /**
     * Proxy node card density — see [com.github.kr328.clash.design.model.ProxyDensity].
     * Orthogonal to [proxyLine], which only sets the column count.
     */
    var proxyDensity: ProxyDensity by store.enum(
        key = "proxy_density",
        defaultValue = ProxyDensity.Standard,
        values = ProxyDensity.values(),
    )

    /** Leading glyph drawn on a node card — see [com.github.kr328.clash.design.model.ProxyIconStyle]. */
    var proxyIconStyle: ProxyIconStyle by store.enum(
        key = "proxy_icon_style",
        defaultValue = ProxyIconStyle.None,
        values = ProxyIconStyle.values(),
    )

    /** Connections page ordering. */
    var connectionsSort: ConnectionSort by store.enum(
        key = "connections_sort",
        defaultValue = ConnectionSort.Size,
        values = ConnectionSort.values(),
    )

    /** Flips [connectionsSort] on every poll. */
    var connectionsSortReversed: Boolean by store.boolean(
        key = "connections_sort_reversed",
        defaultValue = false,
    )

    /** Collapses the connections list into one section per process. */
    var connectionsGroupByProcess: Boolean by store.boolean(
        key = "connections_group_by_process",
        defaultValue = false,
    )

    /** Keys of the collapsed process groups, see [connectionsGroupByProcess]. */
    var connectionsCollapsedGroups: Set<String> by store.stringSet(
        key = "connections_collapsed_groups",
        defaultValue = emptySet(),
    )

    /**
     * Dashboard card order: comma-separated card ids. Unknown ids are
     * ignored; missing ids are appended in default order at the end.
     */
    var dashboardCardOrder: String by store.string(
        key = "dashboard_card_order",
        defaultValue = ""
    )

    /** Hidden dashboard card ids. */
    var dashboardCardHidden: Set<String> by store.stringSet(
        key = "dashboard_card_hidden",
        defaultValue = emptySet()
    )

    var accessControlSort: AppInfoSort by store.enum(
        key = "access_control_sort",
        defaultValue = AppInfoSort.Label,
        values = AppInfoSort.values(),
    )

    var accessControlReverse: Boolean by store.boolean(
        key = "access_control_reverse",
        defaultValue = false
    )

    var accessControlSystemApp: Boolean by store.boolean(
        key = "access_control_system_app",
        defaultValue = false,
    )

    companion object {
        private const val PREFERENCE_NAME = "ui"

        val Context.mainActivityAlias: ComponentName
            get() = ComponentName(this, "com.github.kr328.clash.MainActivityAlias")
    }
}