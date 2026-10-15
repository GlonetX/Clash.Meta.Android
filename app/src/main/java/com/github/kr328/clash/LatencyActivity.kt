package com.github.kr328.clash

import com.github.kr328.clash.design.LatencyDesign
import com.github.kr328.clash.design.model.ProbeServices
import com.github.kr328.clash.design.settings.SettingsStore
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class LatencyActivity : BaseActivity<LatencyDesign>() {
    private lateinit var services: List<ProbeServices.Service>

    /** In-flight probe burst; a refresh supersedes the previous one. */
    private var testJob: Job? = null

    override suspend fun main() {
        services = ProbeServices.resolve(SettingsStore(this))

        val design = LatencyDesign(this, services.map { it.name })

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

    private fun LatencyDesign.testAll() {
        // Same reason as the dashboard's probe burst: awaiting these probes
        // inline would freeze this screen's `select` loop — and with it every
        // tap — for as long as the slowest probe takes. A newer burst
        // cancels the previous one so rows cannot be updated out of order.
        testJob?.cancel()

        testJob = this@LatencyActivity.launch {
            services.forEachIndexed { index, service ->
                launch {
                    val result = withClash { probeUrl(service.url) }

                    update(index, ProbeServices.classify(service, result), result?.latency ?: -1)
                }
            }
        }
    }
}
