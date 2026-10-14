package com.github.kr328.clash.design.model

import com.github.kr328.clash.core.model.ProbeResult
import com.github.kr328.clash.design.settings.SettingsStore

/**
 * The streaming-service probe list — the one source of truth for the
 * latency screen and the dashboard services card.
 *
 * The probe itself is the core's [com.github.kr328.clash.core.Clash.probeUrl];
 * this catalog only decides which URLs are probed and how a response code is
 * classified.
 */
object ProbeServices {
    data class Service(
        val name: String,
        val url: String,
        val okCodes: Set<Int>,
        val restrictedCodes: Set<Int> = emptySet(),
    )

    val ALL: List<Service> = listOf(
        Service(
            "Google",
            "https://www.google.com/generate_204",
            okCodes = setOf(204),
        ),
        Service(
            "Cloudflare",
            "https://www.cloudflare.com/cdn-cgi/trace",
            okCodes = setOf(200),
        ),
        Service(
            "GitHub",
            "https://github.com/generate_204",
            okCodes = setOf(204),
        ),
        Service(
            "YouTube",
            "https://www.youtube.com/generate_204",
            okCodes = setOf(204),
        ),
        Service(
            "ChatGPT",
            "https://ios.chat.openai.com/public-api/mobile/server_status/v1",
            okCodes = setOf(200),
        ),
        Service(
            "Netflix",
            "https://www.netflix.com/title/81280792",
            okCodes = setOf(200),
            restrictedCodes = setOf(404),
        ),
        Service(
            "Disney+",
            "https://www.disneyplus.com/",
            okCodes = setOf(200),
        ),
    )

    val names: List<String> = ALL.map { it.name }

    private val byName: Map<String, Service> = ALL.associateBy { it.name }

    /**
     * The services to probe, in the configured order, honouring the
     * `probe_service_order` / `probe_service_enabled` settings.
     *
     * Until the user has saved the probe-services editor at least once
     * (the stored order is still empty), an empty enabled-set means
     * "every service" — the behaviour before the setting existed — so a
     * fresh install keeps probing all of them. Afterwards the set is
     * literal and may be empty.
     */
    fun resolve(settings: SettingsStore): List<Service> {
        val order = settings.probeServiceOrder(names)

        val enabled = settings.probeServiceEnabled

        val effective =
            if (enabled.isEmpty() && settings.probeServiceOrderRaw.isEmpty())
                order
            else
                order.filter { it in enabled }

        return effective.mapNotNull { byName[it] }
    }

    /**
     * Fold a raw probe result into a display availability. The latency
     * screen's classification is the reference; the dashboard shares it.
     */
    fun classify(service: Service, result: ProbeResult?): ServiceAvailability {
        if (result == null)
            return ServiceAvailability.FAILED

        if (result.code < 0)
            return ServiceAvailability.TIMEOUT

        return when (result.code) {
            in service.okCodes -> ServiceAvailability.OK
            in service.restrictedCodes -> ServiceAvailability.RESTRICTED
            else -> ServiceAvailability.BLOCKED
        }
    }
}
