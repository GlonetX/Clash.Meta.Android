package com.github.kr328.clash.design.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.component.ProxyView
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.component.ProxyViewState

/**
 * Adapter for one proxy group.
 *
 * Holds the unfiltered [states] and a parallel list of visible positions so
 * the per-group search box can hide nodes without destroying the selection
 * state or the per-cell animation state.
 */
class ProxyAdapter(
    private val config: ProxyViewConfig,
    private val clicked: (String) -> Unit,
) : RecyclerView.Adapter<ProxyAdapter.Holder>() {
    class Holder(val view: ProxyView) : RecyclerView.ViewHolder(view)

    var selectable: Boolean = false
    var states: List<ProxyViewState> = emptyList()

    /** name of the currently selected proxy in the owning group */
    var now: String = ""

    private var keyword: String = ""
    private var filtered: List<Int> = emptyList()

    /** Free-text filter over node name, title and subtitle. */
    fun setKeyword(value: String) {
        val key = value.trim()

        if (key == keyword)
            return

        keyword = key

        applyFilter()
    }

    /** Recomputes [filtered] after [states] has been replaced wholesale. */
    fun applyFilter() {
        filtered = if (keyword.isEmpty()) {
            states.indices.toList()
        } else {
            states.indices.filter { states[it].matches(keyword) }
        }

        notifyDataSetChanged()
    }

    /** Position of the node called [name] in the *visible* list, or -1. */
    fun indexOf(name: String): Int {
        val index = states.indexOfFirst { it.proxy.name == name }

        if (index < 0)
            return -1

        return filtered.indexOf(index)
    }

    /**
     * True when the filter hides everything in this group. False while the
     * group has not loaded yet, so restoring a session keyword during a page
     * rebuild does not flash a "no match" notice before the first data
     * arrives.
     */
    val isFilteredEmpty: Boolean
        get() = keyword.isNotEmpty() && states.isNotEmpty() && filtered.isEmpty()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(ProxyView(config.context, config))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = states[filtered[position]]

        holder.view.apply {
            state = current

            setOnClickListener {
                clicked(current.proxy.name)
            }

            val isSelector = selectable

            isFocusable = isSelector
            isClickable = isSelector

            current.update(true)
        }
    }

    override fun getItemCount(): Int {
        return filtered.size
    }
}