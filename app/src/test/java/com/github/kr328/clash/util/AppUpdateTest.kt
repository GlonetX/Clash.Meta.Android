package com.github.kr328.clash.util

import org.junit.Assert.*
import org.junit.Test

class AppUpdateTest {
    @Test fun prereleaseUsesItsAssetVersion() {
        assertEquals("12.12.0.3", AppUpdate.releaseVersion("Prerelease-alpha", "cmfa-12.12.0.3-alpha-arm64-v8a-release.apk"))
        assertNull(AppUpdate.releaseVersion("Prerelease-alpha", "cmfa-12.12.0.3-meta-arm64-v8a-release.apk"))
        assertNull(AppUpdate.releaseVersion("unexpected", "glonetx-12.12.2-alpha-arm64-v8a-release.apk"))
        assertEquals("12.12.1", AppUpdate.releaseVersion("v12.12.1", "glonetx-12.12.1-meta-arm64-v8a-release.apk"))
    }
    @Test fun versionComparisonHandlesAlphaAndRejectsMalformedVersions() {
        assertTrue(AppUpdate.isNewer("12.12.0.3.Alpha", "12.12.1"))
        assertTrue(AppUpdate.isNewer("12.12.0.Alpha", "12.12.0.3"))
        assertTrue(AppUpdate.isNewer("12.12.0.Alpha.debug", "12.12.0.3"))
        assertFalse(AppUpdate.isNewer("12.12.1", "12.12.1"))
        assertFalse(AppUpdate.isNewer("12.12.1", "Prerelease-alpha"))
        assertFalse(AppUpdate.isNewer("broken", "12.12.1"))
    }
}
