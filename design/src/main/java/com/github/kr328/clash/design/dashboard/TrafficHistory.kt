package com.github.kr328.clash.design.dashboard

import androidx.annotation.StringRes
import com.github.kr328.clash.design.R

/**
 * A fixed-capacity ring buffer of upload / download samples.
 *
 * The network speed chart used to hold its own 60 samples inside the View,
 * which meant the history died every time the ViewHolder was recycled and the
 * chart reset to a flat line on every scroll. History therefore lives here,
 * owned by the screen rather than by a View.
 *
 * One sample is pushed per dashboard tick (currently one second), so
 * [CAPACITY] covers the longest selectable window.
 */
class TrafficHistory(val capacity: Int = CAPACITY) {
    private val uploads = LongArray(capacity)
    private val downloads = LongArray(capacity)

    /** Index of the oldest sample. */
    private var head = 0

    /** Number of valid samples, at most [capacity]. */
    private var count = 0

    /** Bumped on every mutation so views know when to rebuild their buffers. */
    var version: Int = 0
        private set

    /** The window the chart is currently showing. */
    var window: Window = Window.MINUTE_1

    fun push(upload: Long, download: Long) {
        val index = (head + count) % capacity

        uploads[index] = upload
        downloads[index] = download

        if (count < capacity)
            count++
        else
            head = (head + 1) % capacity

        version++
    }

    /** Number of samples currently held. */
    val size: Int
        get() = count

    /** Selectable display windows. */
    enum class Window(val samples: Int, @StringRes val labelRes: Int) {
        MINUTE_1(60, R.string.dashboard_window_1m),
        MINUTE_5(300, R.string.dashboard_window_5m),
        MINUTE_30(1800, R.string.dashboard_window_30m),
    }

    /**
     * Copy at most [window].samples trailing samples into [outUpload] /
     * [outDownload], oldest first. Returns the number of samples written, or
     * -1 when [outUpload] is too small (the caller should reallocate).
     */
    fun read(window: Window, outUpload: LongArray, outDownload: LongArray): Int {
        val n = count.coerceAtMost(window.samples)

        if (outUpload.size < n)
            return -1

        val start = (head + count - n) % capacity

        for (i in 0 until n) {
            val index = (start + i) % capacity

            outUpload[i] = uploads[index]
            outDownload[i] = downloads[index]
        }

        return n
    }

    companion object {
        /** One sample per second for 30 minutes. */
        const val CAPACITY = 1800
    }
}