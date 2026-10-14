package com.github.kr328.clash.design.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.AdapterKvItemBinding
import com.github.kr328.clash.design.databinding.AdapterSectionHeaderBinding
import com.github.kr328.clash.design.util.resolveThemedColor

class KvAdapter(
    context: Context,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    sealed class Entry {
        data class Header(val title: String) : Entry()
        data class Item(val item: com.github.kr328.clash.design.model.KvItem) : Entry()
    }

    private val inflater = LayoutInflater.from(context)
    private val entries = mutableListOf<Entry>()

    fun submit(list: List<Entry>) {
        entries.clear()
        entries.addAll(list)

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (entries[position]) {
            is Entry.Header -> TYPE_HEADER
            is Entry.Item -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(
                AdapterSectionHeaderBinding.inflate(inflater, parent, false),
            )
            else -> ItemHolder(
                AdapterKvItemBinding.inflate(inflater, parent, false),
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = entries[position]) {
            is Entry.Header -> (holder as HeaderHolder).bind(entry)
            is Entry.Item -> (holder as ItemHolder).bind(entry.item)
        }
    }

    override fun getItemCount(): Int {
        return entries.size
    }

    class HeaderHolder(private val binding: AdapterSectionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: Entry.Header) {
            binding.title = entry.title
        }
    }

    class ItemHolder(private val binding: AdapterKvItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: com.github.kr328.clash.design.model.KvItem) {
            binding.value = item

            binding.valueView.apply {
                if (item.highlight) {
                    setTextAppearance(context, R.style.TextAppearance_Material3_TitleMedium)
                    setTextColor(context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary))
                } else {
                    setTextAppearance(context, R.style.TextAppearance_Material3_BodyLarge)
                    setTextColor(context.resolveThemedColor(android.R.attr.textColor))
                }
            }
        }
    }
}
