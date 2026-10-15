package com.github.kr328.clash.design

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import com.github.kr328.clash.design.settings.NavigationBarStyle
import com.github.kr328.clash.design.settings.SettingsStore
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(
    context: Context,
    private val uiStore: UiStore,
    private val settings: SettingsStore,
) : Design<MainDesign.Request>(context) {
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

        applyNavigationBarStyle()

        applyDashboardWidgets()
    }

    private fun applyNavigationBarStyle() {
        val floating = settings.navigationBarStyle == NavigationBarStyle.FloatingDock
        val navigation = binding.bottomNavigation
        val params = navigation.layoutParams as FrameLayout.LayoutParams
        val margin = context.resources.getDimensionPixelSize(R.dimen.main_dock_margin_horizontal)

        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.leftMargin = if (floating) margin else 0
        params.rightMargin = if (floating) margin else 0
        params.bottomMargin = if (floating) {
            surface.insets.bottom + context.resources.getDimensionPixelSize(R.dimen.main_dock_margin_bottom)
        } else {
            0
        }

        navigation.background = if (floating) {
            context.getDrawable(R.drawable.bg_dock_glass)
        } else {
            ColorDrawable(context.resolveThemedColor(com.google.android.material.R.attr.colorSurface))
        }
        navigation.elevation = if (floating) {
            context.resources.getDimension(R.dimen.main_dock_elevation)
        } else {
            0f
        }

        binding.mainContent.setPadding(
            binding.mainContent.paddingLeft,
            binding.mainContent.paddingTop,
            binding.mainContent.paddingRight,
            if (floating) context.resources.getDimensionPixelSize(R.dimen.main_dock_content_clearance) else 0,
        )
        navigation.layoutParams = params
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

    /**
     * Every placeable widget, in the user's order — including hidden ones,
     * so a widget's position survives being hidden and shown again.
     */
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

    /** Widgets that are not currently on the dashboard, i.e. hidden ones. */
    fun unplacedWidgets(): List<DashboardWidget> =
        DashboardWidget.placeableWidgets.filter { it.key in uiStore.dashboardCardHidden }

    fun setDashboardWidgetHidden(key: String, hidden: Boolean) {
        val hiddenSet = uiStore.dashboardCardHidden.toMutableSet()

        if (hidden)
            hiddenSet.add(key)
        else
            hiddenSet.remove(key)

        uiStore.dashboardCardHidden = hiddenSet

        applyDashboardWidgets()
    }

    /** Put a widget back on the dashboard: unhide it, keeping its old slot. */
    fun showDashboardWidget(widget: DashboardWidget) {
        val order = placedWidgets().toMutableList()

        if (widget !in order) {
            order.add(widget)

            uiStore.dashboardCardOrder = order.joinToString(",") { it.key }
        }

        setDashboardWidgetHidden(widget.key, false)
    }

    private fun applyDashboardWidgets() {
        val hidden = uiStore.dashboardCardHidden

        val visible = placedWidgets()
            .filter { it.key !in hidden }
            .filter { !it.requiresRunning || state.clashRunning }

        // Fixed widgets bracket the user-ordered block: the status hero pins
        // to the top and the links card to the bottom. `sortedBy` is stable,
        // so the placeable widgets keep the user's order inside the block.
        val widgets = (visible + DashboardWidget.fixedWidgets).sortedBy { widget ->
            if (widget.placeable) PLACEABLE_BLOCK else widget.defaultOrder
        }

        dashboardAdapter.submitList(widgets)
    }

    // ----------------------------------------------------------------- state

    /** The single entry point for every dashboard value. */
    suspend fun setDashboardState(state: DashboardState) {
        withContext(Dispatchers.Main) {
            // `this` is the CoroutineScope in here; qualify the screen's
            // state explicitly — the bare name is shadowed by the parameter.
            val runningChanged =
                this@MainDesign.state.clashRunning != state.clashRunning

            this@MainDesign.state = state

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
     * Drag to reorder (long-press any row), switch to hide. The order lives
     * in the same `UiStore.dashboardCardOrder` string it always did — only
     * the editing mechanism changed, from a hand-rolled dialog with per-row
     * arrow buttons to an `ItemTouchHelper`.
     */
    suspend fun showEditCardsDialog() {
        withContext(Dispatchers.Main) {
            val editAdapter = DashboardEditAdapter(
                context = context,
                isHidden = { key -> key in uiStore.dashboardCardHidden },
                onHiddenChanged = { key, hidden ->
                    setDashboardWidgetHidden(key, hidden)
                },
            ).apply {
                submit(placedWidgets())
            }

            val list = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = editAdapter

                // The dialog wraps its custom view; without an explicit
                // height a long list would push the buttons off screen.
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

            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.edit_dashboard_cards)
                .setView(list)
                .setNeutralButton(R.string.dashboard_add_widget, null)
                .setPositiveButton(R.string.dashboard_edit_done, null)
                .show()

            // Keep the dialog open behind the sheet so several widgets can
            // be added in a row; the default neutral handler dismisses it.
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                showAddWidgetSheet { editAdapter.notifyDataSetChanged() }
            }
        }
    }

    /**
     * Bottom sheet listing every placeable widget not currently on the grid.
     * Picking one unhides it in its old slot.
     */
    private fun showAddWidgetSheet(onAdded: () -> Unit = {}) {
        val unplaced = unplacedWidgets()

        val sheet = BottomSheetDialog(context)

        val content: View = if (unplaced.isEmpty()) {
            TextView(context).apply {
                setText(R.string.dashboard_widgets_empty)
                setTextAppearance(context, R.style.TextAppearance_App_Body)
                gravity = Gravity.CENTER

                val pad = context.resources.getDimensionPixelSize(R.dimen.app_space_xl)
                setPadding(pad, pad, pad, pad)
            }
        } else {
            val pickerAdapter = DashboardWidgetPickerAdapter(context) { widget ->
                showDashboardWidget(widget)

                onAdded()

                sheet.dismiss()
            }.apply {
                submit(unplaced)
            }

            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = pickerAdapter
            }
        }

        sheet.setContentView(content)
        sheet.show()
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

    companion object {
        /**
         * Sort key for placeable widgets in [applyDashboardWidgets]: above
         * every fixed widget's `defaultOrder` except the links card (1000),
         * so status pins to the top and links to the bottom.
         */
        private const val PLACEABLE_BLOCK = 500
    }
}
