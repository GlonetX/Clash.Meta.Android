package com.github.kr328.clash.design.adapter

import android.view.ViewGroup
import androidx.databinding.Observable
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.BR
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.component.ProxyPageFactory
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.component.ProxyViewState
import com.github.kr328.clash.design.model.ProxyPageState
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.ui.Surface
import com.github.kr328.clash.design.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProxyPageAdapter(
    private val surface: Surface,
    private val config: ProxyViewConfig,
    private val adapters: List<ProxyAdapter>,
    private val stateChanged: (Int) -> Unit,
) : RecyclerView.Adapter<ProxyPageFactory.Holder>() {
    private val factory = ProxyPageFactory(config)
    private var parent: RecyclerView? = null

    /**
     * Extra top padding, in pixels, contributed by UI that sits inside the
     * activity bar (the per-group search box). Kept here so pages created
     * later get the same offset as the pages already on screen.
     */
    var extraTopPadding = 0
        set(value) {
            field = value

            forEachLivePage { applyInsets(it.recyclerView) }
        }

    private val toolbarHeight = config.context.getPixels(R.dimen.toolbar_height)
    private val tabHeight = config.context.getPixels(R.dimen.tab_layout_height)
    private val dockClearance = config.context.getPixels(R.dimen.main_dock_content_clearance)

    val states = List(adapters.size) { ProxyPageState() }

    init {
        surface.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                if (propertyId == BR.insets) {
                    forEachLivePage { applyInsets(it.recyclerView) }
                }
            }
        })
    }

    suspend fun updateAdapter(
        position: Int,
        proxies: List<Proxy>,
        selectable: Boolean,
        parent: ProxyState,
        links: Map<String, ProxyState>
    ) {
        val states = withContext(Dispatchers.Default) {
            proxies.map {
                val link = if (it.isGroup) links[it.name] else null

                ProxyViewState(config, it, parent, link)
            }
        }

        withContext(Dispatchers.Main) {
            adapters[position].apply {
                this.selectable = selectable
                this.now = parent.now
                this.swapDataSet(this::states, states, false)
                this.applyFilter()
            }

            requestRedrawVisible()
        }
    }

    fun requestRedrawVisible() {
        factory.fromRoot(parent?.firstVisibleView ?: return)
            .recyclerView.invalidateChildren()
    }

    /** Applies the per-group search keyword to every group. */
    fun setKeyword(keyword: String) {
        adapters.forEach { it.setKeyword(keyword) }
    }

    /** True when [setKeyword] hid every node of the group at [position]. */
    fun isFilteredEmpty(position: Int): Boolean {
        return adapters.getOrNull(position)?.isFilteredEmpty ?: false
    }

    /**
     * Re-applies the card metrics after a density / column-count change. The
     * span lookup reads the config on every call, so invalidating the span
     * assignments plus a layout pass is enough; no page has to be recreated.
     */
    fun applyLayoutConfig() {
        forEachLivePage { holder ->
            val manager = holder.recyclerView.layoutManager

            if (manager is GridLayoutManager) {
                manager.spanSizeLookup = factory.newSpanSizeLookup()
                manager.invalidateSpanAssignments()
                manager.requestLayout()
            }

            holder.recyclerView.requestLayout()
        }

        requestRedrawVisible()
    }

    private fun applyInsets(view: RecyclerView) {
        view.setPaddingRelative(
            view.paddingLeft,
            surface.insets.top + toolbarHeight + tabHeight + extraTopPadding,
            view.paddingRight,
            surface.insets.bottom + dockClearance,
        )
    }

    private fun forEachLivePage(block: (ProxyPageFactory.Holder) -> Unit) {
        val pager = parent ?: return

        for (index in 0 until pager.childCount) {
            val holder = pager.getChildAt(index).tag as? ProxyPageFactory.Holder ?: continue

            block(holder)
        }
    }

    /**
     * Scroll the page at [position] so the currently selected proxy is visible.
     * Returns false when nothing is selected, the node is filtered out, or the
     * page is not attached.
     */
    fun scrollToSelected(position: Int): Boolean {
        val adapter = adapters.getOrNull(position) ?: return false

        val index = adapter.indexOf(adapter.now)
        if (index < 0)
            return false

        val target = livePages().firstOrNull { it.position == position } ?: return false

        target.post {
            (target.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(index, target.height / 3)
        }

        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProxyPageFactory.Holder {
        val holder = factory.newInstance()

        applyInsets(holder.recyclerView)

        holder.recyclerView.addScrolledToBottomObserver { view, bottom ->
            val position = view.position
            val state = states[position]

            if (state.bottom != bottom) {
                state.bottom = bottom

                stateChanged(position)
            }
        }

        return holder
    }

    override fun onBindViewHolder(holder: ProxyPageFactory.Holder, position: Int) {
        val adapter = adapters[position]

        states[position].bottom = false

        holder.recyclerView.apply {
            this.position = position
            this.swapAdapter(adapter, false)
        }

        applyInsets(holder.recyclerView)
    }

    override fun getItemCount(): Int {
        return adapters.size
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.parent = recyclerView

        recyclerView.isFocusable = false
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.parent = null
    }

    private fun livePages(): List<RecyclerView> {
        val pager = parent ?: return emptyList()

        return (0 until pager.childCount)
            .asSequence()
            .map { pager.getChildAt(it) }
            .mapNotNull { it.tag as? ProxyPageFactory.Holder }
            .map { it.recyclerView }
            .toList()
    }

    private var RecyclerView.position: Int
        get() {
            return tag as? Int ?: -1
        }
        set(value) {
            tag = value
        }
}