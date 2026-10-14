package com.github.kr328.clash.design.settings

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * One item of a settings page: either a group of rows inside an `AppCard`, or
 * a standalone explanatory paragraph (which is not a card).
 *
 * Items and rows are immutable. Anything that changes is a new instance and
 * the page rebuilds its list, which is what lets [SettingAdapter] diff by
 * content instead of by object identity — and what stops a recycled row from
 * showing a stale switch position or a stale value.
 *
 * See DESIGN_SYSTEM.md rule 4 — "data flows through one state object".
 */
sealed class SettingItem {
    abstract val identityKey: String
    abstract val contentKey: String
}

/** One `AppCard` worth of settings. A [title] of `0` means "no heading". */
data class SettingGroup(
    @StringRes val title: Int,
    val rows: List<SettingRow>,
) : SettingItem() {
    override val identityKey: String
        get() = if (title != 0) "group/$title" else "group/rows/" + rows.joinToString(",") { it.id }

    override val contentKey: String
        get() = "$title#" + rows.joinToString("|") { it.contentKey }
}

/** A short paragraph rendered above the first card. Never inside one. */
data class SettingTips(
    @StringRes val text: Int,
) : SettingItem() {
    override val identityKey: String
        get() = "tips/$text"

    override val contentKey: String
        get() = "tips/$text"
}

/**
 * One row of a [SettingGroup].
 */
sealed class SettingRow {
    /** Stable within a page. */
    abstract val id: String

    @get:StringRes
    abstract val title: Int

    @get:DrawableRes
    abstract val icon: Int?

    abstract val enabled: Boolean

    /** Everything about this row that changes how it renders. */
    abstract val contentKey: String

    /** A boolean setting. The whole row toggles; the switch itself is inert. */
    class Switch(
        override val id: String,
        @StringRes override val title: Int,
        val checked: Boolean,
        @DrawableRes override val icon: Int? = null,
        val summary: CharSequence? = null,
        override val enabled: Boolean = true,
        val onToggle: (Boolean) -> Unit,
    ) : SettingRow() {
        override val contentKey: String
            get() = "switch/$id/$title/$checked/$enabled/$summary"
    }

    /**
     * A navigable row, or a row whose second line shows the value it is
     * currently set to. [onClick] either opens an editor or runs the action;
     * the row view is handed over so editors can anchor themselves to it.
     */
    class Action(
        override val id: String,
        @StringRes override val title: Int,
        val summary: CharSequence? = null,
        @DrawableRes override val icon: Int? = null,
        override val enabled: Boolean = true,
        val showChevron: Boolean = true,
        val onClick: (View) -> Unit,
    ) : SettingRow() {
        override val contentKey: String
            get() = "action/$id/$title/$enabled/$showChevron/$summary"
    }
}
