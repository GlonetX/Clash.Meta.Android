package com.github.kr328.clash.design.view

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.resolveClickableAttrs
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.card.MaterialCardView
import com.google.android.material.R as MaterialR

/**
 * The single card surface for the whole app.
 *
 * Replaces the three-way mix that used to exist:
 *   - [LargeActionCard]        (8dp radius, 5dp elevation)
 *   - bare `MaterialCardView`  (theme default shape and elevation)
 *   - bare `LinearLayout` rows with no surface at all
 *
 * Every card in the app shares one radius, one elevation and one background,
 * so grids of cards read as one surface rather than a stack of mismatched
 * rectangles.
 *
 * Usage in XML:
 * ```
 * <com.github.kr328.clash.design.view.AppCard
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:onClick="@{() -> self.request(Request.Open)}">
 *
 *     <!-- single child, laid out by the card itself -->
 * </com.github.kr328.clash.design.view.AppCard>
 * ```
 *
 * See DESIGN_SYSTEM.md for the full contract.
 */
class AppCard @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
) : MaterialCardView(context, attributeSet, defStyleAttr) {

    init {
        radius = context.getPixels(R.dimen.app_radius_md).toFloat()
        cardElevation = context.getPixels(R.dimen.app_elevation_card).toFloat()
        strokeWidth = 0

        setCardBackgroundColor(
            context.resolveThemedColor(MaterialR.attr.colorSurface)
        )

        // A card is a surface, not a control. Do NOT force isClickable or
        // isFocusable here: LayoutInflater has already processed
        // android:onClick / android:clickable by the time this runs, and
        // overwriting them would silently break the card's own click
        // handling. Only honour an explicit android:foreground (ripple).
        context.resolveClickableAttrs(attributeSet, defStyleAttr) {
            if (foreground() != null) {
                foreground = foreground()
            }
        }
    }
}