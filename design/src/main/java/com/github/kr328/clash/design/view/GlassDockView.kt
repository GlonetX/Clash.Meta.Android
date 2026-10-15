package com.github.kr328.clash.design.view

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Bottom navigation surface used by the floating dock presentation. */
class GlassDockView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
) : BottomNavigationView(
    context,
    attributeSet,
    defStyleAttr.takeIf { it != 0 }
        ?: com.google.android.material.R.attr.bottomNavigationStyle,
)
