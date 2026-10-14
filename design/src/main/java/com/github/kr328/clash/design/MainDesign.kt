package com.github.kr328.clash.design

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.github.kr328.clash.design.store.UiStore
import android.widget.ScrollView
import android.widget.TextView
import com.github.kr328.clash.core.model.ExternalIP
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficDownloadValue
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.core.util.trafficUpload
import com.github.kr328.clash.core.util.trafficUploadValue
import com.github.kr328.clash.design.databinding.DialogDashboardCardBinding
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.util.toDisplayString
import com.google.android.material.button.MaterialButtonToggleGroup
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
        PatchMode,
        OpenIPQuality,
        OpenLatency,
        OpenConnections,
        EditCards,
    }

    /** Manageable dashboard cards in default order. */
    val dashboardCards = listOf(
        DashboardCard(R.string.outbound_mode, R.id.slot_outbound_mode),
        DashboardCard(R.string.proxy, R.id.slot_proxy),
        DashboardCard(R.string.network_quality, R.id.slot_network_quality),
        DashboardCard(R.string.connections, R.id.slot_connections),
        DashboardCard(R.string.ip_info, R.id.slot_ip_info),
        DashboardCard(R.string.profile, R.id.slot_profile),
    )

    data class DashboardCard(val titleRes: Int, val slotViewId: Int) {
        val key: String
            get() = slotViewId.toString()
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    // embedded tab pages, index 0 = proxy page, index 1 = settings page
    private val pages = arrayOfNulls<View>(2)

    init {
        binding.self = this

        binding.colorClashStarted = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        binding.colorClashStopped = context.resolveThemedColor(R.attr.colorClashStopped)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.page_dashboard -> showPage(0)
                R.id.page_proxy -> showPage(1)
                R.id.page_settings -> showPage(2)
            }
            true
        }

        binding.modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked)
                return@addOnButtonCheckedListener

            val mode = when (checkedId) {
                R.id.mode_direct -> TunnelState.Mode.Direct
                R.id.mode_global -> TunnelState.Mode.Global
                R.id.mode_rule -> TunnelState.Mode.Rule
                else -> return@addOnButtonCheckedListener
            }

            if (updatingMode)
                return@addOnButtonCheckedListener

            request(Request.PatchMode)
        }

        applyDashboardCards()
    }

    private var updatingMode = false

    /** Card order + visibility from UiStore. */
    fun dashboardCardOrder(): List<String> {
        val stored = uiStore.dashboardCardOrder
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val known = stored.filter { id -> dashboardCards.any { it.key == id } }

        // append missing cards in default order
        return known + dashboardCards.map { it.key }.filter { it !in known }
    }

    fun dashboardCardHidden(): Set<String> {
        return uiStore.dashboardCardHidden
    }

    fun moveDashboardCard(key: String, offset: Int) {
        val order = dashboardCardOrder().toMutableList()

        val index = order.indexOf(key)
        if (index < 0)
            return

        val target = (index + offset).coerceIn(0, order.size - 1)

        order.removeAt(index)
        order.add(target, key)

        uiStore.dashboardCardOrder = order.joinToString(",")

        applyDashboardCards()
    }

    fun setDashboardCardHidden(key: String, hidden: Boolean) {
        val hiddenSet = uiStore.dashboardCardHidden.toMutableSet()

        if (hidden)
            hiddenSet.add(key)
        else
            hiddenSet.remove(key)

        uiStore.dashboardCardHidden = hiddenSet

        applyDashboardCards()
    }

    suspend fun showEditCardsDialog() {
        withContext(Dispatchers.Main) {
            val list = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            val rebuild: () -> Unit = {
                list.removeAllViews()

                val inflater = LayoutInflater.from(context)
                val hidden = dashboardCardHidden()

                dashboardCardOrder().forEachIndexed { index, key ->
                    val card = dashboardCards.firstOrNull { it.key == key } ?: return@forEachIndexed

                    val row = DialogDashboardCardBinding.inflate(inflater, list, false)

                    row.titleView.text = context.getString(card.titleRes)
                    row.visibilityView.isChecked = key !in hidden

                    row.visibilityView.setOnCheckedChangeListener { _, checked ->
                        setDashboardCardHidden(key, !checked)
                    }

                    row.moveUpView.isEnabled = index > 0
                    row.moveUpView.alpha = if (index > 0) 1.0f else 0.4f
                    row.moveUpView.setOnClickListener {
                        moveDashboardCard(key, -1)
                        rebuild()
                    }

                    val last = dashboardCardOrder().size - 1
                    row.moveDownView.isEnabled = index < last
                    row.moveDownView.alpha = if (index < last) 1.0f else 0.4f
                    row.moveDownView.setOnClickListener {
                        moveDashboardCard(key, 1)
                        rebuild()
                    }

                    list.addView(row.root)
                }
            }

            rebuild()

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.edit_dashboard_cards)
                .setView(ScrollView(context).apply { addView(list) })
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun applyDashboardCards() {
        val container = binding.dashboardContainer
        val hidden = uiStore.dashboardCardHidden

        // collect slot views
        val slots = dashboardCards.mapNotNull { card ->
            container.findViewById<View>(card.slotViewId)?.let { card to it }
        }

        if (slots.isEmpty())
            return

        // anchor: the view right before the first slot in XML order is the
        // status card; re-attach slots right after it
        val firstIndex = slots.minOf { container.indexOfChild(it.second) }
        val anchorIndex = if (firstIndex > 0) firstIndex - 1 else -1

        val ordered = dashboardCardOrder()

        // detach all slots
        slots.forEach { (_, view) -> container.removeView(view) }

        // re-attach in stored order
        var insertAt = anchorIndex + 1

        ordered.forEach { key ->
            val (card, view) = slots.firstOrNull { it.first.key == key } ?: return@forEach

            container.addView(view, insertAt)

            // slot visibility = user preference only; the inner card keeps
            // its own clashRunning binding
            view.visibility = if (key in hidden) View.GONE else View.VISIBLE

            insertAt++
        }
    }

    fun checkedOutboundMode(): TunnelState.Mode {
        return when (binding.modeToggleGroup.checkedButtonId) {
            R.id.mode_direct -> TunnelState.Mode.Direct
            R.id.mode_global -> TunnelState.Mode.Global
            else -> TunnelState.Mode.Rule
        }
    }

    fun setPage(index: Int, view: View, title: String) {
        require(index in 1..2) { "page index must be 1 (proxy) or 2 (settings)" }

        pages[index - 1]?.let { old ->
            binding.pagesContainer.removeView(old)
        }

        pages[index - 1] = view

        binding.pagesContainer.addView(
            view,
            FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
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

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            binding.profileName = name
        }
    }

    suspend fun setProfileSummary(summary: String?) {
        withContext(Dispatchers.Main) {
            binding.profileSummary = summary
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            binding.clashRunning = running
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            binding.forwarded = value.trafficTotal()
        }
    }

    suspend fun setNetworkSpeed(traffic: com.github.kr328.clash.core.model.Traffic) {
        withContext(Dispatchers.Main) {
            binding.speedUp = traffic.trafficUpload() + "/s"
            binding.speedDown = traffic.trafficDownload() + "/s"

            binding.trafficChart.push(traffic.trafficUploadValue(), traffic.trafficDownloadValue())
        }
    }

    suspend fun setMemoryUsage(value: Long) {
        withContext(Dispatchers.Main) {
            binding.memoryUsage = value.toBytesString()
        }
    }

    suspend fun setIPInfo(exit: ExternalIP?, direct: ExternalIP?) {
        withContext(Dispatchers.Main) {
            binding.ipStatus = null

            binding.exitIPAddr = exit?.ip
            binding.exitIPDisplay = exit?.toDisplayString()
            binding.exitIPISP = exit?.isp?.takeIf { it.isNotBlank() }

            binding.directIPAddr = direct?.ip
            binding.directIPDisplay = direct?.toDisplayString()
        }
    }

    suspend fun setIPInfoLoading() {
        withContext(Dispatchers.Main) {
            binding.ipStatus = context.getString(R.string.ip_querying)
        }
    }

    suspend fun setIPInfoFailed() {
        withContext(Dispatchers.Main) {
            binding.ipStatus = context.getString(R.string.ip_query_failed)
        }
    }

    suspend fun setOutboundMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            updatingMode = true

            when (mode) {
                TunnelState.Mode.Direct -> binding.modeToggleGroup.check(R.id.mode_direct)
                TunnelState.Mode.Global -> binding.modeToggleGroup.check(R.id.mode_global)
                else -> binding.modeToggleGroup.check(R.id.mode_rule)
            }

            updatingMode = false
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            binding.mode = when (mode) {
                TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
                else -> context.getString(R.string.rule_mode)
            }
        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
            binding.hasProviders = has
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
}
