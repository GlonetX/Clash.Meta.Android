package com.github.kr328.clash.service.settings

import org.junit.Assert.*
import org.junit.Test

class LegacySettingsMigrationTest {
    @Test fun freshOrClearedInstallUsesPrivateDefaults() {
        val existing = LegacySettingsMigration.isLegacyInstall(emptyMap<String, Any>(), emptyMap<String, Any>(), emptyMap<String, Any>(), false)
        val snapshot = LegacySettingsMigration.readMaps(emptyMap<String, Any>(), emptyMap<String, Any>(), emptyMap<String, Any>(), existing)
        assertEquals(Ipv6Mode.Block, snapshot.desired.ipv6Mode)
        assertFalse(snapshot.desired.allowBypass)
    }
    @Test fun upgradedInstallPreservesHistoricalDefaultsWithoutExplicitKeys() {
        val existing = LegacySettingsMigration.isLegacyInstall(emptyMap<String, Any>(), emptyMap<String, Any>(), emptyMap<String, Any>(), true)
        val snapshot = LegacySettingsMigration.readMaps(emptyMap<String, Any>(), emptyMap<String, Any>(), emptyMap<String, Any>(), existing)
        assertEquals(Ipv6Mode.Direct, snapshot.desired.ipv6Mode)
        assertTrue(snapshot.desired.allowBypass)
    }
    @Test fun explicitLegacyValuesSurviveMigration() {
        val service = mapOf("allow_ipv6" to true, "allow_bypass" to false)
        val existing = LegacySettingsMigration.isLegacyInstall(emptyMap<String, Any>(), emptyMap<String, Any>(), service, false)
        val snapshot = LegacySettingsMigration.readMaps(emptyMap<String, Any>(), emptyMap<String, Any>(), service, existing)
        assertEquals(Ipv6Mode.Proxy, snapshot.desired.ipv6Mode)
        assertFalse(snapshot.desired.allowBypass)
    }
}
