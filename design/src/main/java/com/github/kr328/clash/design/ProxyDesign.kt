package com.github.kr328.clash.design

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.viewpager2.widget.ViewPager2
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.adapter.ProxyAdapter
import com.github.kr328.clash.design.adapter.ProxyPageAdapter
import com.github.kr328.clash.design.component.ProxyMenu
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.component.ProxyViewState
import com.github.kr328.clash.design.databinding.DesignProxyBinding
import com.github.kr328.clash.design.databinding.DialogProxyNodeBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.view.AppRow
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.material.R as MaterialR

/**
 * The proxy page.
 *
 * The page is embedded as a tab of [com.github.kr328.clash.MainActivity]; it
 * keeps the ViewPager2 + TabLayout + custom-drawn [com.github.kr328.clash.design.component.ProxyView]
 * grid mechanism, and the request contract of [Request] is unchanged so the
 * activity's `select {}` loop does not need to learn about anything new.
 */
class ProxyDesign(
    context: Context,
    overrideMode: TunnelState.Mode?,
    groupNames: List<String>,
    private val uiStore: UiStore,
) : Design<ProxyDesign.Request>(context) {
    sealed class Request {
        object ReloadAll : Request()
        object ReLaunch : Request()

        data class PatchMode(val mode: TunnelState.Mode?) : Request()
        data class Reload(val index: Int) : Request()
        data class Select(val index: Int, val name: String) : Request()
        data class UrlTest(val index: Int) : Request()
    }

    private val binding = DesignProxyBinding
        .inflate(context.layoutInflater, context.root, false)

    private val config = ProxyViewConfig(
        context,
        uiStore.proxyLine,
        uiStore.proxyDensity,
        uiStore.proxyIconStyle,
    )

    /**
     * The node each group currently routes through, as of the last reload
     * (or the last tap, which updates it optimistically).
     */
    private class GroupStatus(val name: String, val delay: Int)

    private val groupStatus: Array<GroupStatus?> = arrayOfNulls(groupNames.size)

    /** One adapter per group, also kept directly so taps can read them. */
    private val groupAdapters: List<ProxyAdapter> = List(groupNames.size) { index ->
        ProxyAdapter(
            config,
            { name ->
                // optimistic header update: Request.Select patches the core and
                // redraws the cards, but no group reload follows a selection, so
                // without this the status line would go stale until the next
                // reload or URL test
                groupStatus[index] =
                    GroupStatus(name, groupAdapters.getOrNull(index)?.delayOf(name) ?: 0)

                if (index == binding.pagesView.currentItem)
                    renderGroupStatus()

                requests.trySend(Request.Select(index, name))
            },
        ) { state ->
            showNodeActions(index, state)
        }
    }

    private val menu: ProxyMenu by lazy {
        ProxyMenu(context, binding.menuView, overrideMode, uiStore, requests) {
            applyLayoutConfig()
        }
    }

    private val adapter: ProxyPageAdapter?
        get() = binding.pagesView.adapter as? ProxyPageAdapter

    private var horizontalScrolling = false

    private val verticalBottomScrolled: Boolean
        get() = pageState(binding.pagesView.currentItem)?.bottom ?: false

    private var urlTesting: Boolean
        get() = pageState(binding.pagesView.currentItem)?.urlTesting ?: false
        set(value) {
            pageState(binding.pagesView.currentItem)?.urlTesting = value
        }

    private fun pageState(index: Int) =
        adapter?.states?.getOrNull(index)

    companion object {
        /**
         * The node search keyword of the current app session.
         *
         * Deliberately not in [UiStore]: a search is a transient hunt for one
         * node, not a preference. It only has to survive the page being
         * rebuilt — the group list changes or the "hide not selectable"
         * filter is toggled, both of which recreate the whole design — not
         * an app restart.
         */
        private var sessionKeyword: String = ""
    }

    override val root: View = binding.root

    suspend fun updateGroup(
        position: Int,
        proxies: List<Proxy>,
        selectable: Boolean,
        parent: ProxyState,
        links: Map<String, ProxyState>
    ) {
        val ordered =
            if (uiStore.proxySort == ProxySort.Delay) stableDelaySort(proxies) else proxies

        adapter?.updateAdapter(position, ordered, selectable, parent, links)

        pageState(position)?.urlTesting = false

        groupStatus[position] = proxies
            .firstOrNull { it.name == parent.now }
            ?.let { GroupStatus(it.name, it.delay) }

        updateUrlTestButtonStatus()
        updateFilterStatus()
    }

    suspend fun requestRedrawVisible() {
        withContext(Dispatchers.Main) {
            adapter?.requestRedrawVisible()
        }
    }

    suspend fun showModeSwitchTips() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.mode_switch_tips, Toast.LENGTH_LONG).show()
        }
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.menuView.setOnClickListener {
            menu.show()
        }

        binding.locateView.setOnClickListener {
            requestLocate()
        }

        binding.searchView.setOnClickListener {
            toggleSearchBar()
        }

        binding.clearKeywordView.setOnClickListener {
            binding.keywordView.setText("")
        }

        binding.keywordView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()

                sessionKeyword = text

                binding.clearKeywordView.visibility =
                    if (text.isEmpty()) View.GONE else View.VISIBLE

                adapter?.setKeyword(text)

                updateFilterStatus()
            }
        })

        if (groupNames.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE

            binding.noMatchView.visibility = View.GONE
            binding.searchView.visibility = View.GONE
            binding.urlTestView.visibility = View.GONE
            binding.locateView.visibility = View.GONE
            binding.tabLayoutView.visibility = View.GONE
            binding.groupStatusBar.visibility = View.GONE
            binding.elevationView.visibility = View.GONE
            binding.pagesView.visibility = View.GONE
            binding.urlTestFloatView.visibility = View.GONE
        } else {
            binding.urlTestFloatView.supportImageTintList = ColorStateList.valueOf(
                context.resolveThemedColor(MaterialR.attr.colorOnPrimary)
            )

            binding.pagesView.apply {
                adapter = ProxyPageAdapter(
                    surface,
                    config,
                    groupAdapters
                ) {
                    if (it == currentItem)
                        updateUrlTestButtonStatus()
                }

                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageScrollStateChanged(state: Int) {
                        horizontalScrolling = state != ViewPager2.SCROLL_STATE_IDLE

                        updateUrlTestButtonStatus()
                    }

                    override fun onPageSelected(position: Int) {
                        // remember the group the user actually landed on, so
                        // the next launch of the page opens here
                        groupNames.getOrNull(position)?.let { uiStore.proxyLastGroup = it }

                        updateFilterStatus()

                        renderGroupStatus()
                    }
                })
            }

            TabLayoutMediator(binding.tabLayoutView, binding.pagesView) { tab, index ->
                tab.text = groupNames[index]
            }.attach()

            val initialPosition = groupNames.indexOf(uiStore.proxyLastGroup)

            binding.pagesView.post {
                if (initialPosition > 0)
                    binding.pagesView.setCurrentItem(initialPosition, false)
            }

            // the search row and the group status row grow the header, so
            // their heights have to become part of the grid's top inset
            binding.searchBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateHeaderExtraPadding()
            }

            binding.groupStatusBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateHeaderExtraPadding()
            }

            // restore an in-flight search after a rebuild. Deliberately
            // without requestFocus: the user is watching the grid, not
            // typing, and the keyboard must not pop open on its own.
            if (sessionKeyword.isNotEmpty()) {
                binding.searchBar.visibility = View.VISIBLE

                binding.searchView.imageTintList = ColorStateList.valueOf(
                    context.resolveThemedColor(MaterialR.attr.colorPrimary)
                )

                binding.keywordView.setText(sessionKeyword)
            }
        }
    }

    fun requestUrlTesting() {
        urlTesting = true

        requests.trySend(Request.UrlTest(binding.pagesView.currentItem))

        updateUrlTestButtonStatus()
    }

    /**
     * Stable, human ordering for the delay sort.
     *
     * The core (`tunnel/proxies.go`) sorts real delays ascending with its
     * special values last, but its comparison has two problems the UI has
     * to compensate for: its sort is not stable, so nodes with equal keys
     * reshuffle on every reload, and a never-tested node arrives with
     * `delay == 0`, which its key ranks as the *fastest* possible delay —
     * the opposite of "never-tested nodes last".
     *
     * This re-sort (UI-side only; the core is frozen) orders: tested delays
     * ascending, then timed-out nodes, then never-tested nodes, with the
     * node name as a deterministic tiebreaker so the order survives reloads.
     */
    private fun stableDelaySort(proxies: List<Proxy>): List<Proxy> {
        fun rank(delay: Int): Int = when {
            delay < 0 -> 1 // tested but timed out
            delay == 0 -> 2 // never tested
            delay >= 0xffff -> 2 // core sentinel for missing history
            else -> 0 // tested
        }

        return proxies.sortedWith(
            compareBy(
                { rank(it.delay) },
                { if (rank(it.delay) == 0) it.delay else 0 },
                { it.name.lowercase() },
            )
        )
    }

    fun requestLocate() {
        adapter?.scrollToSelected(binding.pagesView.currentItem)
    }

    // -------------------------------------------------------------- node sheet

    private var nodeSheet: AppBottomSheetDialog? = null
    private var sheetNode: Proxy? = null

    /**
     * Long-press on a node card: a bottom sheet with the node's data and the
     * actions the group data can support. The node "host" (server address)
     * and per-node traffic totals are not part of the model the core
     * exposes, and a single-node URL test would need a request type the
     * activity's dispatcher does not know — both are out of scope here.
     */
    private fun showNodeActions(index: Int, state: ProxyViewState) {
        val proxy = state.proxy

        val dialog = AppBottomSheetDialog(context)

        val sheet = DialogProxyNodeBinding.inflate(
            context.layoutInflater,
            dialog.window?.decorView as ViewGroup?,
            false,
        )

        sheet.self = this
        sheet.titleView.text = proxy.title.ifBlank { proxy.name }
        sheet.subtitleView.text = "%s  ·  %s".format(
            groupNames.getOrNull(index) ?: "",
            proxy.type,
        )

        sheet.fieldsView.removeAllViews()

        if (proxy.title.isNotBlank() && proxy.title != proxy.name)
            addNodeField(sheet.fieldsView, R.string.proxy_node_detail_name, proxy.name)

        when (proxy.delay) {
            in 1..Short.MAX_VALUE ->
                addNodeField(
                    sheet.fieldsView,
                    R.string.proxy_node_detail_delay,
                    context.getString(R.string.proxy_node_delay_with_unit, proxy.delay),
                )

            else -> {
                val text =
                    if (proxy.delay < 0) context.getString(R.string.timeout)
                    else context.getString(R.string.proxy_node_delay_none)

                addNodeField(sheet.fieldsView, R.string.proxy_node_detail_delay, text)
            }
        }

        nodeSheet = dialog
        sheetNode = proxy

        dialog.setOnDismissListener {
            nodeSheet = null
            sheetNode = null
        }

        dialog.setContentView(sheet.root)
        dialog.show()
    }

    fun requestCopyNodeName() {
        val node = sheetNode ?: return

        val data = ClipData.newPlainText(node.name, node.name)

        context.getSystemService<ClipboardManager>()?.setPrimaryClip(data)

        nodeSheet?.dismiss()

        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun addNodeField(parent: LinearLayout, @StringRes label: Int, value: String) {
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

    /**
     * Re-reads every layout preference into the shared [ProxyViewConfig] and
     * asks the pages to re-measure. No reload: the node list did not change.
     */
    private fun applyLayoutConfig() {
        config.proxyLine = uiStore.proxyLine
        config.density = uiStore.proxyDensity
        config.iconStyle = uiStore.proxyIconStyle

        adapter?.applyLayoutConfig()

        updateFilterStatus()
    }

    private fun toggleSearchBar() {
        if (binding.searchBar.visibility == View.VISIBLE)
            hideSearchBar()
        else
            showSearchBar()
    }

    private fun showSearchBar() {
        binding.searchBar.visibility = View.VISIBLE

        binding.searchView.imageTintList = ColorStateList.valueOf(
            context.resolveThemedColor(MaterialR.attr.colorPrimary)
        )

        binding.keywordView.requestFocus()

        updateFilterStatus()
    }

    /**
     * Hiding the search row also drops the keyword: a filter the user can no
     * longer see is a bug, not a feature.
     */
    private fun hideSearchBar() {
        binding.searchBar.visibility = View.GONE

        binding.searchView.imageTintList = ColorStateList.valueOf(
            context.resolveThemedColor(MaterialR.attr.colorControlNormal)
        )

        binding.keywordView.clearFocus()

        if (binding.keywordView.text?.isNotEmpty() == true)
            binding.keywordView.setText("")

        updateHeaderExtraPadding()

        updateFilterStatus()
    }

    /**
     * Total height of the header rows below the tab row (the per-group search
     * box and the group live-status line), pushed into the grid's top inset
     * so the grid never slides under them.
     */
    private fun updateHeaderExtraPadding() {
        val search = if (binding.searchBar.visibility == View.VISIBLE) binding.searchBar.height else 0
        val status = if (binding.groupStatusBar.visibility == View.VISIBLE) binding.groupStatusBar.height else 0

        adapter?.extraTopPadding = search + status
    }

    /**
     * Renders the live-status line of the group currently displayed: the
     * node it routes through and that node's delay, colour-coded with the
     * same delay tiers as the node cards. Tapping the line locates the node.
     *
     * While a URL test of the displayed group is in flight the line also
     * shows an indeterminate spinner, and falls back to a plain
     * "Testing delays…" line when the group has no rendered status yet.
     */
    private fun renderGroupStatus() {
        val index = binding.pagesView.currentItem

        val status = groupStatus.getOrNull(index)
        val testing = pageState(index)?.urlTesting == true

        if ((status == null || status.name.isEmpty()) && !testing) {
            binding.groupStatusBar.visibility = View.GONE
            binding.groupTestingView.visibility = View.GONE

            updateHeaderExtraPadding()

            return
        }

        binding.groupStatusBar.visibility = View.VISIBLE
        binding.groupTestingView.visibility = if (testing) View.VISIBLE else View.GONE

        val builder = SpannableStringBuilder()

        if (status == null || status.name.isEmpty()) {
            builder.append(context.getString(R.string.proxy_url_testing))
        } else {
            builder.append(context.getString(R.string.proxy_group_status_name, status.name))

            when (status.delay) {
                in 1 until ProxyViewState.DELAY_MEDIUM_THRESHOLD ->
                    appendStatusDelay(builder, "${status.delay}ms", config.delayGood)

                in ProxyViewState.DELAY_MEDIUM_THRESHOLD..Short.MAX_VALUE ->
                    appendStatusDelay(builder, "${status.delay}ms", config.delayMedium)

                else -> {
                    if (status.delay < 0)
                        appendStatusDelay(builder, context.getString(R.string.timeout), config.delayTimeout)
                    // never tested: the name alone
                }
            }
        }

        binding.groupStatusView.text = builder

        updateHeaderExtraPadding()
    }

    private fun appendStatusDelay(builder: SpannableStringBuilder, text: String, color: Int) {
        val start = builder.length

        builder.append("  ·  ")
        builder.append(text)

        builder.setSpan(
            ForegroundColorSpan(color),
            start,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun updateFilterStatus() {
        val keyword = binding.keywordView.text?.toString().orEmpty().trim()

        val empty = adapter?.isFilteredEmpty(binding.pagesView.currentItem) ?: false

        if (empty) {
            binding.noMatchView.text =
                context.getString(R.string.proxy_no_match, keyword)
        }

        binding.noMatchView.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun updateUrlTestButtonStatus() {
        if (verticalBottomScrolled || horizontalScrolling || urlTesting) {
            binding.urlTestFloatView.hide()
        } else {
            binding.urlTestFloatView.show()
        }

        if (urlTesting) {
            binding.urlTestView.visibility = View.GONE
            binding.urlTestProgressView.visibility = View.VISIBLE
        } else {
            binding.urlTestView.visibility = View.VISIBLE
            binding.urlTestProgressView.visibility = View.GONE
        }

        renderGroupStatus()
    }
}