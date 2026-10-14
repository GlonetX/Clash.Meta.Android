package com.github.kr328.clash.design.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import androidx.annotation.AttrRes
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.R as MaterialR

/**
 * Upload / download split as a donut.
 *
 * Built from `queryTrafficTotal()`, which returns the packed upload/download
 * pair the core accumulates since it started. Arcs animate whenever the
 * totals move so the change reads as a change rather than a repaint.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
) : android.view.View(context, attributeSet, defStyleAttr) {

    private val density = context.resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val uploadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val downloadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val bounds = RectF()

    private var upload = 0L
    private var download = 0L

    private var progress = 0f

    private var animator: ValueAnimator? = null

    init {
        val thickness = density * 10f

        trackPaint.strokeWidth = thickness
        uploadPaint.strokeWidth = thickness
        downloadPaint.strokeWidth = thickness

        trackPaint.color = context.resolveThemedColor(MaterialR.attr.colorSurfaceVariant)
        // Matches TrafficChartView: tertiary is download, the medium accent
        // is upload. `colorSecondary` is aliased to `colorPrimary` here and
        // would read as "the app is on".
        downloadPaint.color = context.resolveThemedColor(MaterialR.attr.colorTertiary)
        uploadPaint.color = context.resolveThemedColor(R.attr.delayMedium)
    }

    /** New totals in bytes; animates from whatever is currently drawn. */
    fun setTraffic(upload: Long, download: Long) {
        if (upload == this.upload && download == this.download)
            return

        this.upload = upload.coerceAtLeast(0)
        this.download = download.coerceAtLeast(0)

        animator?.cancel()

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null

        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val inset = uploadPaint.strokeWidth / 2f + density * 2f
        val size = minOf(w, h).toFloat()

        val left = (w - size) / 2f + inset
        val top = (h - size) / 2f + inset

        bounds.set(left, top, left + size - inset * 2, top + size - inset * 2)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (bounds.isEmpty)
            return

        canvas.drawArc(bounds, 0f, 360f, false, trackPaint)

        val total = (upload + download).toFloat()

        if (total <= 0f || progress <= 0f)
            return

        val gap = 4f
        val sweep = 360f * progress

        val downloadSweep = (download / total) * sweep
        val uploadSweep = (upload / total) * sweep

        var start = -90f

        if (downloadSweep > gap) {
            canvas.drawArc(
                bounds,
                start + gap / 2,
                downloadSweep - gap,
                false,
                downloadPaint,
            )
        }

        start += downloadSweep

        if (uploadSweep > gap) {
            canvas.drawArc(
                bounds,
                start + gap / 2,
                uploadSweep - gap,
                false,
                uploadPaint,
            )
        }
    }
}