@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.github.kr328.clash.service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.service.data.Database
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.data.migrations.MIGRATIONS
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.security.ProfileCredentialsRepository
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.MainActivity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileCredentialsInstrumentedTest {
    @Test fun controlTokenIsStoredEncryptedAndRemainsUsable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            val settings = withTimeout(15_000) { Remote.service.remote.get().settings() }
            val token = settings.getControlToken()
            assertTrue(token.isNotBlank())
            assertEquals(token, settings.getControlToken())
            val xml = File(context.applicationInfo.dataDir, "shared_prefs/service.xml").readText()
            assertTrue(xml.contains("name=\"control_token\">gxcred:v1:"))
            assertFalse(xml.contains(token))
        } finally { activity.close() }
    }

    @Test fun existingVersionThreeCredentialsMigrateAndAreBoundToTheirProfile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "profile-credential-migration-${UUID.randomUUID()}"
        val id = UUID.randomUUID().toString()
        val secret = "AGE-SECRET-KEY-SYNTHETIC-TEST"
        fun open() = Room.databaseBuilder(context, Database::class.java, name).addMigrations(*MIGRATIONS).build()
        try {
            val initial = open()
            try {
                val sql = initial.openHelper.writableDatabase
                for (table in listOf("imported", "pending")) {
                    sql.execSQL("INSERT INTO $table (uuid, name, type, source, interval, upload, download, total, expire, createdAt, ageSecretKey, sourceRestricted) VALUES (?, 'test', 'File', '', 0, 0, 0, 0, 0, 0, ?, 0)", arrayOf(id, secret))
                }
                sql.version = 3
            } finally { initial.close() }
            val upgraded = open()
            try {
                val sql = upgraded.openHelper.writableDatabase
                for (table in listOf("imported", "pending")) {
                    sql.query("SELECT ageSecretKey FROM $table WHERE uuid = ?", arrayOf(id)).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        val stored = cursor.getString(0)
                        assertNotEquals(secret, stored)
                        assertEquals(secret, ProfileCredentialsRepository.decrypt(stored, table, id))
                        assertThrows(IllegalStateException::class.java) { ProfileCredentialsRepository.decrypt(stored, table, UUID.randomUUID().toString()) }
                    }
                }
            } finally { upgraded.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun normalDaoWritesCiphertextAndReadsTheOriginalSecret() = runBlocking {
        val id = UUID.randomUUID()
        val secret = "AGE-SECRET-KEY-WRITE-TEST"
        val dao = PendingDao()
        try {
            dao.insert(Pending(id, "test", Profile.Type.File, "", 0, 0, 0, 0, 0, ageSecretKey = secret))
            assertEquals(secret, dao.queryByUUID(id)?.ageSecretKey)
            val stored = Database.database.openPendingDao().queryByUUID(id)?.ageSecretKey
            assertNotNull(stored)
            assertNotEquals(secret, stored)
        } finally { dao.remove(id) }
    }
}
