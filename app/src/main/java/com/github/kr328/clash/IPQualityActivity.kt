package com.github.kr328.clash

import com.github.kr328.clash.design.IPQualityDesign
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.isActive
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

    private suspend fun IPQualityDesign.fetch() {
        update(withClash { queryIPQuality("") })
    }
}
