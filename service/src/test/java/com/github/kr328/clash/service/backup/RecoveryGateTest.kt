package com.github.kr328.clash.service.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecoveryGateTest {
    @Test fun failureBlocksWritesAndRecoveryIsRetriedOnNextEntry() = runBlocking {
        var attempts = 0
        var writes = 0
        suspend fun recover() { if (++attempts < 3) error("recovery failed") }
        repeat(2) {
            assertTrue(runCatching { recoverBeforeOperation(::recover) { writes++ } }.isFailure)
            assertEquals(0, writes)
        }
        recoverBeforeOperation(::recover) { writes++ }
        assertEquals(3, attempts)
        assertEquals(1, writes)
    }
}
