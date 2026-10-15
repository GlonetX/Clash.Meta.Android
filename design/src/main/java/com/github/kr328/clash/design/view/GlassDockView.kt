package com.github.kr328.clash.design.view

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * The floating navigation dock.
 *
 * "Liquid glass" on Android is translucent surface + edge highlight +
 * shadow, not background blur: RenderEffect blurs a whole view (the entire
 * page behind the dock, not just the strip it covers), which is a
 * non-starter. The visual reads glass through:
 *
 *  - `bg_dock_glass`: ~70% opaque surface so scrolling content tints
 *    through the pill,
 *  - a 1dp top-edge highlight (`bg_dock_glass_stroke`) like a sheet of
 *    glass catching light,
 *  - the existing 8dp elevation shadow lifting it off the page.
 *
 * Works identically on every API level the app supports (21+).
 */
class GlassDockView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
) : BottomNavigationView(context, attributeSet, defStyleAttr)