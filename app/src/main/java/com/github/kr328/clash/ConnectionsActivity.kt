package com.github.kr328.clash

import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.ConnectionsDesign
import com.github.kr328.clash.util.withClash
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class ConnectionsActivity : BaseActivity<ConnectionsDesign>() {
    override suspend fun main() {
        val design = ConnectionsDesign(this, uiStore)

        setContentDesign(design)

        design.fetch()

        val ticker = ticker(TimeUnit.SECONDS.toMillis(2))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                }
                if (activityStarted) {
                    ticker.onReceive {
                        design.fetch()
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ConnectionsDesign.Request.Refresh -> design.fetch()
                        is ConnectionsDesign.Request.Close -> {
                            withContext(Dispatchers.IO) {
                                withClash {
                                    closeConnection(it.id)
                                }
                            }

                            design.fetch()
                        }
                        is ConnectionsDesign.Request.CloseAll -> {
                            withContext(Dispatchers.IO) {
                                withClash {
                                    it.ids.forEach { id -> closeConnection(id) }
                                }
                            }

                            design.fetch()
                        }
                    }
                }
            }
        }
    }

    private suspend fun ConnectionsDesign.fetch() {
        val connections = withClash {
            queryConnections()
        }

        update(connections)
    }
}