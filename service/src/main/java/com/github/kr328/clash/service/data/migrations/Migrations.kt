package com.github.kr328.clash.service.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.kr328.clash.service.security.ProfileCredentialsRepository

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE imported ADD COLUMN ageSecretKey TEXT")
        database.execSQL("ALTER TABLE pending ADD COLUMN ageSecretKey TEXT")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE imported ADD COLUMN sourceRestricted INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE pending ADD COLUMN sourceRestricted INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
        for (table in listOf("imported", "pending")) {
            val credentials = mutableListOf<Pair<String, String>>()
            database.query("SELECT uuid, ageSecretKey FROM $table WHERE ageSecretKey IS NOT NULL").use { cursor ->
                while (cursor.moveToNext()) credentials += cursor.getString(0) to cursor.getString(1)
            }
            credentials.forEach { (id, plaintext) ->
                database.execSQL("UPDATE $table SET ageSecretKey = ? WHERE uuid = ?", arrayOf(ProfileCredentialsRepository.encrypt(plaintext, table, id), id))
            }
        }
    }
}

val MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
)

val LEGACY_MIGRATION = ::migrationFromLegacy
