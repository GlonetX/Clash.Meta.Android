package com.github.kr328.clash.design

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import androidx.databinding.Observable
import com.github.kr328.clash.core.model.ConnectionItem
import com.github.kr328.clash.design.BR
import com.github.kr328.clash.design.adapter.ConnectionAdapter
import com.github.kr328.clash.design.databinding.DesignConnectionsBinding
import com.github.kr328.clash.design.databinding.DialogConnectionDetailBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.model.ConnectionItemDisplay
import com.github.kr328.clash.design.model.ConnectionRow
import com.github.kr328.clash.design.model.ConnectionSort
import com.github.kr328.clash.design.model.startTimeDisplay
import com.github.kr328.clash.design.model.toDisplay
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.view.AppRow
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.material.R as MaterialR

/**
 * The active-connections page.
 *
 * The core hands us a flat list of [ConnectionItem]s every two seconds; every
 * user preference (search text aside) lives in [UiStore] so it survives
 * process death, and every visible transformation — filter, sort, grouping —
 * is a pure function of that snapshot so re-applying a preference never needs
 * another core round trip.
 *
 * Note on the "kill process" affordance: an ordinary Android app cannot kill
 * another package's process (`Process.killProcess` only terminates the
 * caller's own process), so the destructive action offered per app is
 * *close every connection that app owns*, behind a confirmation dialog.
 */
class ConnectionsDesign(
    context: Context,
    private val uiStore: UiStore,
) : Design<ConnectionsDesign.Request>(context) {
    sealed class Request {
        object Refresh : Request()

        data class Close(val id: String) : Request()
        data class CloseAll(val ids: List<String>) : Request()
    }

    private class Sample(val time: Long, val totals: Map<String, Long>)

    private val binding = DesignConnectionsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val adapter = ConnectionAdapter(
        context = context,
        clickItem = { showDetail(it.item) },
        toggleGroup = { toggleGroup(it) },
    )

    /** Every active connection, formatted but not filtered. */
    private var snapshot: List<ConnectionItemDisplay> = emptyList()

    /** Bytes/second per connection, recomputed on every poll. */
    private var speeds: Map<String, Long> = emptyMap()

    private var sample: Sample? = null

    private var keyword: String = ""
    private var protocolFilter: String? = null
    private var processFilter: String? = null

    private var protocolValues: List<String> = emptyList()
    private var processValues: List<String> = emptyList()

    private var sheetDialog: AppBottomSheetDialog? = null
    private var sheetItem: ConnectionItemDisplay? = null

    // ------------------------------------------------------------------ data

    suspend fun update(connections: List<ConnectionItem>) {
        val now = System.currentTimeMillis() / 1000

        val formatted = withContext(Dispatchers.Default) {
            connections.map {
                it.toDisplay(context, now) { id -> requests.trySend(Request.Close(id)) }
            }
        }

        val computed = withContext(Dispatchers.Default) {
            val previous = sample

            val elapsed = (now - (previous?.time ?: now)).coerceAtLeast(1L)

            val speeds = formatted.associate { item ->
                val before = previous?.totals?.get(item.id)

                val delta = if (before == null) 0L else (item.totalBytes - before).coerceAtLeast(0L)

                item.id to (delta * 1000L / elapsed)
            }

            Triple(formatted, speeds, Sample(now, formatted.associate { it.id to it.totalBytes }))
        }

        withContext(Dispatchers.Main) {
            snapshot = computed.first
            speeds = computed.second
            sample = computed.third

            applyRows()
        }
    }

    /** Recomputes the visible rows from the cached snapshot. Main thread. */
    private fun applyRows() {
        val rows = buildRows(snapshot)

        adapter.rows = rows

        val shown = rows.count { it is ConnectionRow.Item }
        val upload = snapshot.sumOf { it.uploadBytes }
        val download = snapshot.sumOf { it.downloadBytes }

        binding.summaryView.text = if (shown == snapshot.size) {
            context.getString(
                R.string.conn_summary,
                snapshot.size,
                ConnectionItemDisplay.format(download),
                ConnectionItemDisplay.format(upload),
            )
        } else {
            context.getString(
                R.string.conn_summary_filtered,
                shown,
                snapshot.size,
                ConnectionItemDisplay.format(download),
                ConnectionItemDisplay.format(upload),
            )
        }

        binding.emptyView.setText(
            if (snapshot.isEmpty()) R.string.no_connections else R.string.conn_no_match
        )
        binding.emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

        syncChips()
    }

    private fun buildRows(items: List<ConnectionItemDisplay>): List<ConnectionRow> {
        val filtered = items.filter { matches(it) }

        if (!uiStore.connectionsGroupByProcess)
            return filtered.sortedWith(order())

        val collapsed = uiStore.connectionsCollapsedGroups

        val rows = ArrayList<ConnectionRow>(filtered.size)

        filtered
            .groupBy { it.process }
            .entries
            .sortedByDescending { entry -> entry.value.sumOf { it.totalBytes } }
            .forEach { entry ->
                val members = entry.value.sortedWith(order())
                val process = entry.key
                val isCollapsed = process in collapsed

                rows.add(
                    ConnectionRow.Group(
                        key = process,
                        title = process.ifBlank {
                            context.getString(R.string.conn_filter_unknown_app)
                        },
                        count = members.size,
                        countText = if (members.size == 1) {
                            context.getString(R.string.conn_group_count_one)
                        } else {
                            context.getString(R.string.conn_group_count, members.size)
                        },
                        traffic = "↓ ${ConnectionItemDisplay.format(members.sumOf { it.uploadBytes })}" +
                            "  ↑ ${ConnectionItemDisplay.format(members.sumOf { it.downloadBytes })}",
                        speed = members.sumOf { speeds[it.id] ?: 0L }
                            .takeIf { it > 0 }
                            ?.let {
                                context.getString(
                                    R.string.conn_speed,
                                    ConnectionItemDisplay.format(it),
                                )
                            }
                            .orEmpty(),
                        collapsed = isCollapsed,
                    )
                )

                members.mapTo(rows) { ConnectionRow.Item(it, isCollapsed) }
            }

        return rows
    }

    private fun matches(item: ConnectionItemDisplay): Boolean {
        protocolFilter?.let {
            if (!item.type.equals(it, ignoreCase = true))
                return false
        }

        processFilter?.let {
            if (item.process != it)
                return false
        }

        val key = keyword.trim()

        if (key.isNotEmpty() && !item.searchKey.contains(key.lowercase(Locale.getDefault())))
            return false

        return true
    }

    private fun order(): Comparator<ConnectionItemDisplay> {
        val base: Comparator<ConnectionItemDisplay> = when (uiStore.connectionsSort) {
            ConnectionSort.Size -> compareByDescending { it.totalBytes }
            ConnectionSort.Duration -> compareByDescending { it.durationSeconds }
            ConnectionSort.Process -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.process }
            ConnectionSort.Rule -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.ruleName }
            ConnectionSort.Host -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.host }
        }

        val withTieBreak = base.thenBy(String.CASE_INSENSITIVE_ORDER) { it.host }

        return if (uiStore.connectionsSortReversed) withTieBreak.reversed() else withTieBreak
    }

    // ----------------------------------------------------------------- chips

    /** Keeps the filter chips in sync with the protocols / apps on screen. */
    private fun syncChips() {
        val protocols = snapshot
            .map { it.type }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        // Alphabetical, not by traffic: a traffic-ordered chip row would be
        // rebuilt on every poll and would reorder itself under the user's finger.
        val processes = snapshot
            .map { it.process }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .take(MAX_PROCESS_CHIPS)

        if (protocols != protocolValues) {
            protocolValues = protocols

            buildChips(
                binding.protocolGroup,
                protocols,
                protocolFilter,
            ) { value ->
                protocolFilter = value
            }
        }

        if (processes != processValues) {
            processValues = processes

            buildChips(
                binding.processGroup,
                processes,
                processFilter,
            ) { value ->
                processFilter = value
            }
        }
    }

    /**
     * Rebuilds one chip row. A leading "All" chip is added automatically and
     * selects `null`, which is how "no filter on this field" is represented —
     * no sentinel string is needed, so a real value can never collide with it.
     */
    private fun buildChips(
        group: ChipGroup,
        values: List<String>,
        selected: String?,
        onSelected: (String?) -> Unit,
    ) {
        group.removeAllViews()

        val chips = LinkedHashMap<Int, String?>()

        fun addChip(value: String?, label: String) {
            val chip = Chip(context)

            chip.id = View.generateViewId()
            chip.text = label
            chip.tag = ChipValue(value)
            chip.isCheckable = true

            chip.setOnClickListener { view ->
                onSelected((view.tag as ChipValue).value)

                applyRows()
            }

            chips[chip.id] = value

            group.addView(chip)
        }

        addChip(null, context.getString(R.string.conn_filter_all))

        values.forEach { addChip(it, it) }

        val checked = chips.entries.firstOrNull { it.value == selected }?.key
            ?: chips.keys.firstOrNull()

        if (checked != null)
            group.check(checked)
    }

    private class ChipValue(val value: String?)

    // --------------------------------------------------------------- actions

    fun requestCloseAll() {
        val ids = snapshot.map { it.id }

        if (ids.isEmpty())
            return

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.conn_close_all_confirm_title)
            .setMessage(context.getString(R.string.conn_close_all_confirm_message, ids.size))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                requests.trySend(Request.CloseAll(ids))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toggleGroup(group: ConnectionRow.Group) {
        val collapsed = uiStore.connectionsCollapsedGroups.toMutableSet()

        if (!collapsed.add(group.key))
            collapsed.remove(group.key)

        uiStore.connectionsCollapsedGroups = collapsed

        applyRows()
    }

    /** Closes every connection the detail sheet's connection belongs to. */
    fun requestCloseProcessFromSheet() {
        val item = sheetItem ?: return

        val ids = snapshot.filter { it.process == item.process }.map { it.id }

        if (ids.isEmpty())
            return

        sheetDialog?.dismiss()

        requests.trySend(Request.CloseAll(ids))
    }

    fun requestCloseFromSheet() {
        val item = sheetItem ?: return

        sheetDialog?.dismiss()

        requests.trySend(Request.Close(item.id))
    }

    // ------------------------------------------------------------- detail UI

    private fun showDetail(item: ConnectionItemDisplay) {
        val dialog = AppBottomSheetDialog(context)

        val detail = DialogConnectionDetailBinding.inflate(
            context.layoutInflater,
            dialog.window?.decorView as ViewGroup?,
            false,
        )

        detail.self = this
        detail.titleView.text = item.host
        detail.subtitleView.text = "${item.traffic}   ${item.duration}"

        detail.chainView.removeAllViews()

        if (item.chains.isEmpty()) {
            addChainChip(detail.chainView, context.getString(R.string.conn_detail_no_chain))
        } else {
            item.chains.forEachIndexed { index, name ->
                if (index > 0)
                    addChainArrow(detail.chainView)

                addChainChip(detail.chainView, name)
            }
        }

        detail.fieldsView.removeAllViews()

        val unknown = context.getString(R.string.conn_detail_unknown)

        addField(detail.fieldsView, R.string.conn_detail_started, item.startTimeDisplay(context))
        addField(detail.fieldsView, R.string.conn_detail_network, item.network)
        addField(detail.fieldsView, R.string.conn_detail_type, item.type)

        if (item.process.isNotBlank())
            addField(detail.fieldsView, R.string.conn_detail_process, item.process)

        addField(
            detail.fieldsView,
            R.string.conn_detail_rule,
            item.ruleName.ifBlank { unknown },
        )

        if (item.rulePayload.isNotBlank())
            addField(detail.fieldsView, R.string.conn_detail_payload, item.rulePayload)

        addField(
            detail.fieldsView,
            R.string.conn_detail_source,
            item.source.ifBlank { unknown },
        )
        addField(
            detail.fieldsView,
            R.string.conn_detail_destination,
            item.destination.ifBlank { unknown },
        )
        addField(
            detail.fieldsView,
            R.string.conn_detail_remote,
            item.remoteDestination.ifBlank { unknown },
        )

        detail.closeProcessView.visibility =
            if (item.process.isBlank()) View.GONE else View.VISIBLE

        sheetItem = item
        sheetDialog = dialog

        dialog.setOnDismissListener {
            sheetItem = null
            sheetDialog = null
        }

        dialog.setContentView(detail.root)
        dialog.show()
    }

    private fun addField(parent: LinearLayout, @StringRes label: Int, value: String) {
        parent.addView(
            AppRow(context).apply {
                text = context.getString(label)
                subtext = value
                iconContainerVisible = false

                setPaddingRelative(
                    0,
                    context.getPixels(R.dimen.app_space_sm),
                    0,
                    context.getPixels(R.dimen.app_space_sm),
                )

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
        )
    }

    private fun addChainChip(parent: LinearLayout, label: String) {
        parent.addView(
            Chip(context).apply {
                text = label
                isCheckable = false
                isClickable = false
            }
        )
    }

    private fun addChainArrow(parent: LinearLayout) {
        val size = context.getPixels(R.dimen.app_icon_glyph_size_small)
        val margin = context.getPixels(R.dimen.app_space_xs)

        parent.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_baseline_arrow_forward)

                imageTintList = ColorStateList.valueOf(
                    context.resolveThemedColor(MaterialR.attr.colorOutline)
                )

                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = margin
                    marginEnd = margin
                }
            }
        )
    }

    // ------------------------------------------------------------------ init

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.bindAppBarElevation(binding.activityBarLayout)
        binding.mainList.applyLinearAdapter(context, adapter)

        // the activity bar grows with the summary line and the filter bar, so
        // the list cannot use the fixed toolbar_height padding
        binding.barContainer.addOnLayoutChangeListener { view, _, top, _, bottom, _, _, _, _ ->
            updateListPadding(bottom - top)
        }

        surface.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                if (propertyId == BR.insets)
                    updateListPadding(binding.barContainer.height)
            }
        })

        binding.searchView.setOnClickListener {
            toggleFilterBar()
        }

        binding.clearKeywordView.setOnClickListener {
            binding.keywordView.setText("")
        }

        binding.keywordView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()

                binding.clearKeywordView.visibility =
                    if (text.isEmpty()) View.GONE else View.VISIBLE

                if (text == keyword)
                    return

                keyword = text

                applyRows()
            }
        })

        binding.sortView.setOnClickListener { view -> showSortMenu(view) }
        binding.menuView.setOnClickListener { view -> showMainMenu(view) }
    }

    private fun updateListPadding(barHeight: Int) {
        binding.mainList.setPaddingRelative(
            0,
            if (barHeight > 0) barHeight else context.getPixels(R.dimen.toolbar_height),
            0,
            surface.insets.bottom,
        )
    }

    private fun toggleFilterBar() {
        val visible = binding.filterBar.visibility == View.VISIBLE

        binding.filterBar.visibility = if (visible) View.GONE else View.VISIBLE

        binding.searchView.imageTintList = ColorStateList.valueOf(
            context.resolveThemedColor(
                if (visible) MaterialR.attr.colorControlNormal else MaterialR.attr.colorPrimary
            )
        )

        if (visible) {
            binding.keywordView.clearFocus()
        } else {
            binding.keywordView.requestFocus()
        }

        updateListPadding(binding.barContainer.height)
    }

    private fun showSortMenu(anchor: View) {
        PopupMenu(context, anchor).apply {
            menuInflater.inflate(R.menu.menu_connections_sort, menu)

            menu.findItem(R.id.sort_size).isChecked = uiStore.connectionsSort == ConnectionSort.Size
            menu.findItem(R.id.sort_duration).isChecked =
                uiStore.connectionsSort == ConnectionSort.Duration
            menu.findItem(R.id.sort_process).isChecked =
                uiStore.connectionsSort == ConnectionSort.Process
            menu.findItem(R.id.sort_rule).isChecked = uiStore.connectionsSort == ConnectionSort.Rule
            menu.findItem(R.id.sort_host).isChecked = uiStore.connectionsSort == ConnectionSort.Host
            menu.findItem(R.id.sort_reversed).isChecked = uiStore.connectionsSortReversed

            setOnMenuItemClickListener { item ->
                val applied = when (item.itemId) {
                    R.id.sort_size -> {
                        uiStore.connectionsSort = ConnectionSort.Size
                        true
                    }
                    R.id.sort_duration -> {
                        uiStore.connectionsSort = ConnectionSort.Duration
                        true
                    }
                    R.id.sort_process -> {
                        uiStore.connectionsSort = ConnectionSort.Process
                        true
                    }
                    R.id.sort_rule -> {
                        uiStore.connectionsSort = ConnectionSort.Rule
                        true
                    }
                    R.id.sort_host -> {
                        uiStore.connectionsSort = ConnectionSort.Host
                        true
                    }
                    R.id.sort_reversed -> {
                        uiStore.connectionsSortReversed = item.isChecked
                        true
                    }
                    else -> false
                }

                if (applied)
                    applyRows()

                applied
            }
        }.show()
    }

    private fun showMainMenu(anchor: View) {
        PopupMenu(context, anchor).apply {
            menuInflater.inflate(R.menu.menu_connections, menu)

            menu.findItem(R.id.group_by_process).isChecked = uiStore.connectionsGroupByProcess

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.group_by_process -> {
                        uiStore.connectionsGroupByProcess = item.isChecked

                        applyRows()

                        true
                    }
                    R.id.close_all -> {
                        requestCloseAll()

                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    companion object {

        /**
         * A device can have hundreds of distinct processes connected at once;
         * the chip row stops growing here and the search box covers the rest.
         */
        private const val MAX_PROCESS_CHIPS = 48
    }
}