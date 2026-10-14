package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.dashboard.DashboardAdapter
import com.github.kr328.clash.design.dashboard.DashboardState
import com.github.kr328.clash.design.dashboard.DashboardWidget
import com.github.kr328.clash.design.dashboard.TrafficHistory
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context, private val uiStore: UiStore) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        OpenProxy,
        OpenProfiles,
        OpenProviders,
        OpenLogs,
        OpenSettings,
        OpenHelp,
        OpenAbout,
        RefreshIP,
        RefreshServices,
        PatchMode,
        OpenIPQuality,
        OpenLatency,
        OpenConnections,
        EditCards,
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    /**
     * Sample buffer for the network speed chart.
     *
     * It lives here rather than inside the chart View so scrolling the grid
     * cannot throw the series away.
     */
    private val trafficHistory = TrafficHistory()

    private val dashboardAdapter = DashboardAdapter(
        context = context,
        history = trafficHistory,
        onRequest = { request(it) },
        onSelectMode = { requestModePatch(it) },
    )

    private val gridLayoutManager = GridLayoutManager(context, spanCount())

    // embedded tab pages, index 0 = proxy page, index 1 = settings page
    private val pages = arrayOfNulls<View>(2)

    private var state = DashboardState()

    private var pendingMode: TunnelState.Mode = TunnelState.Mode.Rule

    init {
        binding.self = this

        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val widget = dashboardAdapter.currentList.getOrNull(position) ?: return 1

                return widget.span.coerceIn(1, spanCount())
            }
        }

        binding.dashboardPage.apply {
            setLayoutManager(gridLayoutManager)
            setAdapter(dashboardAdapter)
            setHasFixedSize(false)

            // A new state arrives roughly once a second; the default
            // animator would spend all of it cross-fading unchanged cards.
            itemAnimator = null
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.page_dashboard -> showPage(0)
                R.id.page_proxy -> showPage(1)
                R.id.page_settings -> showPage(2)
            }
            true
        }

        applyDashboardWidgets()
    }

    // ------------------------------------------------------------------ grid

    /**
     * Two columns on a phone, three on a small tablet, four on a large one.
     *
     * The unit card height comes from `values-sw600dp` / `values-sw840dp`
     * dimens, so the same breakpoint widens the grid and grows the cells.
     */
    private fun spanCount(): Int {
        val widthDp = context.resources.configuration.smallestScreenWidthDp

        return when {
            widthDp >= 840 -> 4
            widthDp >= 600 -> 3
            else -> 2
        }
    }

    // ------------------------------------------------------ widget management

    /** Placed widgets, in the user's order — includes hidden ones. */
    fun placedWidgets(): List<DashboardWidget> {
        val stored = uiStore.dashboardCardOrder
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { DashboardWidget.byKey(it) }
            .filter { it.placeable }

        val known = stored.distinct()

        return known + DashboardWidget.placeableWidgets.filter { it !in known }
    }

    fun dashboardWidgetHidden(): Set<String> = uiStore.dashboardCardHidden

    /** Widgets that are not currently on the dashboard. */
    fun unplacedWidgets(): List<DashboardWidget> {
        val placed = placedWidgets().toSet()

        return DashboardWidget.placeableWidgets.filter { it !in placed }
    }

    fun moveDashboardWidget(from: Int, to: Int) {
        val order = placedWidgets().toMutableList()

        if (from !in order.indices || to !in order.indices)
            return

        order.add(to, order.removeAt(from))

        uiStore.dashboardCardOrder = order.joinToString(",") { it.key }

        applyDashboardWidgets()
    }

    fun setDashboardWidgetHidden(key: String, hidden: Boolean) {
        val hiddenSet = uiStore.dashboardCardHidden.toMutableSet()

        if (hidden)
            hiddenSet.add(key)
        else
            hiddenSet.remove(key)

        uiStore.dashboardCardHidden = hiddenSet

        applyDashboardWidgets()
    }

    fun showDashboardWidget(widget: DashboardWidget) {
        val order = placedWidgets().toMutableList()

        if (widget in order)
            return

        order.add(widget)

        uiStore.dashboardCardOrder = order.joinToString(",") { it.key }

        applyDashboardWidgets()
    }

    fun removeDashboardWidget(widget: DashboardWidget) {
        val order = placedWidgets().filterNot { it == widget }

        uiStore.dashboardCardOrder = order.joinToString(",") { it.key }

        val hidden = uiStore.dashboardCardHidden.toMutableSet()
        hidden.remove(widget.key)

        uiStore.dashboardCardHidden = hidden

        applyDashboardWidgets()
    }

    private fun applyDashboardWidgets() {
        val hidden = uiStore.dashboardCardHidden

        val visible = placedWidgets()
            .filter { it.key !in hidden }
            .filter { !it.requiresRunning || state.clashRunning }

        val widgets = visible + DashboardWidget.fixedWidgets

        dashboardAdapter.submitList(widgets)
    }

    // ----------------------------------------------------------------- state

    /** The single entry point for every dashboard value. */
    suspend fun setDashboardState(state: DashboardState) {
        withContext(Dispatchers.Main) {
            val runningChanged = this.state.clashRunning != state.clashRunning

            this.state = state

            if (runningChanged)
                applyDashboardWidgets()

            dashboardAdapter.setState(state)
        }
    }

    /** Read back by MainActivity after a mode pill tap. */
    fun checkedOutboundMode(): TunnelState.Mode = pendingMode

    /** Optimistic update right after a mode patch, before the next fetch. */
    suspend fun updateOutboundMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            setDashboardState(state.copy(mode = mode))
        }
    }

    private fun requestModePatch(mode: TunnelState.Mode) {
        pendingMode = mode

        request(Request.PatchMode)
    }

    // ------------------------------------------------------------------ page

    fun setPage(index: Int, view: View, title: String) {
        require(index in 1..2) { "page index must be 1 (proxy) or 2 (settings)" }

        pages[index - 1]?.let { old ->
            binding.pagesContainer.removeView(old)
        }

        pages[index - 1] = view

        binding.pagesContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        view.visibility = View.GONE

        view.findViewById<TextView>(R.id.activity_bar_title_view)?.text = title

        // embedded pages navigate back to the dashboard instead of finishing the activity
        view.findViewById<View>(R.id.activity_bar_close_view)?.setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.page_dashboard
        }
    }

    private fun showPage(index: Int) {
        binding.dashboardPage.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.dashboardHeader.visibility = if (index == 0) View.VISIBLE else View.GONE

        pages.forEachIndexed { i, page ->
            page?.visibility = if (index == i + 1) View.VISIBLE else View.GONE
        }
    }

    fun showProxyPage() {
        binding.bottomNavigation.selectedItemId = R.id.page_proxy
    }

    fun showSettingsPage() {
        binding.bottomNavigation.selectedItemId = R.id.page_settings
    }

    fun navigateBackToDashboard(): Boolean {
        if (binding.bottomNavigation.selectedItemId == R.id.page_dashboard)
            return false

        binding.bottomNavigation.selectedItemId = R.id.page_dashboard

        return true
    }

    // ----------------------------------------------------------------- about

    /** Placeholder list dialog; replaced by drag-to-reorder in the edit pass. */
    suspend fun showEditCardsDialog() {
        withContext(Dispatchers.Main) {
            val list = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
            }

            rebuildEditList(list)

            com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.edit_dashboard_cards)
                .setView(list)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun rebuildEditList(list: android.widget.LinearLayout) {
        list.removeAllViews()

        val inflater = android.view.LayoutInflater.from(context)
        val hidden = uiStore.dashboardCardHidden
        val order = placedWidgets()

        order.forEachIndexed { index, widget ->
            val row = com.github.kr328.clash.design.databinding.DialogDashboardCardBinding
                .inflate(inflater, list, false)

            row.titleView.text = context.getString(widget.titleRes)
            row.visibilityView.isChecked = widget.key !in hidden

            row.visibilityView.setOnCheckedChangeListener { _, checked ->
                setDashboardWidgetHidden(widget.key, !checked)
            }

            row.moveUpView.isEnabled = index > 0
            row.moveUpView.alpha = if (index > 0) 1.0f else 0.4f
            row.moveUpView.setOnClickListener {
                moveDashboardWidget(index, index - 1)
                rebuildEditList(list)
            }

            val last = order.size - 1
            row.moveDownView.isEnabled = index < last
            row.moveDownView.alpha = if (index < last) 1.0f else 0.4f
            row.moveDownView.setOnClickListener {
                moveDashboardWidget(index, index + 1)
                rebuildEditList(list)
            }

            list.addView(row.root)
        }
    }

    suspend fun showAbout(versionName: String) {
        withContext(Dispatchers.Main) {
            val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }

            com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setView(binding.root)
                .show()
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    /** Exposed so MainActivity can push chart samples without a round trip. */
    val trafficSamples: TrafficHistory
        get() = trafficHistory
}