package com.github.kr328.clash

import com.github.kr328.clash.design.LatencyDesign
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class LatencyActivity : BaseActivity<LatencyDesign>() {
    companion object {
        private val SERVICES = listOf(
            "Google" to "https://www.google.com/generate_204",
            "Cloudflare" to "https://www.cloudflare.com/cdn-cgi/trace",
            "GitHub" to "https://github.com/generate_204",
            "YouTube" to "https://www.youtube.com/generate_204",
            "ChatGPT" to "https://chatgpt.com/cdn-cgi/trace",
            "Netflix" to "https://www.netflix.com/title/81280792",
        )
    }

    override suspend fun main() {
        val design = LatencyDesign(this, SERVICES.map { it.first })

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
            SERVICES.forEachIndexed { index, (_, url) ->
                launch {
                    val latency = withClash { testLatency(url) }

                    update(index, latency)
                }
            }
        }
    }
}
