package com.github.kr328.clash.design.dashboard

import android.content.Context
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.databinding.AdapterDashboardEditRowBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardWidgetPickerBinding
import com.github.kr328.clash.design.util.layoutInflater

/**
 * The reorderable list behind "Customise dashboard".
 *
 * Dragging is handled by an [androidx.recyclerview.widget.ItemTouchHelper]
 * attached by the caller; this adapter only owns the mutable item list and
 * forwards hide/show switches.
 *
 * Hidden state is read through [isHidden] on every bind instead of being
 * snapshotted at construction: the user can flip a switch, pick a widget
 * from the "add widget" sheet, or come back from the grid, and a rebind
 * must reflect the store, not a stale copy — otherwise the switch visibly
 * bounces back to the value it started with.
 */
class DashboardEditAdapter(
    private val context: Context,
    private val isHidden: (String) -> Boolean,
    private val onHiddenChanged: (String, Boolean) -> Unit,
) : RecyclerView.Adapter<DashboardEditAdapter.Holder>() {

    /** Mutable on purpose: [ItemTouchHelper] reorders this list in place. */
    val items: MutableList<DashboardWidget> = mutableListOf()

    fun submit(widgets: List<DashboardWidget>) {
        items.clear()
        items.addAll(widgets)

        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            AdapterDashboardEditRowBinding.inflate(
                context.layoutInflater,
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val widget = items[position]

        holder.binding.icon.setImageResource(widget.iconRes)
        holder.binding.title.text = context.getString(widget.titleRes)

        holder.binding.visibility.setOnCheckedChangeListener(null)
        holder.binding.visibility.isChecked = !isHidden(widget.key)
        holder.binding.visibility.setOnCheckedChangeListener { _, checked ->
            onHiddenChanged(widget.key, !checked)
        }
    }

    class Holder(val binding: AdapterDashboardEditRowBinding) :
        RecyclerView.ViewHolder(binding.root)
}

/**
 * The "add widget" sheet: every placeable widget that is not currently on
 * the dashboard — that is, every hidden one, since the order list always
 * covers the full catalogue. Picking a row unhides the widget.
 */
class DashboardWidgetPickerAdapter(
    private val context: Context,
    private val onPick: (DashboardWidget) -> Unit,
) : RecyclerView.Adapter<DashboardWidgetPickerAdapter.Holder>() {

    val items: MutableList<DashboardWidget> = mutableListOf()

    fun submit(widgets: List<DashboardWidget>) {
        items.clear()
        items.addAll(widgets)

        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            AdapterDashboardWidgetPickerBinding.inflate(
                context.layoutInflater,
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val widget = items[position]

        holder.binding.row.text = context.getString(widget.titleRes)
        holder.binding.row.icon = ContextCompat.getDrawable(context, widget.iconRes)

        holder.binding.row.setOnClickListener {
            val index = holder.bindingAdapterPosition

            if (index == RecyclerView.NO_POSITION)
                return@setOnClickListener

            onPick(items[index])
        }
    }

    class Holder(val binding: AdapterDashboardWidgetPickerBinding) :
        RecyclerView.ViewHolder(binding.root)
}
