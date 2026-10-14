package com.github.kr328.clash.design.component

import android.content.Context
import android.graphics.Color
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ProxyDensity
import com.github.kr328.clash.design.model.ProxyIconStyle
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.resolveThemedResourceId
import com.google.android.material.R as MaterialR

/**
 * Every measurement and colour the custom-drawn proxy node card needs.
 *
 * The card is drawn on a [android.graphics.Canvas] rather than composed from
 * `AppCard`, because it animates its own selection colour and because a
 * RecyclerView of several hundred cards cannot afford a view hierarchy per
 * cell. What it borrows from the design system is the *scale*: every radius,
 * padding and text size below comes from `values/tokens.xml` or the
 * proxy-owned density ladder in `values/dimens_proxy.xml`. The legacy
 * `proxy_card_radius` (5dp) and the rest of the old ad-hoc `proxy_*` ladder
 * are deliberately not read any more.
 */
class ProxyViewConfig(
    val context: Context,
    var proxyLine: Int,
    density: ProxyDensity = ProxyDensity.Standard,
    iconStyle: ProxyIconStyle = ProxyIconStyle.None,
) {
    /** Tight / standard / loose. Affects padding, type size and corner radius. */
    var density: ProxyDensity = density

    /** Leading glyph drawn on a node card. */
    var iconStyle: ProxyIconStyle = iconStyle

    private val colorSurface = context.resolveThemedColor(MaterialR.attr.colorSurface)

    private fun step(tight: Int, standard: Int, loose: Int): Int {
        return when (density) {
            ProxyDensity.Tight -> tight
            ProxyDensity.Standard -> standard
            ProxyDensity.Loose -> loose
        }
    }

    private fun px(resId: Int): Float = context.getPixels(resId).toFloat()

    val clickableBackground =
        context.resolveThemedResourceId(android.R.attr.selectableItemBackground)

    val selectedControl = context.resolveThemedColor(MaterialR.attr.colorOnPrimary)
    val selectedBackground = context.resolveThemedColor(MaterialR.attr.colorPrimary)

    val unselectedControl = context.resolveThemedColor(MaterialR.attr.colorOnSurface)

    /** The one-column layout is a full-bleed list row, so it has no card fill. */
    val unselectedBackground: Int
        get() = if (proxyLine == 1) Color.TRANSPARENT else colorSurface

    // ---- delay tiers: delayGood / delayMedium / colorError, never a glyph ----

    val delayGood = context.resolveThemedColor(R.attr.delayGood)
    val delayMedium = context.resolveThemedColor(R.attr.delayMedium)
    val delayTimeout = context.resolveThemedColor(MaterialR.attr.colorError)

    val chipBackground = colorSurface
    val chipPaddingHorizontal = px(R.dimen.app_space_sm)
    val chipPaddingVertical = px(R.dimen.app_space_xs)

    val layoutPadding = px(
        step(
            R.dimen.proxy_gap_tight,
            R.dimen.proxy_gap_standard,
            R.dimen.proxy_gap_loose,
        )
    )

    val contentPadding
        get() = if (proxyLine == 2) {
            px(
                step(
                    R.dimen.proxy_content_wide_tight,
                    R.dimen.proxy_content_wide_standard,
                    R.dimen.proxy_content_wide_loose,
                )
            )
        } else {
            px(
                step(
                    R.dimen.proxy_content_grid_tight,
                    R.dimen.proxy_content_grid_standard,
                    R.dimen.proxy_content_grid_loose,
                )
            )
        }

    val textMargin
        get() = if (proxyLine == 2) {
            px(
                step(
                    R.dimen.proxy_margin_wide_tight,
                    R.dimen.proxy_margin_wide_standard,
                    R.dimen.proxy_margin_wide_loose,
                )
            )
        } else {
            px(
                step(
                    R.dimen.proxy_margin_grid_tight,
                    R.dimen.proxy_margin_grid_standard,
                    R.dimen.proxy_margin_grid_loose,
                )
            )
        }

    val textSize
        get() = if (proxyLine == 2) {
            px(
                step(
                    R.dimen.proxy_text_wide_tight,
                    R.dimen.proxy_text_wide_standard,
                    R.dimen.proxy_text_wide_loose,
                )
            )
        } else {
            px(
                step(
                    R.dimen.proxy_text_grid_tight,
                    R.dimen.proxy_text_grid_standard,
                    R.dimen.proxy_text_grid_loose,
                )
            )
        }

    /** Corner radius follows the density step so tight cards read as compact. */
    val cardRadius
        get() = px(
            when (density) {
                ProxyDensity.Tight -> R.dimen.app_radius_xs
                ProxyDensity.Standard -> R.dimen.app_radius_sm
                ProxyDensity.Loose -> R.dimen.app_radius_md
            }
        )

    /** Gap between the leading icon and the node title. */
    val iconMargin = px(R.dimen.proxy_icon_margin)

    val shadow = Color.argb(
        0x15,
        Color.red(Color.DKGRAY),
        Color.green(Color.DKGRAY),
        Color.blue(Color.DKGRAY),
    )

    var cardOffset = px(R.dimen.proxy_card_offset)
}