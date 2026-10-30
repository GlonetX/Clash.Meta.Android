@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.github.kr328.clash.service.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.settings.SettingsManager
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupArchiveInstrumentedTest {
    @Test fun archiveRetainsManualFilesExcludesRemoteCacheAndPassesRestorePreview() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Establish new-install settings before creating the test database.
        SettingsManager.get(context)
        val id = UUID.randomUUID()
        val profile = File(context.filesDir, "imported/$id")
        val work = File(context.cacheDir, "backup-archive-test-$id").apply { mkdirs() }
        val dao = ImportedDao()
        try {
            File(profile, "providers").mkdirs()
            File(profile, "config.yaml").writeText("""
                proxy-providers:
                  remote:
                    type: http
                    url: https://example.com/remote
                    path: remote.yaml
                  local:
                    type: file
                    path: local.yaml
            """.trimIndent())
            File(profile, "providers/remote.yaml").writeText("remote cache")
            File(profile, "providers/local.yaml").writeText("local provider")
            File(profile, "providers/manual.txt").writeText("unreferenced user file")
            val secret = "AGE-SECRET-KEY-BACKUP-TEST"
            dao.insert(Imported(id, "Synthetic", Profile.Type.File, "", 0, 0, 0, 0, 0, System.currentTimeMillis(), ageSecretKey = secret))
            val archive = BackupArchive.create(context, work)
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toSet()
                assertTrue(entries.contains("imported/$id/config.yaml"))
                assertTrue(entries.contains("imported/$id/providers/local.yaml"))
                assertTrue(entries.contains("imported/$id/providers/manual.txt"))
                assertFalse(entries.contains("imported/$id/providers/remote.yaml"))
                assertTrue(zip.getInputStream(zip.getEntry("snapshot.json")).bufferedReader().use { it.readText().contains(secret) })
            }
            val preview = BackupArchive.prepare(context, archive, File(work, "preview"))
            assertTrue(preview.imported >= 1)
            assertFalse(preview.legacy)
            assertEquals("unreferenced user file", File(profile, "providers/manual.txt").readText())
            val outside = File(work, "outside.txt").apply { writeText("outside private profile") }
            val symlink = File(profile, "providers/link.txt")
            android.system.Os.symlink(outside.absolutePath, symlink.absolutePath)
            val rejected = runCatching { BackupArchive.create(context, File(work, "symlink-case").apply { mkdirs() }) }
            assertTrue("A symlink inside the profile must not be exported", rejected.isFailure)
            assertEquals("unreferenced user file", File(profile, "providers/manual.txt").readText())
        } finally {
            dao.remove(id)
            profile.deleteRecursively()
            work.deleteRecursively()
        }
    }
}
