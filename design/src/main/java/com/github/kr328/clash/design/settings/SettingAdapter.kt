package com.github.kr328.clash.design.settings

import androidx.annotation.DrawableRes
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.AdapterSettingGroupBinding
import com.github.kr328.clash.design.databinding.AdapterSettingRowBinding
import com.github.kr328.clash.design.databinding.AdapterSettingSwitchBinding
import com.github.kr328.clash.design.databinding.AdapterSettingTipsBinding
import com.github.kr328.clash.design.util.getHtml
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.view.AppRow
import com.google.android.material.R as MaterialR

/**
 * Renders a settings page: one item per [SettingItem]. A [SettingGroup] is an
 * `adapter_setting_group.xml` card whose rows are inflated from
 * `adapter_setting_row.xml` (an AppRow) or `adapter_setting_switch.xml`.
 *
 * This replaces the runtime `LinearLayout` that `preference/Screen.kt` used to
 * build: every pixel of every row now comes from an XML layout.
 */
class SettingAdapter(private val context: Context) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class GroupHolder(val binding: AdapterSettingGroupBinding) :
        RecyclerView.ViewHolder(binding.root)

    class TipsHolder(val binding: AdapterSettingTipsBinding) :
        RecyclerView.ViewHolder(binding.root)

    var items: List<SettingItem> = emptyList()
        private set

    /** Replace the whole page. Items are diffed by [SettingItem.contentKey]. */
    fun submitItems(newItems: List<SettingItem>) {
        val old = items

        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size

            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return old[oldItemPosition].identityKey == newItems[newItemPosition].identityKey
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return old[oldItemPosition].contentKey == newItems[newItemPosition].contentKey
            }
        })

        items = newItems

        result.dispatchUpdatesTo(this)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SettingGroup -> TYPE_GROUP
            is SettingTips -> TYPE_TIPS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TIPS -> TipsHolder(
                AdapterSettingTipsBinding.inflate(context.layoutInflater, parent, false)
            )

            else -> GroupHolder(
                AdapterSettingGroupBinding.inflate(context.layoutInflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SettingGroup -> bindGroup(holder as GroupHolder, item)
            is SettingTips -> (holder as TipsHolder).binding.tips.text = context.getHtml(item.text)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    private fun bindGroup(holder: GroupHolder, group: SettingGroup) {
        val binding = holder.binding

        if (group.title != 0) {
            binding.titleView.text = context.getText(group.title)
            binding.titleView.visibility = View.VISIBLE
        } else {
            binding.titleView.text = null
            binding.titleView.visibility = View.GONE
        }

        val container = binding.rowsContainer

        container.removeAllViews()

        val inflater = context.layoutInflater

        for (row in group.rows) {
            when (row) {
                is SettingRow.Switch -> {
                    val rowBinding =
                        AdapterSettingSwitchBinding.inflate(inflater, container, false)

                    bindSwitch(rowBinding, row)

                    container.addView(rowBinding.root)
                }

                is SettingRow.Action -> {
                    val rowBinding =
                        AdapterSettingRowBinding.inflate(inflater, container, false)

                    bindAction(rowBinding.rowView, row)

                    container.addView(rowBinding.root)
                }
            }
        }
    }

    private fun bindSwitch(binding: AdapterSettingSwitchBinding, row: SettingRow.Switch) {
        binding.textView.text = context.getText(row.title)

        binding.subtextView.text = row.summary
        binding.subtextView.visibility = if (row.summary.isNullOrEmpty()) View.GONE else View.VISIBLE

        binding.iconView.bindIcon(row.icon)
        binding.iconContainer.visibility = if (row.icon != null) View.VISIBLE else View.GONE

        binding.switchView.isChecked = row.checked

        binding.root.isEnabled = row.enabled
        binding.root.alpha = if (row.enabled) 1.0f else DISABLED_ALPHA

        binding.root.setOnClickListener {
            if (row.enabled)
                row.onToggle(!row.checked)
        }
    }

    private fun bindAction(row: AppRow, item: SettingRow.Action) {
        row.text = context.getText(item.title)
        row.subtext = item.summary
        row.icon = context.loadDrawable(item.icon)
        row.iconContainerVisible = item.icon != null
        row.trailingIcon = context.loadDrawable(
            if (item.showChevron) R.drawable.ic_settings_chevron_right else null
        )

        row.isEnabled = item.enabled
        row.alpha = if (item.enabled) 1.0f else DISABLED_ALPHA

        row.setOnClickListener {
            if (item.enabled)
                item.onClick(row)
        }
    }

    /**
     * Icons are ImageViews tinted from the theme, never a bare `<View
     * android:background="@drawable/ic_*"/>`. Every vector in this module bakes
     * in `android:tint="?attr/colorControlNormal"`, so the on-surface tint is
     * applied here — the same rule AppRow follows.
     */
    private fun ImageView.bindIcon(@DrawableRes resId: Int?) {
        setImageDrawable(context.loadDrawable(resId))

        imageTintList = ColorStateList.valueOf(
            context.resolveThemedColor(MaterialR.attr.colorOnSurfaceVariant)
        )
    }

    private fun Context.loadDrawable(@DrawableRes resId: Int?): Drawable? {
        if (resId == null)
            return null

        return ContextCompat.getDrawable(this, resId)
    }

    companion object {
        private const val DISABLED_ALPHA = 0.33f

        private const val TYPE_GROUP = 0
        private const val TYPE_TIPS = 1
    }
}
