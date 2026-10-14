package com.github.kr328.clash.design.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.AdapterSettingEntryBinding
import com.github.kr328.clash.design.databinding.DialogSettingListBinding
import com.github.kr328.clash.design.dialog.FullScreenDialog
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * One reorderable entry inside a settings editor.
 *
 * Dashboard widgets use [Sized] (large / small / hidden); probe services use
 * [Enabled] (probed or not). Both share the same reorder affordances, so they
 * share one row layout.
 */
sealed class SettingEntry {
    abstract val key: String
    abstract val title: CharSequence
    abstract val summary: CharSequence?

    class Sized(
        override val key: String,
        override val title: CharSequence,
        override val summary: CharSequence? = null,
        var size: WidgetSize,
    ) : SettingEntry()

    class Enabled(
        override val key: String,
        override val title: CharSequence,
        override val summary: CharSequence? = null,
        var checked: Boolean,
    ) : SettingEntry()
}

/**
 * Dashboard widgets, in default order. The keys are stable strings rather than
 * view ids so the dashboard can map onto them without depending on this
 * package.
 */
object DashboardWidgets {
    data class Widget(val key: String, val titleRes: Int)

    val ALL = listOf(
        Widget("outbound_mode", R.string.outbound_mode),
        Widget("proxy", R.string.proxy),
        Widget("network_quality", R.string.network_quality),
        Widget("connections", R.string.connections),
        Widget("ip_info", R.string.ip_info),
        Widget("profile", R.string.profile),
    )

    val keys: List<String> = ALL.map { it.key }
}

/**
 * Opens the full-screen editor for an ordered [SettingEntry] list. Returns the
 * edited list, or `null` when the user resets it. A cancelled dialog returns
 * the list unchanged.
 */
suspend fun Context.editSettingEntries(
    title: CharSequence,
    initial: List<SettingEntry>,
): List<SettingEntry>? {
    return suspendCancellableCoroutine { ctx ->
        val entries = initial.map { it.copy() }.toMutableList()

        val dialog = FullScreenDialog(this)

        val binding = DialogSettingListBinding.inflate(layoutInflater, root, false)

        val adapter = SettingEntryAdapter(this, entries)

        binding.surface = dialog.surface
        binding.titleView.text = title
        binding.newView.visibility = View.GONE
        binding.resetView.setOnClickListener {
            ctx.resume(null)

            dialog.dismiss()
        }
        binding.mainList.applyLinearAdapter(this, adapter)

        binding.okView.setOnClickListener {
            ctx.resume(entries.map { it.copy() })

            dialog.dismiss()
        }

        binding.cancelView.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(binding.root)

        dialog.setOnDismissListener {
            if (!ctx.isCompleted)
                ctx.resume(initial)
        }

        ctx.invokeOnCancellation {
            dialog.dismiss()
        }

        dialog.show()
    }
}

private fun SettingEntry.copy(): SettingEntry = when (this) {
    is SettingEntry.Sized -> SettingEntry.Sized(key, title, summary, size)
    is SettingEntry.Enabled -> SettingEntry.Enabled(key, title, summary, checked)
}

private class SettingEntryAdapter(
    private val context: Context,
    private val entries: MutableList<SettingEntry>,
) : RecyclerView.Adapter<SettingEntryAdapter.Holder>() {

    class Holder(val binding: AdapterSettingEntryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            AdapterSettingEntryBinding.inflate(
                LayoutInflater.from(context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = entries[position]
        val binding = holder.binding

        binding.titleView.text = entry.title

        binding.summaryView.text = entry.summary
        binding.summaryView.visibility =
            if (entry.summary.isNullOrEmpty()) View.GONE else View.VISIBLE

        // check() reports a change, so the programmatic re-check on rebind has
        // to be distinguishable from a tap.
        var updating = false

        when (entry) {
            is SettingEntry.Sized -> {
                binding.sizeGroup.visibility = View.VISIBLE
                binding.switchView.visibility = View.GONE

                binding.sizeGroup.clearOnButtonCheckedListeners()

                updating = true

                binding.sizeGroup.check(
                    when (entry.size) {
                        WidgetSize.Large -> binding.sizeLargeView.id
                        WidgetSize.Small -> binding.sizeSmallView.id
                        WidgetSize.Hidden -> binding.sizeHiddenView.id
                    }
                )

                updating = false

                binding.sizeGroup.addOnButtonCheckedListener { _, id, isChecked ->
                    if (updating || !isChecked)
                        return@addOnButtonCheckedListener

                    entry.size = when (id) {
                        binding.sizeSmallView.id -> WidgetSize.Small
                        binding.sizeHiddenView.id -> WidgetSize.Hidden
                        else -> WidgetSize.Large
                    }
                }

                binding.root.setOnClickListener(null)
            }

            is SettingEntry.Enabled -> {
                binding.sizeGroup.visibility = View.GONE
                binding.sizeGroup.clearOnButtonCheckedListeners()
                binding.switchView.visibility = View.VISIBLE
                binding.switchView.isChecked = entry.checked

                binding.root.setOnClickListener {
                    entry.checked = !entry.checked

                    val index = holder.bindingAdapterPosition

                    if (index != RecyclerView.NO_POSITION)
                        notifyItemChanged(index)
                }
            }
        }

        binding.moveUpView.isEnabled = position > 0
        binding.moveUpView.alpha = if (position > 0) 1.0f else 0.33f
        binding.moveUpView.setOnClickListener {
            val index = holder.bindingAdapterPosition

            if (index > 0) {
                entries.add(index - 1, entries.removeAt(index))

                notifyItemMoved(index, index - 1)
            }
        }

        binding.moveDownView.isEnabled = position < entries.size - 1
        binding.moveDownView.alpha = if (position < entries.size - 1) 1.0f else 0.33f
        binding.moveDownView.setOnClickListener {
            val index = holder.bindingAdapterPosition

            if (index in 0 until entries.size - 1) {
                entries.add(index + 1, entries.removeAt(index))

                notifyItemMoved(index, index + 1)
            }
        }
    }

    override fun getItemCount(): Int {
        return entries.size
    }
}
