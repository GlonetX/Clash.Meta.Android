package com.github.kr328.clash.design.component

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.github.kr328.clash.design.store.UiStore

class ProxyView(
    context: Context,
    config: ProxyViewConfig,
) : View(context) {

    init {
        background = context.getDrawableCompat(config.clickableBackground)
    }

    var state: ProxyViewState? = null
    constructor(context: Context) : this(context, ProxyViewConfig(context, 2))
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val state = state ?: return super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED ->
                resources.displayMetrics.widthPixels
            MeasureSpec.AT_MOST, MeasureSpec.EXACTLY ->
                MeasureSpec.getSize(widthMeasureSpec)
            else ->
                throw IllegalArgumentException("invalid measure spec")
        }

        state.paint.apply {
            reset()

            textSize = state.config.textSize

            getTextBounds("Stub!", 0, 1, state.rect)
        }

        val textHeight = state.rect.height()
        val exceptHeight = (state.config.layoutPadding * 2 +
                state.config.contentPadding * 2 +
                textHeight * 2 +
                state.config.textMargin).toInt()

        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED ->
                exceptHeight
            MeasureSpec.AT_MOST, MeasureSpec.EXACTLY ->
                exceptHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
            else ->
                throw IllegalArgumentException("invalid measure spec")
        }

        setMeasuredDimension(width, height)
    }

    override fun draw(canvas: Canvas) {
        val state = state ?: return super.draw(canvas)

        if (state.update(false))
            postInvalidate()

        val width = width.toFloat()
        val height = height.toFloat()

        val paint = state.paint

        paint.reset()

        paint.color = state.background
        paint.style = Paint.Style.FILL

        // draw background
        canvas.apply {
            if (state.config.proxyLine == 1) {
                drawRect(0f, 0f, width, height, paint)
            } else {
                val path = state.path

                path.reset()

                path.addRoundRect(
                    state.config.layoutPadding,
                    state.config.layoutPadding,
                    width - state.config.layoutPadding,
                    height - state.config.layoutPadding,
                    state.config.cardRadius,
                    state.config.cardRadius,
                    Path.Direction.CW,
                )

                paint.setShadowLayer(
                    state.config.cardRadius,
                    state.config.cardOffset,
                    state.config.cardOffset,
                    state.config.shadow
                )

                drawPath(path, paint)
                clipPath(path)
            }
        }

        super.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val state = state ?: return

        val paint = state.paint

        val width = width.toFloat()
        val height = height.toFloat()

        paint.textSize = state.config.textSize

        // selected cards draw the delay inside a surface-colored chip
        // so the tier color stays readable on the primary background
        val drawChip = state.isSelected && state.delayText.isNotEmpty()
        val chipPadH = if (drawChip) state.config.chipPaddingHorizontal else 0f

        val padding = state.config.layoutPadding + state.config.contentPadding

        // leading icon (country flag), measured once and reserved on the left
        val icon = state.icon
        val iconWidth = if (icon.isEmpty()) {
            0f
        } else {
            paint.getTextBounds(icon, 0, icon.length, state.rect)

            state.rect.width().toFloat()
        }
        val iconGap = if (iconWidth > 0f) state.config.iconMargin else 0f

        // measure delay text bounds
        val delayCount = paint.breakText(
            state.delayText,
            false,
            (width - padding * 2 - chipPadH * 2).coerceAtLeast(0f),
            null
        )

        state.paint.getTextBounds(state.delayText, 0, delayCount, state.rect)

        val delayWidth = state.rect.width()

        val mainTextWidth = (width -
                padding * 2 -
                iconWidth -
                iconGap -
                delayWidth -
                chipPadH * 2 -
                state.config.textMargin * 2
                )
            .coerceAtLeast(0f)

        // measure title text bounds
        val titleCount = paint.breakText(
            state.title,
            false,
            mainTextWidth,
            null,
        )

        // measure subtitle text bounds
        val subtitleCount = paint.breakText(
            state.subtitle,
            false,
            mainTextWidth,
            null,
        )

        // text draw measure
        val textOffset = (paint.descent() + paint.ascent()) / 2

        paint.reset()

        paint.textSize = state.config.textSize
        paint.isAntiAlias = true

        // draw delay
        canvas.apply {
            if (state.delayText.isEmpty())
                return@apply

            val right = width - padding

            if (drawChip) {
                val chipHeight = state.config.textSize + state.config.chipPaddingVertical * 2
                val top = height / 2f - chipHeight / 2
                val bottom = height / 2f + chipHeight / 2
                val left = right - delayWidth - chipPadH * 2

                val path = state.path

                path.reset()

                path.addRoundRect(
                    left,
                    top,
                    right,
                    bottom,
                    chipHeight / 2,
                    chipHeight / 2,
                    Path.Direction.CW
                )

                paint.color = state.config.chipBackground
                paint.style = Paint.Style.FILL

                drawPath(path, paint)
            }

            paint.color = state.delayColor

            val x = right - chipPadH - delayWidth
            val y = height / 2f - textOffset

            drawText(state.delayText, 0, delayCount, x, y, paint)
        }

        paint.color = state.controls

        val textX = padding + iconWidth + iconGap

        // draw title
        canvas.apply {
            val y = state.config.layoutPadding +
                    (height - state.config.layoutPadding * 2) / 3f - textOffset

            drawText(state.title, 0, titleCount, textX, y, paint)
        }

        // draw subtitle
        canvas.apply {
            val y = state.config.layoutPadding +
                    (height - state.config.layoutPadding * 2) / 3f * 2 - textOffset

            drawText(state.subtitle, 0, subtitleCount, textX, y, paint)
        }

        // draw the leading icon last so it is never clipped by the title
        if (icon.isNotEmpty()) {
            paint.color = state.controls

            val y = state.config.layoutPadding +
                    (height - state.config.layoutPadding * 2) / 3f - textOffset

            canvas.drawText(icon, 0, icon.length, padding, y, paint)
        }
    }
}