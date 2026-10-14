package com.github.kr328.clash.design.dashboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.databinding.AdapterDashboardEditRowBinding
import com.github.kr328.clash.design.databinding.AdapterDashboardWidgetPickerBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.R as MaterialR

/**
 * The reorderable list behind "Customise dashboard".
 *
 * Dragging is handled by an [androidx.recyclerview.widget.ItemTouchHelper]
 * attached by the caller; this adapter only owns the mutable item list, the
 * hide/show switches and the persistence hook.
 */
class DashboardEditAdapter(
    private val context: Context,
    private val hidden: Set<String>,
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

        holder.binding.title.text = context.getString(widget.titleRes)
        holder.binding.dragHandle.setImageResource(widget.iconRes)

        holder.binding.visibility.setOnCheckedChangeListener(null)
        holder.binding.visibility.isChecked = widget.key !in hidden
        holder.binding.visibility.setOnCheckedChangeListener { _, checked ->
            onHiddenChanged(widget.key, !checked)
        }
    }

    class Holder(val binding: AdapterDashboardEditRowBinding) :
        RecyclerView.ViewHolder(binding.root)
}

/**
 * The "add widget" sheet: every placeable widget that is not currently on the
 * dashboard.
 */
class DashboardWidgetPickerAdapter(
    private val context: Context,
    private val onPick: (DashboardWidget) -> Unit,
) : RecyclerView.Adapter<DashboardWidgetPickerAdapter.Holder>() {

    val items: MutableList<DashboardWidget> = mutableListOf()

    private val tint: Int = context.resolveThemedColor(MaterialR.attr.colorOnSurfaceVariant)

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

        holder.binding.title.text = context.getString(widget.titleRes)
        holder.binding.icon.setImageResource(widget.iconRes)
        holder.binding.icon.imageTintList = android.content.res.ColorStateList.valueOf(tint)

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