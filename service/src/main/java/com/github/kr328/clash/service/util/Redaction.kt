package com.github.kr328.clash.service.util

import java.net.URI

/**
 * Matches http(s) URLs up to whitespace, quotes or angle brackets.
 */
private val URL_PATTERN = Regex("""https?://[^\s"'<>]+""")

/**
 * Removes credentials and query strings from URLs embedded in an error
 * message before it reaches notifications or the UI.
 */
fun String.redactUrls(): String = URL_PATTERN.replace(this) { match ->
    redactUrl(match.value)
}

private fun redactUrl(url: String): String {
    // Keep only the part before the query string when URI parsing is not possible.
    val fallback = if (url.contains('?')) {
        url.substringBefore('?') + "?<redacted>"
    } else {
        url
    }

    return try {
        val uri = URI(url)

        // Relative or malformed values are left to the fallback above.
        val scheme = uri.scheme ?: return fallback
        val host = uri.host ?: return fallback

        buildString {
            append(scheme)
            append("://")

            if (uri.userInfo != null) {
                append("<redacted>@")
            }

            append(host)
            append(if (uri.port != -1) ":${uri.port}" else "")
            append(uri.rawPath ?: "")

            if (uri.rawQuery != null) {
                append("?<redacted>")
            }
        }
    } catch (e: Exception) {
        fallback
    }
}
