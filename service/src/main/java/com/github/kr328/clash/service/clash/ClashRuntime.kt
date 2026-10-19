package com.github.kr328.clash.service.clash

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.clash.module.Module
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val globalLock = Mutex()

interface ClashRuntimeScope {
    fun <E, T : Module<E>> install(module: T): T
}

interface ClashRuntime {
    fun launch()
    fun requestGc()
}

fun CoroutineScope.clashRuntime(block: suspend ClashRuntimeScope.() -> Unit): ClashRuntime {
    return object : ClashRuntime {
        override fun launch() {
            launch(Dispatchers.IO) {
                globalLock.withLock {
                    Log.d("ClashRuntime: initialize")

                    val supervisor = SupervisorJob()
                    val moduleScope = CoroutineScope(coroutineContext + supervisor + Dispatchers.IO)

                    try {
                        Clash.reset()
                        Clash.clearOverride(Clash.OverrideSlot.Session)

                        val scope = object : ClashRuntimeScope {
                            override fun <E, T : Module<E>> install(module: T): T {
                                moduleScope.launch {
                                    try {
                                        module.execute()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Throwable) {
                                        Log.e("$module failed", e)
                                    }
                                }

                                return module
                            }
                        }

                        scope.block()

                        supervisor.cancel()
                        cancel()
                    } finally {
                        // Cancel module coroutines on the exceptional path as well.
                        supervisor.cancel()

                        withContext(NonCancellable) {
                            Clash.reset()
                            Clash.clearOverride(Clash.OverrideSlot.Session)

                            Log.d("ClashRuntime: destroyed")
                        }
                    }
                }
            }
        }

        override fun requestGc() {
            Clash.forceGc()
        }
    }
}