package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.dashboard.DashboardAdapter
import com.github.kr328.clash.design.dashboard.DashboardEditAdapter
import com.github.kr328.clash.design.dashboard.DashboardState
import com.github.kr328.clash.design.dashboard.DashboardWidget
import com.github.kr328.clash.design.dashboard.DashboardWidgetPickerAdapter
import com.github.kr328.clash.design.dashboard.TrafficHistory
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    /**
     * "Customise dashboard".
     *
     * Drag to reorder, switch to hide. The order lives in the same
     * `UiStore.dashboardCardOrder` string it always did — only the editing
     * mechanism changed, from a hand-rolled dialog with per-row arrow buttons
     * to an `ItemTouchHelper`.
     */
    suspend fun showEditCardsDialog() {
        withContext(Dispatchers.Main) {
            val editAdapter = DashboardEditAdapter(
                context = context,
                hidden = uiStore.dashboardCardHidden,
                onHiddenChanged = { key, hidden ->
                    setDashboardWidgetHidden(key, hidden)
                    editAdapter.notifyDataSetChanged()
                },
            ).apply {
                submit(placedWidgets())
            }

            val list = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = editAdapter
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (context.resources.displayMetrics.heightPixels * 0.5f).toInt(),
                )
            }

            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0,
            ) {
                override fun isLongPressDragEnabled(): Boolean = true

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition

                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION)
                        return false

                    editAdapter.items.add(to, editAdapter.items.removeAt(from))
                    editAdapter.notifyItemMoved(from, to)

                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) {
                    super.clearView(recyclerView, viewHolder)

                    persistWidgetOrder(editAdapter.items)
                }
            }).attachToRecyclerView(list)

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.edit_dashboard_cards)
                .setView(list)
                .setNeutralButton(R.string.dashboard_add_widget) { _, _ -> showAddWidgetSheet() }
                .setPositiveButton(R.string.dashboard_edit_done, null)
                .show()
        }
    }

    /** Bottom sheet listing every placeable widget not currently on the grid. */
    private fun showAddWidgetSheet() {
        val pickerAdapter = DashboardWidgetPickerAdapter(context) { widget ->
            showDashboardWidget(widget)
        }.apply {
            submit(unplacedWidgets())
        }

        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = pickerAdapter
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(context)

        sheet.setContentView(list)
        sheet.show()

        if (pickerAdapter.itemCount == 0) {
            sheet.setOnShowListener {
                com.google.android.material.snackbar.Snackbar.make(
                    list,
                    R.string.dashboard_widgets_empty,
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun persistWidgetOrder(widgets: List<DashboardWidget>) {
        uiStore.dashboardCardOrder = widgets.joinToString(",") { it.key }

        applyDashboardWidgets()
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