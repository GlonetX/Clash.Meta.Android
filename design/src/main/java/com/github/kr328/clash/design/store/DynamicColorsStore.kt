package com.github.kr328.clash.design.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider

/**
 * User preference for Material You (wallpaper) dynamic colour.
 *
 * Stored in the shared `ui` preferences file (same file as [UiStore], but a
 * separate key owned by the profiles/logs worktree) so the settings page can
 * adopt it later without a data migration.
 */
class DynamicColorsStore(context: Context) {
    private val store = Store(
        context
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .asStoreProvider()
    )

    /** Follow the wallpaper palette instead of the hardcoded Clash blue. */
    var enabled: Boolean by store.boolean(
        key = "dynamic_colors_enabled",
        defaultValue = true
    )

    companion object {
        private const val PREFERENCE_NAME = "ui"
    }
}
