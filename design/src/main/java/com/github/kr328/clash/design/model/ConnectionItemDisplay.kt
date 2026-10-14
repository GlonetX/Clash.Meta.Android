package com.github.kr328.clash.design.model

import android.content.Context
import com.github.kr328.clash.core.model.ConnectionItem
import com.github.kr328.clash.design.util.format
import java.util.Date
import java.util.Locale

/**
 * Presentation wrapper for [ConnectionItem].
 *
 * Everything the connections list, its filter, its sort and its detail sheet
 * need is computed once per poll and stored here, so the layout can bind plain
 * strings and the detail sheet never has to touch a core model.
 */
class ConnectionItemDisplay(
    val id: String,
    val host: String,
    val network: String,
    val type: String,
    val subtitle: String,
    val traffic: String,
    val duration: String,
    /** Seconds since the connection was opened, frozen at poll time. */
    val durationSeconds: Long,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val startTimeSeconds: Long,
    /** Empty when the core could not attribute the connection to a process. */
    val process: String,
    /** Empty when no rule matched yet, otherwise `Rule(payload)`. */
    val rule: String,
    val ruleName: String,
    val rulePayload: String,
    /** Proxy outbounds the connection traverses, first hop first. */
    val chains: List<String>,
    /** Local endpoint that opened the connection. */
    val source: String,
    /** Host / SNI the client asked for. */
    val destination: String,
    /** Endpoint the core actually reached. */
    val remoteDestination: String,
    /** Lowercased haystack for the free-text search box. */
    val searchKey: String,
    private val onClose: (String) -> Unit,
) {
    /** Total bytes moved, used by the size sort. */
    val totalBytes: Long
        get() = uploadBytes + downloadBytes

    fun close() {
        onClose(id)
    }

    companion object {
        fun format(traffic: Long): String {
            if (traffic < 1024)
                return "${traffic}B"

            val kb = traffic / 1024.0

            if (kb < 1024)
                return String.format(Locale.getDefault(), "%.1fKB", kb)

            val mb = kb / 1024.0

            if (mb < 1024)
                return String.format(Locale.getDefault(), "%.1fMB", mb)

            return String.format(Locale.getDefault(), "%.2fGB", mb / 1024.0)
        }

        fun formatDuration(seconds: Long): String {
            if (seconds < 0)
                return ""

            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60

            return when {
                h > 0 -> "%d:%02d:%02d".format(Locale.getDefault(), h, m, s)
                m > 0 -> "%d:%02d".format(Locale.getDefault(), m, s)
                else -> "0:%02d".format(Locale.getDefault(), s)
            }
        }

        /**
         * The core joins proxy chains with an arrow; accept every separator it
         * might emit so a chain never renders as one long chip.
         */
        fun splitChains(chains: String?): List<String> {
            if (chains.isNullOrBlank())
                return emptyList()

            return chains
                .split("->", "→", ",", "|")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        /** `RULE(payload)` -> ("RULE", "payload"). */
        fun splitRule(rule: String?): Pair<String, String> {
            if (rule.isNullOrBlank())
                return "" to ""

            val open = rule.indexOf('(')
            if (open <= 0 || !rule.endsWith(")"))
                return rule to ""

            return rule.substring(0, open).trim() to rule.substring(open + 1, rule.length - 1)
        }
    }
}

fun ConnectionItem.toDisplay(
    context: Context,
    now: Long,
    onClose: (String) -> Unit,
): ConnectionItemDisplay {
    val process = this.process.orEmpty()
    val (ruleName, rulePayload) = ConnectionItemDisplay.splitRule(rule)
    val chains = ConnectionItemDisplay.splitChains(this.chains)

    val subtitleParts = mutableListOf<String>()

    process.takeIf { it.isNotBlank() }?.let { subtitleParts.add(it) }
    ruleName.takeIf { it.isNotBlank() }?.let { subtitleParts.add(it) }
    chains.joinToString(" → ").takeIf { it.isNotBlank() }?.let { subtitleParts.add(it) }

    if (destination.isNotBlank() && destination != host)
        subtitleParts.add(destination)

    val searchKey = listOf(
        network, type, host, destination, source, process, ruleName, rulePayload,
        chains.joinToString(" "),
    ).joinToString(" ").lowercase(Locale.getDefault())

    return ConnectionItemDisplay(
        id = id,
        host = host.ifBlank { destination },
        network = network.uppercase(Locale.getDefault()),
        type = type,
        subtitle = subtitleParts.joinToString(" · "),
        traffic = "↓ ${ConnectionItemDisplay.format(download)}  ↑ ${ConnectionItemDisplay.format(upload)}",
        duration = ConnectionItemDisplay.formatDuration(now - start),
        durationSeconds = now - start,
        uploadBytes = upload,
        downloadBytes = download,
        startTimeSeconds = start,
        process = process,
        rule = rule.orEmpty(),
        ruleName = ruleName,
        rulePayload = rulePayload,
        chains = chains,
        source = source,
        destination = host.ifBlank { destination },
        remoteDestination = destination,
        searchKey = searchKey,
        onClose = onClose,
    )
}

/** Absolute start time, formatted for the detail sheet. */
fun ConnectionItemDisplay.startTimeDisplay(context: Context): String =
    Date(startTimeSeconds * 1000).format(context)