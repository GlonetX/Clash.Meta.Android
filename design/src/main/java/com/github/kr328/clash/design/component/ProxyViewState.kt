package com.github.kr328.clash.design.component

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ProxyIconStyle
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.util.toFlagEmoji
import kotlin.math.absoluteValue
import kotlin.math.max

class ProxyViewState(
    val config: ProxyViewConfig,
    val proxy: Proxy,
    private val parent: ProxyState,
    private val link: ProxyState?
) {
    companion object {
        // delays at or above this value are rendered with the "medium" color
        const val DELAY_MEDIUM_THRESHOLD = 600

        /** Range covered by the flag emoji, U+1F1E6 REGIONAL INDICATOR SYMBOL LETTER A. */
        private const val REGIONAL_INDICATOR_FIRST = 0x1F1E6
        private const val REGIONAL_INDICATOR_LAST = 0x1F1FF
    }

    val paint = Paint()
    val rect = Rect()
    val path = Path()

    var title: String = ""
    var subtitle: String = ""

    /**
     * Leading glyph for the card, or empty when [config.iconStyle] is
     * [ProxyIconStyle.None] or the node name carries no country code.
     *
     * The icon and the delay badge are distinct pieces of information and use
     * distinct affordances: the icon is a flag, the delay is text. Neither one
     * is ever reused to signal the other.
     */
    var icon: String = ""

    var delayText: String = ""
    var delayColor: Int = config.unselectedControl
    var background: Int = config.unselectedBackground
    var controls: Int = config.unselectedControl

    val isSelected: Boolean
        get() = selected

    private var delay: Int = 0
    private var selected: Boolean = false
    private var parentNow: String = ""
    private var linkNow: String? = null
    private var iconSource: String = ""

    private var lastFrameTime = System.currentTimeMillis()

    /** Case-insensitive match against every text the card can show. */
    fun matches(keyword: String): Boolean {
        if (keyword.isEmpty())
            return true

        return title.contains(keyword, ignoreCase = true) ||
            subtitle.contains(keyword, ignoreCase = true) ||
            proxy.name.contains(keyword, ignoreCase = true)
    }

    fun update(snap: Boolean): Boolean {
        val frameTime = System.currentTimeMillis()
        var invalidate = false

        if (proxy.isGroup) {
            title = proxy.name

            if (link == null) {
                subtitle = proxy.type
            } else {
                if (linkNow !== link.now) {
                    linkNow = link.now

                    subtitle = "%s(%s)".format(
                        proxy.type,
                        link.now.ifEmpty { "*" }
                    )
                }
            }
        } else {
            title = proxy.title
            subtitle = proxy.subtitle
        }

        updateIcon()

        if (delay != proxy.delay) {
            delay = proxy.delay

            when {
                // tested but timed out
                proxy.delay < 0 && !proxy.isGroup -> {
                    delayText = config.context.getString(R.string.timeout)
                    delayColor = config.delayTimeout
                }
                proxy.delay in 1 until DELAY_MEDIUM_THRESHOLD -> {
                    delayText = proxy.delay.toString()
                    delayColor = config.delayGood
                }
                proxy.delay in DELAY_MEDIUM_THRESHOLD..Short.MAX_VALUE -> {
                    delayText = proxy.delay.toString()
                    delayColor = config.delayMedium
                }
                else -> {
                    // never tested or group without delay
                    delayText = ""
                }
            }
        }

        if (parentNow !== parent.now) {
            parentNow = parent.now
            selected = proxy.name == parent.now
        }

        controls = if (selected) config.selectedControl else config.unselectedControl

        if (snap) {
            background = if (selected) config.selectedBackground else config.unselectedBackground
        } else {
            val target = if (selected) config.selectedBackground else config.unselectedBackground

            if (background != target) {
                val sa = Color.alpha(background)
                val sr = Color.red(background)
                val sg = Color.green(background)
                val sb = Color.blue(background)

                val ta = Color.alpha(target)
                val tr = Color.red(target)
                val tg = Color.green(target)
                val tb = Color.blue(target)

                val da = ta - sa
                val dr = tr - sr
                val dg = tg - sg
                val db = tb - sb

                val max = max(
                    da.absoluteValue,
                    max(
                        dr.absoluteValue,
                        max(
                            dg.absoluteValue,
                            db.absoluteValue
                        )
                    )
                )

                val frameOffset = frameTime - lastFrameTime

                val colorOffset = (frameOffset / max.toFloat().coerceAtLeast(0.001f))
                    .coerceIn(0.0f, 1.0f)

                background = if (colorOffset > 0.999f) {
                    target
                } else {
                    Color.argb(
                        (sa + da * colorOffset).toInt(),
                        (sr + dr * colorOffset).toInt(),
                        (sg + dg * colorOffset).toInt(),
                        (sb + db * colorOffset).toInt()
                    )
                }

                invalidate = true
            }
        }

        lastFrameTime = frameTime

        return invalidate
    }

    private fun updateIcon() {
        if (config.iconStyle != ProxyIconStyle.Flag) {
            if (icon.isNotEmpty()) {
                iconSource = ""
                icon = ""
            }

            return
        }

        val source = proxy.title.ifBlank { proxy.name }

        if (iconSource != source) {
            iconSource = source

            icon = source.leadingFlag()
        }
    }

    /**
     * The flag for a node whose name starts with either a flag emoji already
     * (`🇭🇰 …`) or a two-letter country code (`HK 01`, `US-2`). Anything else
     * renders no icon rather than guessing.
     */
    private fun String.leadingFlag(): String {
        val trimmed = trimStart()

        if (trimmed.isEmpty())
            return ""

        val codePoint = trimmed.codePointAt(0)

        if (codePoint in REGIONAL_INDICATOR_FIRST..REGIONAL_INDICATOR_LAST)
            return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1))

        return trimmed.take(2).toFlagEmoji()
    }
}