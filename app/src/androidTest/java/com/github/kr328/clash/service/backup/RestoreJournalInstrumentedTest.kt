@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.github.kr328.clash.service.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.data.SelectionDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.settings.SettingsManager
import com.github.kr328.clash.service.store.ServiceStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Simulates the durable state left by a process death after rows and files were replaced. */
@RunWith(AndroidJUnit4::class)
class RestoreJournalInstrumentedTest {
    @Test fun preparedJournalRollsBackFilesRowsAndSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = SettingsManager.get(context)
        val dao = ImportedDao()
        val id = UUID.randomUUID()
        val temporaryId = UUID.randomUUID()
        val live = File(context.filesDir, "imported")
        val profileDir = File(live, id.toString()).apply { mkdirs() }
        val root = BackupArchive.root(context)
        val rollback = File(root, "rollback")
        val journal = File(root, "restore-journal.json")
        check(!rollback.exists() && !journal.exists()) { "An unrelated restore is already in progress" }
        try {
            File(profileDir, "config.yaml").writeText("proxies: []\n")
            dao.insert(Imported(id, "Original", Profile.Type.File, "", 0, 0, 0, 0, 0, System.currentTimeMillis(), ageSecretKey = "AGE-SECRET-KEY-ROLLBACK"))
            val override = File(context.filesDir, "clash/override.json")
            val old = BackupSnapshot(imported = dao.queryAll(), pending = PendingDao().queryAll(), selections = SelectionDao().queryAll(),
                activeProfile = ServiceStore(context).activeProfile?.toString(), settings = manager.exportRecoverySettings(),
                override = if (override.exists()) override.readText() else "{}")
            rollback.mkdirs()
            BackupArchive.atomicWrite(File(rollback, "snapshot.json"), Json.encodeToString(old))
            BackupArchive.atomicWrite(journal, """{"phase":"Prepared","importedExisted":true,"pendingExisted":${File(context.filesDir, "pending").exists()},"overrideExisted":${override.exists()}}""")
            check(live.renameTo(File(rollback, "imported")))
            live.mkdirs()
            File(live, "partial-replacement").writeText("incomplete")
            dao.remove(id)
            dao.insert(Imported(temporaryId, "Replacement", Profile.Type.File, "", 0, 0, 0, 0, 0, System.currentTimeMillis()))
            BackupArchive.recover(context)
            assertTrue(File(profileDir, "config.yaml").isFile)
            assertFalse(File(live, "partial-replacement").exists())
            assertEquals("AGE-SECRET-KEY-ROLLBACK", dao.queryByUUID(id)?.ageSecretKey)
            assertNull(dao.queryByUUID(temporaryId))
            assertFalse(journal.exists())
            assertFalse(rollback.exists())
        } finally {
            if (journal.exists()) BackupArchive.recover(context)
            dao.remove(id)
            dao.remove(temporaryId)
            profileDir.deleteRecursively()
        }
    }
}
