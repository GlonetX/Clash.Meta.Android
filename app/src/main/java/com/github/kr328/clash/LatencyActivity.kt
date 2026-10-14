package com.github.kr328.clash

import com.github.kr328.clash.design.LatencyDesign
import com.github.kr328.clash.design.model.ProbeServices
import com.github.kr328.clash.design.settings.SettingsStore
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class LatencyActivity : BaseActivity<LatencyDesign>() {
    private lateinit var services: List<ProbeServices.Service>

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

    private suspend fun LatencyDesign.testAll() {
        coroutineScope {
            services.forEachIndexed { index, service ->
                launch {
                    val result = withClash { probeUrl(service.url) }

                    update(index, ProbeServices.classify(service, result), result?.latency ?: -1)
                }
            }
        }
    }
}
