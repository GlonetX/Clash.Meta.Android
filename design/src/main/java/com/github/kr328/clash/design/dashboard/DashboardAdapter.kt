package com.github.kr328.clash.design.dashboard

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.AdapterDashboardConnectionsBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardIpInfoBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardLinksBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardMemoryBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardNodeBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardOutboundModeBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardProfileBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardServicesBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardStatusBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardTrafficChartBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardTrafficTotalBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardUptimeBinding
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.toBytesString
import com.google.android.material.R as MaterialR

/**
 * Renders the dashboard grid: one view type per [DashboardWidget], bound from
 * a single [DashboardState].
 *
 * The adapter holds no per-cell mutable dashboard data. The one piece of
 * mutable state it shares — the traffic sample buffer — is screen scoped, so
 * a recycled holder still draws the full series rather than starting from
 * scratch.
 *
 * Values are assigned from Kotlin rather than through DataBinding expressions
 * on purpose: most cells mix a theme colour, a formatted string and a
 * visibility rule, and spelling that in `@{...}` costs more than it saves.
 */
class DashboardAdapter(
    private val context: Context,
    private val history: TrafficHistory,
    private val onRequest: (MainDesign.Request) -> Unit,
    private val onSelectMode: (TunnelState.Mode) -> Unit,
) : ListAdapter<DashboardWidget, DashboardAdapter.Holder>(DIFF) {

    var state: DashboardState = DashboardState()
        private set

    /** Push a new snapshot; no-op when nothing actually changed. */
    fun setState(state: DashboardState) {
        if (this.state == state)
            return

        this.state = state

        if (itemCount > 0)
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE)
    }

    override fun getItemViewType(position: Int): Int = getItem(position).ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(context)

        return when (DashboardWidget.values()[viewType]) {
            DashboardWidget.STATUS ->
                Holder.Status(AdapterDashboardStatusBinding.inflate(inflater, parent, false))
            DashboardWidget.OUTBOUND_MODE ->
                Holder.OutboundMode(
                    AdapterDashboardOutboundModeBinding.inflate(inflater, parent, false)
                )
            DashboardWidget.NODE ->
                Holder.Node(AdapterDashboardNodeBinding.inflate(inflater, parent, false))
            DashboardWidget.TRAFFIC_CHART ->
                Holder.TrafficChart(
                    AdapterDashboardTrafficChartBinding.inflate(inflater, parent, false)
                )
            DashboardWidget.TRAFFIC_TOTAL ->
                Holder.TrafficTotal(
                    AdapterDashboardTrafficTotalBinding.inflate(inflater, parent, false)
                )
            DashboardWidget.MEMORY ->
                Holder.Memory(AdapterDashboardMemoryBinding.inflate(inflater, parent, false))
            DashboardWidget.CONNECTIONS ->
                Holder.Connections(
                    AdapterDashboardConnectionsBinding.inflate(inflater, parent, false)
                )
            DashboardWidget.UPTIME ->
                Holder.Uptime(AdapterDashboardUptimeBinding.inflate(inflater, parent, false))
            DashboardWidget.SERVICES ->
                Holder.Services(AdapterDashboardServicesBinding.inflate(inflater, parent, false))
            DashboardWidget.IP_INFO ->
                Holder.IpInfo(AdapterDashboardIpInfoBinding.inflate(inflater, parent, false))
            DashboardWidget.PROFILE ->
                Holder.Profile(AdapterDashboardProfileBinding.inflate(inflater, parent, false))
            DashboardWidget.LINKS ->
                Holder.Links(AdapterDashboardLinksBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        bind(holder, getItem(position))
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        bind(holder, getItem(position))
    }

    private fun bind(holder: Holder, widget: DashboardWidget) {
        when (widget) {
            DashboardWidget.STATUS -> bindStatus((holder as Holder.Status).binding)
            DashboardWidget.OUTBOUND_MODE ->
                bindOutboundMode((holder as Holder.OutboundMode).binding, holder)
            DashboardWidget.NODE -> bindNode((holder as Holder.Node).binding)
            DashboardWidget.TRAFFIC_CHART ->
                bindTrafficChart((holder as Holder.TrafficChart).binding)
            DashboardWidget.TRAFFIC_TOTAL ->
                bindTrafficTotal((holder as Holder.TrafficTotal).binding)
            DashboardWidget.MEMORY -> bindMemory((holder as Holder.Memory).binding)
            DashboardWidget.CONNECTIONS ->
                bindConnections((holder as Holder.Connections).binding)
            DashboardWidget.UPTIME -> bindUptime((holder as Holder.Uptime).binding)
            DashboardWidget.SERVICES -> bindServices((holder as Holder.Services).binding)
            DashboardWidget.IP_INFO -> bindIpInfo((holder as Holder.IpInfo).binding)
            DashboardWidget.PROFILE -> bindProfile((holder as Holder.Profile).binding)
            DashboardWidget.LINKS -> bindLinks((holder as Holder.Links).binding)
        }
    }

    // ---------------------------------------------------------------- status

    private fun bindStatus(binding: AdapterDashboardStatusBinding) {
        val running = state.clashRunning

        binding.statusCard.setCardBackgroundColor(
            context.resolveThemedColor(
                if (running) MaterialR.attr.colorPrimary else R.attr.colorClashStopped
            )
        )

        val foreground = context.resolveThemedColor(
            if (running) MaterialR.attr.colorOnPrimary else R.attr.colorOnSurface
        )

        binding.statusIcon.setImageResource(
            if (running) R.drawable.ic_outline_check_circle
            else R.drawable.ic_outline_not_interested
        )
        binding.statusIcon.imageTintList = ColorStateList.valueOf(foreground)

        binding.statusTitle.text =
            context.getString(if (running) R.string.running else R.string.stopped)
        binding.statusTitle.setTextColor(foreground)

        binding.statusSubtext.text = if (running) {
            context.getString(R.string.format_traffic_forwarded, state.forwarded)
        } else {
            context.getString(R.string.tap_to_start)
        }
        binding.statusSubtext.setTextColor(foreground)
        binding.statusSubtext.alpha = 0.75f
    }

    // ---------------------------------------------------------- outbound mode

    private fun bindOutboundMode(binding: AdapterDashboardOutboundModeBinding, holder: Holder.OutboundMode) {
        val index = modeIndex(state.mode)

        binding.modeRule.setOnClickListener { onSelectMode(TunnelState.Mode.Rule) }
        binding.modeGlobal.setOnClickListener { onSelectMode(TunnelState.Mode.Global) }
        binding.modeDirect.setOnClickListener { onSelectMode(TunnelState.Mode.Direct) }

        val place: () -> Unit = {
            val step = binding.modeStack.width / MODES

            if (step <= 0)
                return@place

            val params = binding.modeHighlight.layoutParams

            if (params.width != step) {
                params.width = step
                binding.modeHighlight.layoutParams = params
            }

            if (holder.pillPlaced) {
                binding.modeHighlight.animate()
                    .translationX(step * index.toFloat())
                    .setDuration(180)
                    .start()
            } else {
                // First layout of this holder: jump rather than slide from 0.
                binding.modeHighlight.animate().cancel()
                binding.modeHighlight.translationX = step * index.toFloat()
                holder.pillPlaced = true
            }
        }

        if (binding.modeStack.width > 0)
            place()
        else
            binding.modeStack.post { place() }
    }

    private fun modeIndex(mode: TunnelState.Mode): Int = when (mode) {
        TunnelState.Mode.Direct -> 2
        TunnelState.Mode.Global -> 1
        else -> 0
    }

    // ------------------------------------------------------------------ node

    private fun bindNode(binding: AdapterDashboardNodeBinding) {
        binding.nodeName.text =
            state.nodeName ?: context.getString(R.string.dashboard_node_unavailable)

        binding.nodeDetail.text = when {
            state.nodeDelay >= 0 ->
                context.getString(R.string.dashboard_probe_latency, state.nodeDelay)
            !state.nodeGroupName.isNullOrEmpty() -> state.nodeGroupName
            else -> context.getString(R.string.dashboard_delay_unknown)
        }
    }

    // ---------------------------------------------------------- traffic chart

    private fun bindTrafficChart(binding: AdapterDashboardTrafficChartBinding) {
        binding.speedDown.text = state.speedDown
        binding.speedUp.text = state.speedUp
        binding.chartTotal.text = state.forwarded

        binding.trafficChart.history = history
        binding.trafficChart.refresh()

        TrafficHistory.Window.values().forEach { window ->
            val chip = when (window) {
                TrafficHistory.Window.MINUTE_1 -> binding.windowOneMinute
                TrafficHistory.Window.MINUTE_5 -> binding.windowFiveMinutes
                TrafficHistory.Window.MINUTE_30 -> binding.windowThirtyMinutes
            }

            val selected = history.window == window

            chip.isSelected = selected
            chip.alpha = if (selected) 1f else 0.55f

            chip.setOnClickListener {
                if (history.window != window) {
                    history.window = window
                    binding.trafficChart.refresh()

                    // Only the chip row changes, so update it in place instead
                    // of round-tripping a new state through the screen.
                    bindTrafficChart(binding)
                }
            }
        }
    }

    // --------------------------------------------------------- traffic totals

    private fun bindTrafficTotal(binding: AdapterDashboardTrafficTotalBinding) {
        binding.donut.setTraffic(state.trafficTotalUp, state.trafficTotalDown)
        binding.donutUp.text = state.trafficTotalUp.toBytesString()
        binding.donutDown.text = state.trafficTotalDown.toBytesString()
    }

    // ----------------------------------------------------------------- memory

    private fun bindMemory(binding: AdapterDashboardMemoryBinding) {
        binding.memoryValue.text = state.memoryBytes.toBytesString()
        binding.memoryBar.setMemory(state.memoryBytes)
    }

    // ------------------------------------------------------------ connections

    private fun bindConnections(binding: AdapterDashboardConnectionsBinding) {
        binding.connectionsValue.text = state.connectionCount.toString()
        binding.requestsValue.text = state.requestCount.toString()
        binding.dnsValue.text = state.dnsCount.toString()
    }

    // ----------------------------------------------------------------- uptime

    private fun bindUptime(binding: AdapterDashboardUptimeBinding) {
        binding.uptimeValue.text = formatUptime(state.uptimeMillis)
        binding.uptimeSince.text = context.getString(
            if (state.clashRunning) R.string.dashboard_since_start
            else R.string.dashboard_not_running
        )
    }

    private fun formatUptime(millis: Long): String {
        val total = millis / 1000
        val days = total / 86400
        val hours = (total % 86400) / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60

        return if (days > 0) {
            context.getString(R.string.dashboard_uptime_days, days, hours, minutes)
        } else {
            context.getString(R.string.dashboard_uptime_clock, hours, minutes, seconds)
        }
    }

    // -------------------------------------------------------------- services

    private fun bindServices(binding: AdapterDashboardServicesBinding) {
        binding.servicesPager.setServices(state.services)
        binding.servicesRefresh.setOnClickListener {
            onRequest(MainDesign.Request.RefreshServices)
        }
    }

    // --------------------------------------------------------------- ip info

    private fun bindIpInfo(binding: AdapterDashboardIpInfoBinding) {
        binding.ipStatus.text = state.ipStatus.orEmpty()
        binding.ipStatus.visibility = visible(state.ipStatus)

        binding.exitIpAddr.text = state.exitIP ?: context.getString(R.string.unknown_ip)

        binding.exitIpDisplay.text = state.exitIPDisplay.orEmpty()
        binding.exitIpDisplay.visibility = visible(state.exitIPDisplay)

        binding.exitIpIsp.text = state.exitIPISP.orEmpty()
        binding.exitIpIsp.visibility = visible(state.exitIPISP)

        binding.directIpAddr.text = state.directIP ?: context.getString(R.string.unknown_ip)
        binding.directIpDisplay.text = state.directIPDisplay.orEmpty()

        binding.ipRefresh.setOnClickListener { onRequest(MainDesign.Request.RefreshIP) }
    }

    // --------------------------------------------------------------- profile

    private fun bindProfile(binding: AdapterDashboardProfileBinding) {
        binding.profileName.text =
            state.profileName ?: context.getString(R.string.not_selected)

        binding.profileSummary.text = state.profileSummary.orEmpty()
        binding.profileSummary.visibility = visible(state.profileSummary)
    }

    // ----------------------------------------------------------------- links

    private fun bindLinks(binding: AdapterDashboardLinksBinding) {
        binding.rowProviders.visibility =
            visible(state.clashRunning && state.hasProviders)

        binding.rowProviders.setOnClickListener { onRequest(MainDesign.Request.OpenProviders) }
        binding.rowLogs.setOnClickListener { onRequest(MainDesign.Request.OpenLogs) }
        binding.rowHelp.setOnClickListener { onRequest(MainDesign.Request.OpenHelp) }
        binding.rowAbout.setOnClickListener { onRequest(MainDesign.Request.OpenAbout) }
    }

    private fun visible(value: String?): Int =
        if (value.isNullOrEmpty()) View.GONE else View.VISIBLE

    // ---------------------------------------------------------------- holder

    sealed class Holder(root: View) : RecyclerView.ViewHolder(root) {
        class Status(val binding: AdapterDashboardStatusBinding) : Holder(binding.root)

        class OutboundMode(val binding: AdapterDashboardOutboundModeBinding) :
            Holder(binding.root) {
            /** The pill has been positioned at least once on this holder. */
            var pillPlaced = false
        }

        class Node(val binding: AdapterDashboardNodeBinding) : Holder(binding.root)

        class TrafficChart(val binding: AdapterDashboardTrafficChartBinding) :
            Holder(binding.root)

        class TrafficTotal(val binding: AdapterDashboardTrafficTotalBinding) :
            Holder(binding.root)

        class Memory(val binding: AdapterDashboardMemoryBinding) : Holder(binding.root)

        class Connections(val binding: AdapterDashboardConnectionsBinding) :
            Holder(binding.root)

        class Uptime(val binding: AdapterDashboardUptimeBinding) : Holder(binding.root)

        class Services(val binding: AdapterDashboardServicesBinding) : Holder(binding.root)

        class IpInfo(val binding: AdapterDashboardIpInfoBinding) : Holder(binding.root)

        class Profile(val binding: AdapterDashboardProfileBinding) : Holder(binding.root)

        class Links(val binding: AdapterDashboardLinksBinding) : Holder(binding.root)
    }

    companion object {
        private const val MODES = 3

        private const val PAYLOAD_STATE = "state"

        private val DIFF = object : DiffUtil.ItemCallback<DashboardWidget>() {
            override fun areItemsTheSame(
                oldItem: DashboardWidget,
                newItem: DashboardWidget,
            ): Boolean = oldItem == newItem

            override fun areContentsTheSame(
                oldItem: DashboardWidget,
                newItem: DashboardWidget,
            ): Boolean = oldItem == newItem
        }
    }
}