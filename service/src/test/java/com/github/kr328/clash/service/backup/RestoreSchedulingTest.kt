package com.github.kr328.clash.service.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreSchedulingTest {
    @Test fun replayAfterInterruptedSchedulingReplacesOldAlarms() {
        val alarms = mutableSetOf("old-a", "old-b")
        val old = listOf("old-a", "old-b")
        val restored = listOf("new-a", "new-b")
        runCatching {
            rescheduleProfiles(old, restored, { alarms.remove(it) }) {
                alarms.add(it)
                if (it == "new-a") error("process interrupted")
            }
        }
        rescheduleProfiles(old, restored, { alarms.remove(it) }, { alarms.add(it) })
        assertEquals(restored.toSet(), alarms)
    }
}
