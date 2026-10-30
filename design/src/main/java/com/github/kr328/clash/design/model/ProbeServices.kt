package com.github.kr328.clash.design.model


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

}
