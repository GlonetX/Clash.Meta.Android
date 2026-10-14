package com.github.kr328.clash.design.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider

/**
 * User preference for refreshing due subscription profiles when the app is
 * launched. Each profile still honours its own update interval — a profile
 * is only refreshed if its interval has elapsed since the last update.
 *
 * Stored in the shared `ui` preferences file (same file as [UiStore], but a
 * separate key owned by the profiles/logs worktree) so the settings page
 * can adopt it later without a data migration.
 */
class ProfileUpdateStore(context: Context) {
    private val store = Store(
        context
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .asStoreProvider()
    )

    var updateOnLaunch: Boolean by store.boolean(
        key = "profiles_update_on_launch",
        defaultValue = true
    )

    companion object {
        private const val PREFERENCE_NAME = "ui"
    }
}
