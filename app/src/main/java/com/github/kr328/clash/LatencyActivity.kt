package com.github.kr328.clash

import com.github.kr328.clash.core.model.ProbeResult
import com.github.kr328.clash.design.LatencyDesign
import com.github.kr328.clash.design.model.ServiceAvailability
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class LatencyActivity : BaseActivity<LatencyDesign>() {
    companion object {
        private class Service(
            val name: String,
            val url: String,
            val okCodes: Set<Int>,
            val restrictedCodes: Set<Int> = emptySet(),
        )

        private val SERVICES = listOf(
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
    }

    override suspend fun main() {
        val design = LatencyDesign(this, SERVICES.map { it.name })

        setContentDesign(design)

        design.testAll()

        while (isActive) {
            select<Unit> {
                events.onReceive {
                }
                design.requests.onReceive {
                    when (it) {
                        LatencyDesign.Request.Refresh -> design.testAll()
                    }
                }
            }
        }
    }

    private suspend fun LatencyDesign.testAll() {
        coroutineScope {
            SERVICES.forEachIndexed { index, service ->
                launch {
                    val result = withClash { probeUrl(service.url) }

                    update(index, classify(service, result), result?.latency ?: -1)
                }
            }
        }
    }

    private fun classify(service: Service, result: ProbeResult?): ServiceAvailability {
        if (result == null)
            return ServiceAvailability.FAILED

        if (result.code < 0)
            return ServiceAvailability.TIMEOUT

        return when (result.code) {
            in service.okCodes -> ServiceAvailability.OK
            in service.restrictedCodes -> ServiceAvailability.RESTRICTED
            403, 451 -> ServiceAvailability.BLOCKED
            else -> ServiceAvailability.BLOCKED
        }
    }
}
