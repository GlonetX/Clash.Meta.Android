package com.github.kr328.clash.design.dashboard

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.AdapterDashboardConnectionsBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardIpInfoBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardLinksBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardMemoryBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardOutboundModeBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardProvidersBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardProfileBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardServicesBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardStatusBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardTrafficChartBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardTrafficTotalBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardUptimeBinding
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.view.AppRow
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
            DashboardWidget.PROVIDERS ->
                Holder.Providers(AdapterDashboardProvidersBinding.inflate(inflater, parent, false))
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
            DashboardWidget.TRAFFIC_CHART ->
                bindTrafficChart((holder as Holder.TrafficChart).binding)
            DashboardWidget.TRAFFIC_TOTAL ->
                bindTrafficTotal((holder as Holder.TrafficTotal).binding)
            DashboardWidget.MEMORY -> bindMemory((holder as Holder.Memory).binding)
            DashboardWidget.CONNECTIONS ->
                bindConnections((holder as Holder.Connections).binding)
            DashboardWidget.UPTIME -> bindUptime((holder as Holder.Uptime).binding)
            DashboardWidget.SERVICES -> bindServices((holder as Holder.Services).binding)
            DashboardWidget.PROVIDERS ->
                bindProviders((holder as Holder.Providers).binding)
            DashboardWidget.IP_INFO -> bindIpInfo((holder as Holder.IpInfo).binding)
            DashboardWidget.PROFILE -> bindProfile((holder as Holder.Profile).binding)
            DashboardWidget.LINKS -> bindLinks((holder as Holder.Links).binding)
        }
    }

    // ---------------------------------------------------------------- status

    private fun bindStatus(binding: AdapterDashboardStatusBinding) {
        val running = state.clashRunning
        // `starting` = the toggle was tapped but the service has not reported
        // back yet. The core startup crosses processes and can take a moment;
        // without this state the card sits on "Stopped / tap to start" for
        // the whole gap and invites a double-tap.
        val starting = running != state.coreStarted

        binding.statusCard.setOnClickListener { onRequest(MainDesign.Request.ToggleStatus) }

        when {
            running -> {
                binding.statusCard.setCardBackgroundColor(
                    context.resolveThemedColor(MaterialR.attr.colorPrimary)
                )
                binding.statusCard.strokeWidth = 0
            }
            starting -> {
                binding.statusCard.setCardBackgroundColor(
                    context.resolveThemedColor(MaterialR.attr.colorSurface)
                )
                binding.statusCard.setStrokeColor(
                    ColorStateList.valueOf(
                        context.resolveThemedColor(MaterialR.attr.colorPrimary)
                    )
                )
                binding.statusCard.strokeWidth =
                    context.resources.getDimensionPixelSize(R.dimen.app_space_xs)
            }
            else -> {
                // Stopped must NOT be plain colorSurface — every other card on
                // the page is colorSurface, so the hero would lose its status
                // role entirely. surfaceVariant keeps it a distinct surface
                // without pretending to be "on".
                binding.statusCard.setCardBackgroundColor(
                    context.resolveThemedColor(MaterialR.attr.colorSurfaceVariant)
                )
                binding.statusCard.strokeWidth = 0
            }
        }

        val foreground = context.resolveThemedColor(
            when {
                running -> MaterialR.attr.colorOnPrimary
                starting -> MaterialR.attr.colorPrimary
                else -> MaterialR.attr.colorOnSurfaceVariant
            }
        )

        binding.statusIcon.setImageResource(
            when {
                running -> R.drawable.ic_outline_check_circle
                starting -> R.drawable.ic_dashboard_status_starting
                else -> R.drawable.ic_outline_not_interested
            }
        )
        binding.statusIcon.imageTintList = ColorStateList.valueOf(foreground)

        binding.statusTitle.text = context.getString(
            when {
                running -> R.string.running
                starting -> R.string.dashboard_status_starting
                else -> R.string.stopped
            }
        )
        binding.statusTitle.setTextColor(foreground)

        binding.statusSubtext.text = when {
            running -> context.getString(
                R.string.format_traffic_forwarded, state.forwarded
            )
            starting -> context.getString(R.string.dashboard_status_waiting)
            else -> context.getString(R.string.tap_to_start)
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

        val place = place@{
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

        // The pill is colorPrimary, so the selected label must be onPrimary
        // and the idle labels onSurfaceVariant — without this the active
        // label is unreadable on the filled pill.
        val onPrimary = ColorStateList.valueOf(
            context.resolveThemedColor(MaterialR.attr.colorOnPrimary)
        )
        val idle = ColorStateList.valueOf(
            context.resolveThemedColor(MaterialR.attr.colorOnSurfaceVariant)
        )
        listOf(
            binding.modeRule to (index == 0),
            binding.modeGlobal to (index == 1),
            binding.modeDirect to (index == 2),
        ).forEach { (label, selected) ->
            label.setTextColor(if (selected) onPrimary else idle)
        }
    }

    private fun modeIndex(mode: TunnelState.Mode): Int = when (mode) {
        TunnelState.Mode.Direct -> 2
        TunnelState.Mode.Global -> 1
        else -> 0
    }

    // ------------------------------------------------------------- providers

    private fun bindProviders(binding: AdapterDashboardProvidersBinding) {
        val providers = state.providers
            .filter { it.vehicleType != Provider.VehicleType.Inline }

        // [MainDesign] drops this widget when there is nothing remote, so a
        // GONE root here would only be a transient during a state swap —
        // and a GONE item still occupies its measured height in a
        // RecyclerView, which is what used to leave a blank gap on the page.
        binding.root.visibility = View.VISIBLE
        if (providers.isEmpty())
            return

        binding.root.setOnClickListener { onRequest(MainDesign.Request.OpenProviders) }
        binding.providersUpdateAll.setOnClickListener {
            onRequest(MainDesign.Request.OpenProviders)
        }

        val list = binding.providersList
        list.removeAllViews()

        providers.take(MAX_PROVIDER_ROWS).forEachIndexed { index, provider ->
            val row = AppRow(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )

                icon = context.getDrawable(R.drawable.ic_baseline_swap_vertical_circle)
                iconContainerVisible = false

                text = provider.name

                subtext = context.getString(
                    when (provider.type) {
                        Provider.Type.Rule -> R.string.dashboard_provider_rule
                        else -> R.string.dashboard_provider_proxy
                    }
                ) + " · " + formatUpdatedAt(provider.updatedAt)

                setOnClickListener { onRequest(MainDesign.Request.OpenProviders) }
            }

            list.addView(row)

            if (index < minOf(providers.size, MAX_PROVIDER_ROWS) - 1) {
                val divider = View(context)
                divider.setBackgroundColor(
                    context.resolveThemedColor(MaterialR.attr.colorOutline)
                )
                divider.alpha = 0.2f
                list.addView(
                    divider,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        context.resources.getDimensionPixelSize(R.dimen.divider_size),
                    ),
                )
            }
        }

        if (providers.size > MAX_PROVIDER_ROWS) {
            val more = TextView(context).apply {
                text = context.getString(
                    R.string.dashboard_provider_more, providers.size - MAX_PROVIDER_ROWS
                )
                setTextAppearance(R.style.TextAppearance_App_Label)
                setPadding(0, context.resources.getDimensionPixelSize(R.dimen.app_space_sm), 0, 0)
            }
            list.addView(more)
        }
    }

    private fun formatUpdatedAt(updatedAt: Long): String {
        if (updatedAt <= 0)
            return context.getString(R.string.dashboard_provider_never)

        val delta = (System.currentTimeMillis() - updatedAt) / 1000L

        return when {
            delta < 60 -> context.getString(R.string.dashboard_provider_just_now)
            delta < 3600 -> context.getString(R.string.dashboard_provider_minutes_ago, delta / 60)
            delta < 86400 -> context.getString(R.string.dashboard_provider_hours_ago, delta / 3600)
            else -> context.getString(R.string.dashboard_provider_days_ago, delta / 86400)
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

            // The chips have no state-list colour of their own; the selected
            // window is called out with the primary colour and full alpha.
            chip.setTextColor(
                context.resolveThemedColor(
                    if (selected) MaterialR.attr.colorPrimary
                    else MaterialR.attr.colorOnSurfaceVariant
                )
            )
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
        binding.root.setOnClickListener { onRequest(MainDesign.Request.OpenConnections) }

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
        binding.root.setOnClickListener { onRequest(MainDesign.Request.OpenLatency) }

        binding.servicesPager.setServices(state.services)
        binding.servicesRefresh.setOnClickListener {
            onRequest(MainDesign.Request.RefreshServices)
        }
    }

    // --------------------------------------------------------------- ip info

    private fun bindIpInfo(binding: AdapterDashboardIpInfoBinding) {
        binding.root.setOnClickListener { onRequest(MainDesign.Request.OpenIPQuality) }

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
        binding.root.setOnClickListener { onRequest(MainDesign.Request.OpenProfiles) }

        binding.profileName.text =
            state.profileName ?: context.getString(R.string.not_selected)

        binding.profileSummary.text = state.profileSummary.orEmpty()
        binding.profileSummary.visibility = visible(state.profileSummary)
    }

    // ----------------------------------------------------------------- links

    private fun bindLinks(binding: AdapterDashboardLinksBinding) {
        binding.rowProviders.visibility =
            if (state.clashRunning && state.hasProviders) View.VISIBLE else View.GONE

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

        class Providers(val binding: AdapterDashboardProvidersBinding) :
            Holder(binding.root)

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

        /** Rows shown on the dashboard providers card before "+N more". */
        private const val MAX_PROVIDER_ROWS = 4

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