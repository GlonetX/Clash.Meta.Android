package com.github.kr328.clash.design

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.databinding.Observable
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.BR
import com.github.kr328.clash.design.adapter.LogMessageAdapter
import com.github.kr328.clash.design.databinding.DesignLogcatBinding
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogcatDesign(
    context: Context,
    private val streaming: Boolean,
    initialLevel: LogMessage.Level = LogMessage.Level.Debug,
) : Design<LogcatDesign.Request>(context) {
    sealed class Request {
        object Close : Request()
        object Delete : Request()
        object Export : Request()

        /** Patches the core's session log-level override (streaming only). */
        data class SetLevel(val level: LogMessage.Level) : Request()
    }

    private val binding = DesignLogcatBinding
        .inflate(context.layoutInflater, context.root, false)
    private val adapter = LogMessageAdapter(context) {
        launch {
            val data = ClipData.newPlainText("log_message", it.message)

            context.getSystemService<ClipboardManager>()?.setPrimaryClip(data)

            showToast(R.string.copied, ToastDuration.Short)
        }
    }

    /**
     * The full message list from the core logcat cache (streaming) or the
     * log file. The adapter only ever sees the portion selected by
     * [clearMarker], [levelThreshold] and [query].
     */
    private var allMessages: List<LogMessage> = emptyList()

    /** Index in [allMessages] after which messages are visible. */
    private var clearMarker: Int = 0

    /** Messages with a lower level are hidden. Debug shows everything. */
    private var levelThreshold: LogMessage.Level = initialLevel

    /** Case-insensitive substring filter over the message text. */
    private var query: String = ""

    private var firstPatch: Boolean = true
    private var searchJob: Job? = null

    override val root: View
        get() = binding.root

    init {
        binding.self = this
        binding.streaming = streaming

        binding.activityBarLayout.applyFrom(context)

        binding.recyclerList.bindAppBarElevation(binding.activityBarLayout)

        binding.recyclerList.layoutManager = LinearLayoutManager(context).apply {
            if (streaming) {
                reverseLayout = true
                stackFromEnd = true
            }
        }
        binding.recyclerList.adapter = adapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Debounced so typing in a large log file does not rebind
                // the list on every keystroke.
                searchJob?.cancel()
                searchJob = launch(Dispatchers.Main) {
                    delay(SEARCH_DEBOUNCE_MS)
                    query = s?.toString()?.trim().orEmpty()
                    applyFilters()
                }
            }
        })

        surface.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                if (propertyId == BR.insets) {
                    applyListPadding()
                }
            }
        })

        applyListPadding()
    }

    suspend fun patchMessages(messages: List<LogMessage>, removed: Int, appended: Int) {
        withContext(Dispatchers.Main) {
            // Messages evicted from the cache shift the clear marker.
            if (removed > 0)
                clearMarker = (clearMarker - removed).coerceAtLeast(0)

            allMessages = messages

            if (firstPatch || isFiltering()) {
                firstPatch = false
                applyFilters()
            } else {
                adapter.messages = messages

                if (removed > 0)
                    adapter.notifyItemRangeRemoved(0, removed)
                if (appended > 0)
                    adapter.notifyItemRangeInserted(messages.size - appended, appended)
            }

            if (streaming && binding.recyclerList.isTop && adapter.itemCount > 0) {
                binding.recyclerList.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    /**
     * Opens the log level selector. In streaming mode the chosen level is
     * also pushed to the core as a *session* override (Request.SetLevel —
     * see LogcatActivity), so the core stops *emitting* lower-severity
     * lines instead of the viewer merely hiding them. No config file is
     * touched; the override dies with the core process. On top of that,
     * the viewer keeps its own threshold so lines already sitting in the
     * 128-entry cache are hidden immediately.
     */
    fun showLevelSelector() {
        val levels = arrayOf(
            LogMessage.Level.Debug,
            LogMessage.Level.Info,
            LogMessage.Level.Warning,
            LogMessage.Level.Error,
            LogMessage.Level.Silent,
        )
        val names = levels.map { it.localized(context) }.toTypedArray()
        val selected = levels.indexOf(levelThreshold).coerceAtLeast(0)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.log_level)
            .setSingleChoiceItems(names, selected) { dialog, which ->
                levelThreshold = levels[which]

                dialog.dismiss()

                applyFilters()

                if (streaming) {
                    requests.trySend(Request.SetLevel(levels[which]))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun toggleSearch() {
        if (binding.searchBar.visibility == View.VISIBLE) {
            closeSearch()
        } else {
            binding.searchBar.visibility = View.VISIBLE
            applyListPadding()

            binding.searchInput.requestFocus()

            context.getSystemService<InputMethodManager>()?.showSoftInput(
                binding.searchInput,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    fun closeSearch() {
        binding.searchBar.visibility = View.GONE
        applyListPadding()

        binding.searchInput.clearFocus()

        context.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(
            binding.searchInput.windowToken,
            0
        )

        if (query.isNotEmpty()) {
            query = ""
            applyFilters()
        }
    }

    /** Clears the search text (the X button in the search bar). */
    fun clearSearchQuery() {
        binding.searchInput.setText("")
        binding.searchInput.requestFocus()
    }

    /** Clears the visible messages; newly streamed messages keep arriving. */
    fun clearScreen() {
        clearMarker = allMessages.size
        applyFilters()
    }

    private fun isFiltering(): Boolean {
        return clearMarker > 0 || levelThreshold != LogMessage.Level.Debug || query.isNotEmpty()
    }

    private fun applyFilters() {
        val source = allMessages
        val start = clearMarker
        val threshold = levelThreshold
        val q = query

        val displayed = if (start == 0 && threshold == LogMessage.Level.Debug && q.isEmpty()) {
            source
        } else {
            source.asSequence()
                .drop(start)
                .filter { it.level.ordinal >= threshold.ordinal }
                .filter { q.isEmpty() || it.message.contains(q, ignoreCase = true) }
                .toList()
        }

        adapter.messages = displayed
        adapter.notifyDataSetChanged()

        if (streaming && binding.recyclerList.isTop && displayed.isNotEmpty()) {
            binding.recyclerList.scrollToPosition(displayed.size - 1)
        }
    }

    private fun applyListPadding() {
        val top = surface.insets.top +
                context.getPixels(R.dimen.toolbar_height) +
                if (binding.searchBar.visibility == View.VISIBLE) {
                    context.getPixels(R.dimen.toolbar_height)
                } else {
                    0
                }
        val bottom = surface.insets.bottom

        binding.recyclerList.setPaddingRelative(0, top, 0, bottom)
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L

        /** Localized level names for the selector dialog. */
        private fun LogMessage.Level.localized(context: Context): String {
            val res = when (this) {
                LogMessage.Level.Debug -> R.string.debug
                LogMessage.Level.Info -> R.string.info
                LogMessage.Level.Warning -> R.string.warning
                LogMessage.Level.Error -> R.string.error
                LogMessage.Level.Silent -> R.string.silent
                LogMessage.Level.Unknown -> R.string.debug
            }

            return context.getString(res)
        }
    }
}
