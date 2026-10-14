package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.databinding.AdapterConnectionBinding
import com.github.kr328.clash.design.databinding.AdapterConnectionGroupBinding
import com.github.kr328.clash.design.model.ConnectionRow
import com.github.kr328.clash.design.model.ConnectionRow.Group
import com.github.kr328.clash.design.model.ConnectionRow.Item
import com.github.kr328.clash.design.util.layoutInflater

/**
 * Renders the connections list.
 *
 * The whole screen polls every two seconds and every counter changes on every
 * poll, so `DiffUtil` would report "everything changed" anyway; the list is
 * replaced wholesale with [rows] instead. That also keeps the two view types
 * and the collapsed-group bookkeeping in one place.
 */
class ConnectionAdapter(
    private val context: Context,
    private val clickItem: (Item) -> Unit,
    private val toggleGroup: (Group) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private class ItemHolder(val binding: AdapterConnectionBinding) :
        RecyclerView.ViewHolder(binding.root)

    private class GroupHolder(val binding: AdapterConnectionGroupBinding) :
        RecyclerView.ViewHolder(binding.root)

    var rows: List<ConnectionRow> = emptyList()
        set(value) {
            field = value

            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is Group -> TYPE_GROUP
            is Item -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = context.layoutInflater

        return when (viewType) {
            TYPE_GROUP -> GroupHolder(
                AdapterConnectionGroupBinding.inflate(inflater, parent, false)
            )
            else -> ItemHolder(
                AdapterConnectionBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Group -> {
                val group = holder as GroupHolder

                group.binding.group = row

                // rotation is a layout concern the model must not own
                group.binding.chevronView.rotation = if (row.collapsed) 0f else 90f

                group.binding.root.setOnClickListener {
                    toggleGroup(row)
                }
            }
            is Item -> {
                val item = holder as ItemHolder

                item.binding.connection = row.item
                item.binding.root.alpha = if (row.collapsed) 0.4f else 1.0f

                item.binding.root.setOnClickListener {
                    clickItem(row)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return rows.size
    }

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_GROUP = 1
    }
}