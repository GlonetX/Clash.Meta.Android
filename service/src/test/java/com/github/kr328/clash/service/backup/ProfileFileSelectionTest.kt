package com.github.kr328.clash.service.backup

import org.junit.Assert.*
import org.junit.Test

class ProfileFileSelectionTest {
    @Test fun retainsUserFilesAndExcludesKnownRemoteCaches() {
        val remote = setOf("providers/remote/cache.yaml")
        assertTrue(includeProfileFile("config.yaml", remote))
        assertTrue(includeProfileFile("providers/manual/unreferenced.yaml", remote))
        assertTrue(includeProfileFile("providers/local.yaml", remote))
        assertFalse(includeProfileFile("providers/remote/cache.yaml", remote))
        assertFalse(includeProfileFile("profile-internal.tmp", remote))
    }
}
