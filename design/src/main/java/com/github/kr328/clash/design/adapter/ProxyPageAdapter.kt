package com.github.kr328.clash.design.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Proxy
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

    val states = List(adapters.size) { ProxyPageState() }

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
            }

            requestRedrawVisible()
        }
    }

    fun requestRedrawVisible() {
        factory.fromRoot(parent?.firstVisibleView ?: return)
            .recyclerView.invalidateChildren()
    }

    /**
     * Scroll the page at [position] so the currently selected proxy is visible.
     * Returns false when nothing is selected or the page is not attached.
     */
    fun scrollToSelected(position: Int): Boolean {
        val adapter = adapters.getOrNull(position) ?: return false

        val index = adapter.states.indexOfFirst { it.proxy.name == adapter.now }
        if (index < 0)
            return false

        val pager = parent ?: return false

        val target = (0 until pager.childCount)
            .asSequence()
            .map { pager.getChildAt(it) }
            .mapNotNull { it.tag as? ProxyPageFactory.Holder }
            .map { it.recyclerView }
            .firstOrNull { it.position == position }
            ?: return false

        target.post {
            (target.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(index, target.height / 3)
        }

        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProxyPageFactory.Holder {
        val holder = factory.newInstance()

        val toolbarHeight = config.context.getPixels(R.dimen.toolbar_height)
        val tabHeight = config.context.getPixels(R.dimen.tab_layout_height)
        val dockClearance = config.context.getPixels(R.dimen.main_dock_content_clearance)

        holder.recyclerView.bindInsets(surface, toolbarHeight + tabHeight, dockClearance)
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


    private var RecyclerView.position: Int
        get() {
            return tag as? Int ?: -1
        }
        set(value) {
            tag = value
        }
}