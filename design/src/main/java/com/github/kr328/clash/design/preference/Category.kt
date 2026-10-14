package com.github.kr328.clash.design.preference

import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.PreferenceCategoryBinding
import com.github.kr328.clash.design.util.layoutInflater

fun PreferenceScreen.category(
    @StringRes text: Int,
) {
    val binding = PreferenceCategoryBinding
        .inflate(context.layoutInflater, root, false)

    binding.textView.text = context.getString(text)

    val density = context.resources.displayMetrics.density

    val group = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL

        background = context.getDrawableCompat(R.drawable.bg_preference_group)
    }

    val bottomMargin = (12 * density).toInt()

    addElement(
        object : Preference {
            override val view: View
                get() = binding.root
            override var enabled: Boolean
                get() = binding.root.isEnabled
                set(value) {
                    binding.root.isEnabled = value
                }
        }
    )

    val groupParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        marginStart = (16 * density).toInt()
        marginEnd = (16 * density).toInt()
        bottomMargin = bottomMargin
    }

    root.addView(group, groupParams)

    if (this is PreferenceScreenImpl)
        this.groupContainer = group
}
