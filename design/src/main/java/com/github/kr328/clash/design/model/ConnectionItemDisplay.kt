package com.github.kr328.clash.design.model

import com.github.kr328.clash.core.model.ConnectionItem

/**
 * Presentation wrapper for [ConnectionItem]: pre-formats subtitle, traffic
 * and duration rows so the layout can bind plain strings.
 */
class ConnectionItemDisplay(
    val id: String,
    val host: String,
    val network: String,
    val subtitle: String,
    val traffic: String,
    val duration: String,
    val download: Long,
    private val onClose: (String) -> Unit,
) {
    fun close() {
        onClose(id)
    }

    companion object {
        fun format(traffic: Long): String {
            if (traffic < 1024)
                return "${traffic}B"

            val kb = traffic / 1024.0

            if (kb < 1024)
                return String.format("%.1fKB", kb)

            val mb = kb / 1024.0

            if (mb < 1024)
                return String.format("%.1fMB", mb)

            return String.format("%.2fGB", mb / 1024.0)
        }

        fun formatDuration(seconds: Long): String {
            if (seconds < 0)
                return ""

            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60

            return when {
                h > 0 -> "%d:%02d:%02d".format(h, m, s)
                m > 0 -> "%d:%02d".format(m, s)
                else -> "0:%02d".format(s)
            }
        }
    }
}

fun ConnectionItem.toDisplay(
    now: Long,
    onClose: (String) -> Unit,
): ConnectionItemDisplay {
    val subtitleParts = mutableListOf<String>()

    process?.takeIf { it.isNotBlank() }?.let { subtitleParts.add(it) }
    rule?.takeIf { it.isNotBlank() }?.let { subtitleParts.add(it) }
    chains?.takeIf { it.isNotBlank() }?.let { subtitleParts.add(it) }

    if (destination.isNotBlank() && destination != host)
        subtitleParts.add(destination)

    return ConnectionItemDisplay(
        id = id,
        host = host.ifBlank { destination },
        network = network.uppercase(),
        subtitle = subtitleParts.joinToString(" · "),
        traffic = "↓ ${ConnectionItemDisplay.format(download)}  ↑ ${ConnectionItemDisplay.format(upload)}",
        duration = ConnectionItemDisplay.formatDuration(now - start),
        download = download,
        onClose = onClose,
    )
}
