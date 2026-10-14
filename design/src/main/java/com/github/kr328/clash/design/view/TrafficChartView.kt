package com.github.kr328.clash.design.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import androidx.annotation.AttrRes
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dashboard.TrafficHistory
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.toBytesString
import com.google.android.material.R as MaterialR

/**
 * Upload / download speed over time.
 *
 * Differences from the version this replaces:
 *
 * - **History survives recycling.** Samples live in a shared
 *   [TrafficHistory] owned by the screen, not in the View, so scrolling the
 *   grid no longer throws the series away.
 * - **Selectable window.** 1 / 5 / 30 minutes, drawn from the same ring
 *   buffer and downsampled to at most one point per pixel column.
 * - **Axes.** Faint horizontal gridlines plus a scale label, so the curve is
 *   readable instead of decorative.
 * - **Peak marker.** The highest point of the combined series is called out.
 * - **A download colour that does not read as "the app is on".** It used to be
 *   `colorPrimary`, which is also the running-status card's background; the
 *   two collided. Download is now `colorTertiary` (green), upload
 *   `delayMedium` (orange).
 */
class TrafficChartView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
) : android.view.View(context, attributeSet, defStyleAttr) {

    /** The shared sample buffer. Set by the screen before the view is drawn. */
    var history: TrafficHistory? = null
        set(value) {
            field = value
            reset()
            postInvalidateOnAnimation()
        }

    private val density = context.resources.displayMetrics.density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 1.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = density * 10f
    }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val uploadPath = Path()
    private val downloadPath = Path()

    private var uploads = LongArray(0)
    private var downloads = LongArray(0)
    private var samples = 0

    private var cachedVersion = -1
    private var cachedWindow: TrafficHistory.Window? = null

    // `colorSecondary` is aliased to `colorPrimary` in every theme this app
    // ships, so it cannot be used here. `colorTertiary` (green) and
    // `delayMedium` (orange) are the only two accents guaranteed to differ
    // from the running-status card.
    private val downloadColor: Int = context.resolveThemedColor(MaterialR.attr.colorTertiary)
    private val uploadColor: Int = context.resolveThemedColor(R.attr.delayMedium)
    private val gridColor: Int = context.resolveThemedColor(MaterialR.attr.colorOutline)
    private val labelColor: Int = context.resolveThemedColor(MaterialR.attr.colorOnSurfaceVariant)

    init {
        gridPaint.color = gridColor
        gridPaint.alpha = 48
        labelPaint.color = labelColor
        peakPaint.color = labelColor
    }

    private fun reset() {
        cachedVersion = -1
        cachedWindow = null
        samples = 0
    }

    /** Force a redraw with the next sample, e.g. after the window changes. */
    fun refresh() {
        reset()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val history = this.history

        if (history == null)
            return

        if (history.version != cachedVersion || history.window != cachedWindow) {
            cachedVersion = history.version
            cachedWindow = history.window

            samples = readInto(history, history.window)
        }

        if (samples < 2 || width == 0 || height == 0)
            return

        val labelHeight = labelPaint.fontMetrics.let { it.descent - it.ascent }
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val top = paddingTop.toFloat() + labelHeight
        val bottom = (height - paddingBottom).toFloat()

        val usableWidth = right - left
        val usableHeight = bottom - top

        if (usableWidth <= 0f || usableHeight <= 0f)
            return

        val max = maxOf(uploads.maxOf(), downloads.maxOf()).coerceAtLeast(1L)

        drawGrid(canvas, left, right, top, bottom, usableHeight, max)

        // One column per pixel: keep the peak of each bucket so a short spike
        // cannot disappear when a 30 minute window is squeezed into ~300dp.
        val columns = usableWidth.toInt().coerceIn(1, samples)
        val perColumn = samples.toFloat() / columns

        drawSeries(
            canvas = canvas,
            source = downloads,
            columns = columns,
            perColumn = perColumn,
            left = left,
            top = top,
            usableHeight = usableHeight,
            max = max,
            path = downloadPath,
            stroke = downloadColor,
        )

        drawSeries(
            canvas = canvas,
            source = uploads,
            columns = columns,
            perColumn = perColumn,
            left = left,
            top = top,
            usableHeight = usableHeight,
            max = max,
            path = uploadPath,
            stroke = uploadColor,
        )

        drawPeak(canvas, left, top, usableHeight, max, perColumn)
    }

    private fun readInto(history: TrafficHistory, window: TrafficHistory.Window): Int {
        if (uploads.size < window.samples) {
            uploads = LongArray(window.samples)
            downloads = LongArray(window.samples)
        }

        val n = history.read(window, uploads, downloads)

        if (n < 0)
            return 0

        return n
    }

    private fun drawGrid(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        usableHeight: Float,
        max: Long,
    ) {
        val lines = 3

        for (i in 0..lines) {
            val y = bottom - usableHeight * i / lines

            canvas.drawLine(left, y, right, y, gridPaint)
        }

        canvas.drawText(
            max.toBytesString(),
            left,
            top - labelPaint.fontMetrics.let { it.ascent } - density * 2f,
            labelPaint,
        )
    }

    private fun drawSeries(
        canvas: Canvas,
        source: LongArray,
        columns: Int,
        perColumn: Float,
        left: Float,
        top: Float,
        usableHeight: Float,
        max: Long,
        path: Path,
        stroke: Int,
    ) {
        path.reset()

        var drawn = false
        var x = left

        for (column in 0 until columns) {
            val from = (column * perColumn).toInt().coerceIn(0, samples - 1)
            val to = (((column + 1) * perColumn).toInt().coerceIn(from + 1, samples)

            var peak = 0L

            for (i in from until to) {
                val v = source[i]

                if (v > peak)
                    peak = v
            }

            if (peak == 0L && from > 0)
                peak = source[from - 1]

            val y = top + usableHeight - (peak.toFloat() / max) * usableHeight

            if (!drawn) {
                path.moveTo(x, y)
                drawn = true
            } else {
                path.lineTo(x, y)
            }

            x += usableWidth / columns
        }

        if (!drawn)
            return

        val fill = Path(path)
        fill.lineTo(left + usableWidth, top + usableHeight)
        fill.lineTo(left, top + usableHeight)
        fill.close()

        fillPaint.color = (stroke and 0x00FFFFFF) or (0x26 shl 24)
        canvas.drawPath(fill, fillPaint)

        linePaint.color = stroke
        canvas.drawPath(path, linePaint)
    }

    private fun drawPeak(
        canvas: Canvas,
        left: Float,
        top: Float,
        usableHeight: Float,
        max: Long,
        perColumn: Float,
    ) {
        var peakValue = 0L
        var peakIndex = -1

        for (i in 0 until samples) {
            val v = maxOf(uploads[i], downloads[i])

            if (v > peakValue) {
                peakValue = v
                peakIndex = i
            }
        }

        if (peakIndex < 0 || peakValue <= 0L)
            return

        val x = left + peakIndex / perColumn
        val y = top + usableHeight - (peakValue.toFloat() / max) * usableHeight
        val r = density * 3f

        canvas.drawLine(x, y + r, x, top + usableHeight, gridPaint)
        canvas.drawCircle(x, y, r, peakPaint)
    }
}