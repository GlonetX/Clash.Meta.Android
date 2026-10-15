package com.github.kr328.clash

import com.github.kr328.clash.design.IPQualityDesign
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class IPQualityActivity : BaseActivity<IPQualityDesign>() {
    override suspend fun main() {
        val design = IPQualityDesign(this)

        setContentDesign(design)

        design.fetch()

        while (isActive) {
            select<Unit> {
                events.onReceive {
                }
                design.requests.onReceive {
                    when (it) {
                        IPQualityDesign.Request.Refresh -> design.fetch()
                    }
                }
            }
        }
    }

    private fun IPQualityDesign.fetch() {
        // A quality check is a multi-second network call through the core:
        // awaiting it inside the `select` loop would freeze this screen —
        // and every tap on it — for the whole duration.
        this@IPQualityActivity.launch {
            update(withClash { queryIPQuality("") })
        }
    }
}
