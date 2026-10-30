package com.github.kr328.clash.service.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceStoreTest {
    @Test fun legacyVpnChoiceAppliesBeforeMigration() {
        assertFalse(resolveVpnEnabled(null) { false })
        assertTrue(resolveVpnEnabled(null) { true })
    }

    @Test fun migratedChoiceTakesPrecedenceOverLegacy() {
        assertTrue(resolveVpnEnabled(true) { false })
        assertFalse(resolveVpnEnabled(false) { true })
    }
}
