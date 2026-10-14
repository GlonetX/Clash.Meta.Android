package com.github.kr328.clash.design.settings

import android.content.Context
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.adapter.EditableTextListAdapter
import com.github.kr328.clash.design.adapter.EditableTextMapAdapter
import com.github.kr328.clash.design.adapter.PopupListAdapter
import com.github.kr328.clash.design.databinding.DialogEditableMapTextFieldBinding
import com.github.kr328.clash.design.databinding.DialogSettingListBinding
import com.github.kr328.clash.design.databinding.DialogTextFieldBinding
import com.github.kr328.clash.design.dialog.FullScreenDialog
import com.github.kr328.clash.design.util.Validator
import com.github.kr328.clash.design.util.ValidatorAcceptAll
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.measureWidth
import com.github.kr328.clash.design.util.requestTextInput
import com.github.kr328.clash.design.util.root
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Converts one value to and from the text a row shows.
 *
 * Moved here from the retired `preference` package so the reusable list
 * editors keep a home for it.
 */
interface NullableTextAdapter<T> {
    fun from(value: T): String?
    fun to(text: String?): T

    companion object {
        val Port = object : NullableTextAdapter<Int?> {
            override fun from(value: Int?): String? {
                if (value == null) return null

                return if (value > 0) value.toString() else ""
            }

            override fun to(text: String?): Int? {
                if (text == null) return null

                return text.toIntOrNull() ?: 0
            }
        }

        val String = object : NullableTextAdapter<String?> {
            override fun from(value: String?): String? = value

            override fun to(text: String?): String? = text
        }
    }
}

/** Same, for a list element that has no empty state. */
interface TextAdapter<T> {
    fun from(value: T): String
    fun to(text: String): T

    companion object {
        val String = object : TextAdapter<String> {
            override fun from(value: String): String = value

            override fun to(text: String): String = text
        }
    }
}

/**
 * Shows [texts] in a popup anchored to [anchor] and returns the chosen index,
 * or [selected] when the popup is dismissed.
 */
suspend fun Context.selectSetting(
    anchor: View,
    texts: List<CharSequence>,
    selected: Int,
): Int = suspendCancellableCoroutine { ctx ->
    ListPopupWindow(this).apply {
        val adapter = PopupListAdapter(this@selectSetting, texts, selected)

        setAdapter(adapter)

        anchorView = anchor

        width = adapter.measureWidth(this@selectSetting)
            .coerceAtLeast(getPixels(R.dimen.dialog_menu_min_width))

        isModal = true

        setOnItemClickListener { _, _, position, _ ->
            dismiss()

            ctx.resume(position)
        }

        setOnDismissListener {
            if (!ctx.isCompleted)
                ctx.resume(selected)
        }

        ctx.invokeOnCancellation {
            dismiss()
        }

        show()
    }
}

/**
 * Opens a single-value text editor.
 *
 * Returns the edited text, or `null` when the user picks "Reset" or dismisses
 * the prompt. Callers therefore compare against the current value to tell
 * "unchanged" apart from "cleared".
 *
 * This is a local copy of `design.dialog.requestModelTextInput` because
 * credentials need an [inputType], which that helper does not expose.
 */
suspend fun Context.editSettingText(
    initial: String?,
    title: CharSequence,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    allowReset: Boolean = true,
    inputType: Int? = null,
    validator: Validator = ValidatorAcceptAll,
): String? {
    return suspendCancellableCoroutine { ctx ->
        val binding = DialogTextFieldBinding.inflate(layoutInflater, root, false)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(binding.root)
            .setCancelable(true)
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = binding.textField.text?.toString() ?: ""

                ctx.resume(if (validator(text)) text else initial)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .setOnDismissListener { _ ->
                if (!ctx.isCompleted)
                    ctx.resume(initial)
            }

        if (allowReset) {
            builder.setNeutralButton(R.string.reset) { _, _ ->
                ctx.resume(null)
            }
        }

        val dialog = builder.create()

        ctx.invokeOnCancellation {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            if (hint != null)
                binding.textLayout.hint = hint

            binding.textField.apply {
                if (inputType != null)
                    this.inputType = inputType

                binding.textLayout.isErrorEnabled = error != null

                doOnTextChanged { text, _, _, _ ->
                    if (!validator(text?.toString() ?: "")) {
                        if (error != null)
                            binding.textLayout.error = error

                        dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
                    } else {
                        if (error != null)
                            binding.textLayout.error = null

                        dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                    }
                }

                setText(initial)

                setSelection(0, initial?.length ?: 0)

                requestTextInput()
            }
        }

        dialog.show()
    }
}

/**
 * Opens the full-screen list editor. Returns `null` on Reset; a cancelled
 * editor returns the list unchanged.
 */
suspend fun <T> Context.editSettingTextList(
    initial: List<T>?,
    title: CharSequence,
    adapter: TextAdapter<T>,
    newItemText: suspend () -> String?,
): List<T>? {
    val editable = initial?.map { it }?.toMutableList() ?: mutableListOf()

    val recyclerAdapter = EditableTextListAdapter(this, editable, adapter)

    val result = requestSettingListOverlay(title, recyclerAdapter) {
        val text = newItemText()

        if (!text.isNullOrBlank())
            recyclerAdapter.addElement(text)
    }

    return when (result) {
        SettingListResult.Cancel -> initial
        SettingListResult.Apply -> recyclerAdapter.values
        SettingListResult.Reset -> null
    }
}

/** Map flavour of [editSettingTextList]. */
suspend fun <K, V> Context.editSettingTextMap(
    initial: Map<K, V>?,
    title: CharSequence,
    keyAdapter: TextAdapter<K>,
    valueAdapter: TextAdapter<V>,
): Map<K, V>? {
    val editable = initial?.map { it.key to it.value }?.toMutableList() ?: mutableListOf()

    val recyclerAdapter = EditableTextMapAdapter(this, editable, keyAdapter, valueAdapter)

    val result = requestSettingListOverlay(title, recyclerAdapter) {
        val entry = requestSettingMapEntry(title)

        if (entry != null)
            recyclerAdapter.addElement(entry.first, entry.second)
    }

    return when (result) {
        SettingListResult.Cancel -> initial
        SettingListResult.Apply -> recyclerAdapter.values.toMap()
        SettingListResult.Reset -> null
    }
}

/**
 * Asks the user to pick one of [labels], returning the matching element of
 * [values], or `null` when the dialog is dismissed.
 */
suspend fun <T> Context.chooseSettingOption(
    title: CharSequence,
    labels: List<CharSequence>,
    values: List<T>,
): T? {
    if (labels.size != values.size)
        throw IllegalArgumentException("labels and values must have the same size")

    return suspendCancellableCoroutine { ctx ->
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, index -> ctx.resume(values[index]) }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .create()

        dialog.setOnDismissListener {
            if (!ctx.isCompleted)
                ctx.resume(null)
        }

        ctx.invokeOnCancellation {
            dialog.dismiss()
        }

        dialog.show()
    }
}

/** Asks a yes/no question. Returns false when dismissed. */
suspend fun Context.confirmSetting(
    title: CharSequence,
    message: CharSequence,
    confirmText: CharSequence = getText(R.string.ok),
): Boolean = suspendCancellableCoroutine { ctx ->
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(confirmText) { _, _ -> ctx.resume(true) }
        .setNegativeButton(R.string.cancel) { _, _ -> }
        .create()

    dialog.setOnDismissListener {
        if (!ctx.isCompleted)
            ctx.resume(false)
    }

    ctx.invokeOnCancellation {
        dialog.dismiss()
    }

    dialog.show()
}

private enum class SettingListResult { Cancel, Apply, Reset }

private suspend fun Context.requestSettingListOverlay(
    title: CharSequence,
    adapter: RecyclerView.Adapter<*>,
    addNewItem: suspend () -> Unit,
): SettingListResult = suspendCancellableCoroutine { ctx ->
    val dialog = FullScreenDialog(this)

    val binding = DialogSettingListBinding.inflate(layoutInflater, root, false)

    binding.surface = dialog.surface
    binding.titleView.text = title
    binding.mainList.applyLinearAdapter(this, adapter)

    binding.newView.setOnClickListener {
        launch {
            addNewItem()
        }
    }

    binding.okView.setOnClickListener {
        ctx.resume(SettingListResult.Apply)

        dialog.dismiss()
    }

    binding.cancelView.setOnClickListener {
        dialog.dismiss()
    }

    binding.resetView.setOnClickListener {
        ctx.resume(SettingListResult.Reset)

        dialog.dismiss()
    }

    dialog.setContentView(binding.root)

    dialog.setOnDismissListener {
        if (!ctx.isCompleted)
            ctx.resume(SettingListResult.Cancel)
    }

    ctx.invokeOnCancellation {
        dialog.dismiss()
    }

    dialog.show()
}

private suspend fun Context.requestSettingMapEntry(
    title: CharSequence
): Pair<String, String>? {
    return suspendCancellableCoroutine { ctx ->
        val binding = DialogEditableMapTextFieldBinding
            .inflate(layoutInflater, root, false)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .setPositiveButton(R.string.ok) { _, _ ->
                val key = binding.keyView.text?.toString()?.trim() ?: ""
                val value = binding.valueView.text?.toString()?.trim() ?: ""

                if (key.isNotEmpty() && value.isNotEmpty())
                    ctx.resume(key to value)
            }
            .setView(binding.root)
            .create()

        dialog.setOnCancelListener {
            if (!ctx.isCompleted)
                ctx.resume(null)
        }

        dialog.show()
    }
}
