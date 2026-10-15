package com.github.kr328.clash.design.dashboard

import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.github.kr328.clash.design.R

/**
 * One placeable (or fixed) block on the dashboard.
 *
 * The widget declares everything the grid and the edit sheet need to know
 * about it — title, icon, how many grid columns it occupies and where it sits
 * by default — so adding a widget means adding one enum constant and one
 * layout, not editing five places in [MainDesign].
 *
 * [key] is what [com.github.kr328.clash.design.store.UiStore.dashboardCardOrder]
 * persists; it must never change once released.
 */
enum class DashboardWidget(
    val key: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    @LayoutRes val layoutRes: Int,
    /** Columns out of [DashboardGrid.spanCount] this widget wants. */
    val span: Int,
    /** Position in the default order; lower comes first. */
    val defaultOrder: Int,
    /** Hidden while the core is stopped. */
    val requiresRunning: Boolean = true,
    /** Can the user move or hide this widget? */
    val placeable: Boolean = true,
) {
    STATUS(
        key = "status",
        titleRes = R.string.running,
        iconRes = R.drawable.ic_outline_check_circle,
        layoutRes = R.layout.adapter_dashboard_status,
        span = 2,
        defaultOrder = 0,
        requiresRunning = false,
        placeable = false,
    ),

    OUTBOUND_MODE(
        key = "outbound_mode",
        titleRes = R.string.outbound_mode,
        iconRes = R.drawable.ic_dashboard_outbound,
        layoutRes = R.layout.adapter_dashboard_outbound_mode,
        span = 2,
        defaultOrder = 10,
    ),

    TRAFFIC_CHART(
        key = "traffic_chart",
        titleRes = R.string.dashboard_network_speed,
        iconRes = R.drawable.ic_baseline_speed,
        layoutRes = R.layout.adapter_dashboard_traffic_chart,
        span = 2,
        defaultOrder = 30,
    ),

    TRAFFIC_TOTAL(
        key = "traffic_total",
        titleRes = R.string.dashboard_traffic_total,
        iconRes = R.drawable.ic_dashboard_donut,
        layoutRes = R.layout.adapter_dashboard_traffic_total,
        span = 1,
        defaultOrder = 40,
    ),

    MEMORY(
        key = "memory",
        titleRes = R.string.dashboard_memory,
        iconRes = R.drawable.ic_dashboard_memory,
        layoutRes = R.layout.adapter_dashboard_memory,
        span = 1,
        defaultOrder = 50,
    ),

    CONNECTIONS(
        key = "connections",
        titleRes = R.string.connections,
        iconRes = R.drawable.ic_dashboard_connections,
        layoutRes = R.layout.adapter_dashboard_connections,
        span = 1,
        defaultOrder = 60,
    ),

    UPTIME(
        key = "uptime",
        titleRes = R.string.dashboard_uptime,
        iconRes = R.drawable.ic_dashboard_uptime,
        layoutRes = R.layout.adapter_dashboard_uptime,
        span = 1,
        defaultOrder = 70,
    ),

    SERVICES(
        key = "services",
        titleRes = R.string.dashboard_services,
        iconRes = R.drawable.ic_dashboard_services,
        layoutRes = R.layout.adapter_dashboard_services,
        span = 2,
        defaultOrder = 80,
    ),

    PROVIDERS(
        key = "providers",
        titleRes = R.string.providers,
        iconRes = R.drawable.ic_baseline_swap_vertical_circle,
        layoutRes = R.layout.adapter_dashboard_providers,
        span = 2,
        defaultOrder = 85,
    ),

    IP_INFO(
        key = "ip_info",
        titleRes = R.string.ip_info,
        iconRes = R.drawable.ic_baseline_public,
        layoutRes = R.layout.adapter_dashboard_ip_info,
        span = 2,
        defaultOrder = 90,
    ),

    PROFILE(
        key = "profile",
        titleRes = R.string.profile,
        iconRes = R.drawable.ic_baseline_view_list,
        layoutRes = R.layout.adapter_dashboard_profile,
        span = 2,
        defaultOrder = 100,
    ),

    LINKS(
        key = "links",
        titleRes = R.string.dashboard_links,
        iconRes = R.drawable.ic_dashboard_links,
        layoutRes = R.layout.adapter_dashboard_links,
        span = 2,
        defaultOrder = 1000,
        requiresRunning = false,
        placeable = false,
    ),

    ;

    companion object {
        /** Widgets the user may reorder and hide. */
        val placeableWidgets: List<DashboardWidget> =
            values().filter { it.placeable }.sortedBy { it.defaultOrder }

        /** Fixed widgets, always in their declared position. */
        val fixedWidgets: List<DashboardWidget> =
            values().filterNot { it.placeable }.sortedBy { it.defaultOrder }

        fun byKey(key: String): DashboardWidget? = values().firstOrNull { it.key == key }
    }
}
