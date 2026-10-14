package com.github.kr328.clash.design.component

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.view.VerticalScrollableHost

class ProxyPageFactory(private val config: ProxyViewConfig) {
    class Holder(
        val recyclerView: RecyclerView,
        val root: View,
    ) : RecyclerView.ViewHolder(root)

    private val childrenPool = RecyclerView.RecycledViewPool()

    fun newInstance(): Holder {
        val root = VerticalScrollableHost(config.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val recyclerView = RecyclerView(config.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(recyclerView)

        recyclerView.apply {
            layoutManager = GridLayoutManager(config.context, GRID_COLUMNS).apply {
                spanSizeLookup = newSpanSizeLookup()
            }

            setRecycledViewPool(childrenPool)

            clipToPadding = false
        }

        return Holder(recyclerView, root).apply {
            root.tag = this
        }
    }

    fun fromRoot(root: View): Holder {
        return root.tag!! as Holder
    }

    /**
     * The lookup reads [ProxyViewConfig.proxyLine] on every call instead of
     * baking the column count in, so switching between the one-, two- and
     * three-column layouts only needs the span assignments invalidated — the
     * page does not have to be recreated.
     */
    fun newSpanSizeLookup(): GridLayoutManager.SpanSizeLookup {
        return object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (config.proxyLine) {
                    1 -> GRID_COLUMNS
                    2 -> GRID_COLUMNS / 2
                    else -> GRID_COLUMNS / 3
                }
            }
        }
    }

    companion object {
        /** Grid resolution: 6 spans => 1, 2 or 3 columns per row. */
        const val GRID_COLUMNS = 6
    }
}