package com.github.kr328.clash.design.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.annotation.AttrRes
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.R as MaterialR

/**
 * One rounded bar for the core's memory footprint.
 *
 * `queryMemory()` returns a single `Long` — the total, with no heap / cache
 * breakdown — so this shows one value against a ceiling rather than a
 * fabricated split. The ceiling is the largest value seen since the view was
 * attached, decayed slowly back towards the current reading so a one-off peak
 * does not pin the bar at full forever.
 */
class MemoryBarView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
) : android.view.View(context, attributeSet, defStyleAttr) {

    private val density = context.resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var value = 0L
    private var ceiling = 0L

    init {
        trackPaint.color = context.resolveThemedColor(MaterialR.attr.colorSurfaceVariant)
        fillPaint.color = context.resolveThemedColor(MaterialR.attr.colorTertiary)
    }

    /** Current total in bytes. */
    fun setMemory(bytes: Long) {
        val next = bytes.coerceAtLeast(0)

        if (next == value && ceiling != 0L)
            return

        value = next

        // Rise instantly, fall gently.
        ceiling = if (next > ceiling) next else (ceiling * 0.98 + next * 0.02).toLong()

        if (ceiling < 1L)
            ceiling = 1L

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f

        if (w <= 0f || h <= 0f)
            return

        canvas.drawRoundRect(0f, 0f, w, h, r, r, trackPaint)

        val fraction = (value.toFloat() / ceiling).coerceIn(0f, 1f)
        val filled = (w * fraction).coerceAtLeast(h)

        if (filled <= 0f)
            return

        // Keep a short bar looking like a rounded pill rather than a lozenge.
        val fillRadius = if (filled < h) h / 2f else r

        canvas.drawRoundRect(0f, 0f, filled, h, fillRadius, fillRadius, fillPaint)
    }
}