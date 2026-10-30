package com.github.kr328.clash.core.bridge

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the packaged JNI bridge and the actual age implementation on Android. */
@RunWith(AndroidJUnit4::class)
class BackupBridgeInstrumentedTest {
    @Test fun encryptedBackupRoundTripRejectsWrongPasswordAndTampering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val work = File(context.cacheDir, "backup-bridge-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val plain = File(work, "source").apply { writeText("synthetic subscription payload") }
            val encrypted = File(work, "encrypted.age")
            val decoded = File(work, "decoded")
            val passphrase = "test-passphrase-123456".toByteArray()
            assertNull(BackupBridge.cryptFile(plain.path, encrypted.path, passphrase, false))
            assertFalse(encrypted.readBytes().toString(Charsets.UTF_8).contains("synthetic subscription payload"))
            assertNotNull(BackupBridge.cryptFile(encrypted.path, decoded.path, "different-password-123".toByteArray(), true))
            assertFalse(decoded.exists())
            assertNull(BackupBridge.cryptFile(encrypted.path, decoded.path, passphrase, true))
            assertEquals(plain.readText(), decoded.readText())
            val corrupt = encrypted.readBytes().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
            encrypted.writeBytes(corrupt)
            assertTrue(decoded.delete())
            assertNotNull(BackupBridge.cryptFile(encrypted.path, decoded.path, passphrase, true))
            assertFalse(decoded.exists())
        } finally { work.deleteRecursively() }
    }
}
