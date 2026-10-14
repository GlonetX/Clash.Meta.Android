package com.github.kr328.clash.design.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import androidx.annotation.AttrRes
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.R as MaterialR

class TrafficChartView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
) : android.view.View(context, attributeSet, defStyleAttr) {
    private val capacity = 60
    private val uploads = ArrayDeque<Long>(capacity)
    private val downloads = ArrayDeque<Long>(capacity)

    private val uploadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 1.5f
    }
    private val downloadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 1.5f
    }
    private val uploadFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val downloadFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val uploadPath = Path()
    private val downloadPath = Path()

    var uploadColor: Int
        get() = uploadPaint.color
        set(value) {
            uploadPaint.color = value
            uploadFillPaint.color = (value and 0x00FFFFFF) or (0x30 shl 24)
        }

    var downloadColor: Int
        get() = downloadPaint.color
        set(value) {
            downloadPaint.color = value
            downloadFillPaint.color = (value and 0x00FFFFFF) or (0x30 shl 24)
        }

    init {
        uploadColor = context.resolveThemedColor(com.google.android.material.R.attr.colorTertiary)
        downloadColor = context.resolveThemedColor(MaterialR.attr.colorPrimary)
    }

    fun push(upload: Long, download: Long) {
        if (uploads.size >= capacity) {
            uploads.removeFirst()
            downloads.removeFirst()
        }

        uploads.addLast(upload)
        downloads.addLast(download)

        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (uploads.size < 2)
            return

        val max = maxOf(
            uploads.max(),
            downloads.max(),
        ).coerceAtLeast(1)

        val width = width.toFloat()
        val height = height.toFloat()
        val usable = height - paddingTop - paddingBottom
        val stepX = width / (capacity - 1)

        drawSeries(canvas, downloads, max, usable, stepX, downloadPath, downloadPaint, downloadFillPaint)
        drawSeries(canvas, uploads, max, usable, stepX, uploadPath, uploadPaint, uploadFillPaint)
    }

    private fun drawSeries(
        canvas: Canvas,
        data: ArrayDeque<Long>,
        max: Long,
        usableHeight: Float,
        stepX: Float,
        path: Path,
        strokePaint: Paint,
        fillPaint: Paint,
    ) {
        path.reset()

        val offset = capacity - data.size
        val bottom = height.toFloat() - paddingBottom

        data.forEachIndexed { index, value ->
            val x = (offset + index) * stepX
            val y = bottom - (value.toFloat() / max) * usableHeight

            if (index == 0)
                path.moveTo(x, y)
            else
                path.lineTo(x, y)
        }

        val fill = Path(path)
        fill.lineTo((offset + data.size - 1) * stepX, bottom)
        fill.lineTo(offset * stepX, bottom)
        fill.close()

        canvas.drawPath(fill, fillPaint)
        canvas.drawPath(path, strokePaint)
    }
}
