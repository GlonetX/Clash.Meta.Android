package com.github.kr328.clash.design

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.viewpager2.widget.ViewPager2
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.adapter.ProxyAdapter
import com.github.kr328.clash.design.adapter.ProxyPageAdapter
import com.github.kr328.clash.design.component.ProxyMenu
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.databinding.DesignProxyBinding
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
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
        adapter?.updateAdapter(position, proxies, selectable, parent, links)

        pageState(position)?.urlTesting = false

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
                    List(groupNames.size) { index ->
                        ProxyAdapter(config) { name ->
                            requests.trySend(Request.Select(index, name))
                        }
                    }
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

            // the search bar grows the bar, so its height has to become part of
            // the grid's top inset
            binding.searchBar.addOnLayoutChangeListener { view, _, top, _, bottom, _, _, _, _ ->
                if (view.visibility != View.VISIBLE)
                    return@addOnLayoutChangeListener

                adapter?.extraTopPadding = bottom - top
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

    fun requestLocate() {
        adapter?.scrollToSelected(binding.pagesView.currentItem)
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

        adapter?.extraTopPadding = 0

        updateFilterStatus()
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
    }
}