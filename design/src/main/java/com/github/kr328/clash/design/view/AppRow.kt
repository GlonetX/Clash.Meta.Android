package com.github.kr328.clash.design.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.ComponentAppRowBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveClickableAttrs
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.R as MaterialR

/**
 * The single row component for the whole app: an icon container, a primary
 * line, an optional secondary line and an optional trailing glyph.
 *
 * Replaces the three divergent row implementations that used to coexist:
 *   - `component_action_label.xml`       (rounded icon container)
 *   - `component_large_action_label.xml` (bare icon, no container)
 *   - `preference_clickable.xml`         (DSL-injected, different metrics)
 *
 * Usage in XML:
 * ```
 * <com.github.kr328.clash.design.view.AppRow
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:onClick="@{() -> self.request(Request.Open)}"
 *     app:icon="@drawable/ic_baseline_settings"
 *     app:trailingIcon="@drawable/ic_baseline_chevron_right"
 *     app:text="@string/app"
 *     app:subtext="@string/app_summary" />
 * ```
 *
 * [subtext] hides its view when null or empty, so a nullable DataBinding
 * variable can be assigned directly without a visibility expression.
 *
 * See DESIGN_SYSTEM.md for the full contract.
 */
class AppRow @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
) : LinearLayout(context, attributeSet, defStyleAttr) {

    private val binding = ComponentAppRowBinding
        .inflate(context.layoutInflater, this, true)

    var text: CharSequence?
        get() = binding.textView.text
        set(value) {
            binding.textView.text = value
        }

    var subtext: CharSequence?
        get() = binding.subtextView.text
        set(value) {
            binding.subtextView.text = value
            binding.subtextView.visibility =
                if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

    /** The leading glyph. Tinted with `colorOnSurfaceVariant`. */
    var icon: Drawable?
        get() = binding.iconView.drawable
        set(value) {
            binding.iconView.setImageDrawable(value)
        }

    /** An optional trailing glyph, usually a chevron. Tinted `colorOutline`. */
    var trailingIcon: Drawable?
        get() = binding.trailingView.drawable
        set(value) {
            if (value == null) {
                binding.trailingView.visibility = View.GONE
            } else {
                binding.trailingView.visibility = View.VISIBLE
                binding.trailingView.setImageDrawable(value)
            }
        }

    /** Hides the rounded icon container, collapsing the row to text only. */
    var iconContainerVisible: Boolean
        get() = binding.iconContainer.visibility == View.VISIBLE
        set(value) {
            binding.iconContainer.visibility = if (value) View.VISIBLE else View.GONE
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        // Icons inherit their tint from the theme rather than from a
        // `android:tint` baked into each vector, so one drawable works in
        // every context (on-surface in a card, on-primary in a hero card).
        binding.iconView.imageTintList = ColorStateList.valueOf(
            context.resolveThemedColor(MaterialR.attr.colorOnSurfaceVariant)
        )
        binding.trailingView.imageTintList = ColorStateList.valueOf(
            context.resolveThemedColor(MaterialR.attr.colorOutline)
        )

        context.resolveClickableAttrs(attributeSet, defStyleAttr) {
            isFocusable = focusable(true)
            isClickable = clickable(true)
            foreground = foreground()
        }

        context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.AppRow,
            defStyleAttr,
            0
        ).apply {
            try {
                text = getString(R.styleable.AppRow_text)
                subtext = getString(R.styleable.AppRow_subtext)
                icon = getDrawable(R.styleable.AppRow_icon)
                trailingIcon = getDrawable(R.styleable.AppRow_trailingIcon)
                iconContainerVisible =
                    !getBoolean(R.styleable.AppRow_hideIconContainer, false)
            } finally {
                recycle()
            }
        }
    }
}