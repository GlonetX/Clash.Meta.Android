package com.github.kr328.clash.design.util

import android.content.Context
import android.net.Uri
import com.github.kr328.clash.common.compat.preferredLocale
import com.github.kr328.clash.design.R
import com.github.kr328.clash.service.model.Profile
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

/**
 * Formatting helpers for the profile card (adapter_profile.xml). Kept as
 * plain top-level functions so DataBinding can call them via
 * `ProfileFormatKt`.
 *
 * Note: the Profile model has no category/tag/notes fields; the meta line
 * surfaces what the model actually holds — type, source host and the
 * profile's own auto-update interval.
 */

private val MIN_AUTO_INTERVAL = TimeUnit.MINUTES.toMillis(15)

/** The profile card meta line: `type · host · auto-update interval`. */
fun Profile.describe(context: Context): String {
    val parts = mutableListOf<String>()

    parts.add(
        if (pending) {
            context.getString(R.string.format_type_unsaved, type.toString(context))
        } else {
            type.toString(context)
        }
    )

    if (this.type == Profile.Type.Url && source.isNotBlank()) {
        try {
            Uri.parse(source).host?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        } catch (_: Exception) {
            // ignore malformed sources
        }
    }

    autoUpdateLabel(context)?.let { parts.add(it) }

    return parts.joinToString(" · ")
}

/**
 * A short label for the profile's own update interval, or null when the
 * profile never auto-updates (File type or interval below the 15 minute
 * floor used by ProfileReceiver.scheduleNext).
 */
fun Profile.autoUpdateLabel(context: Context): String? {
    if (type == Profile.Type.File || interval < MIN_AUTO_INTERVAL)
        return null

    val hours = TimeUnit.MILLISECONDS.toHours(interval)

    return if (hours > 0) {
        context.getString(R.string.profile_update_interval_hours, hours)
    } else {
        context.getString(
            R.string.profile_update_interval_minutes,
            TimeUnit.MILLISECONDS.toMinutes(interval)
        )
    }
}

/** Date-only rendering for subscription expiry. */
fun Long.toDateOnly(context: Context): String {
    return SimpleDateFormat("yyyy-MM-dd", context.resources.configuration.preferredLocale)
        .format(this)
}
