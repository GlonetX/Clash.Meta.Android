package com.github.kr328.clash.service.data

import com.github.kr328.clash.service.security.ProfileCredentialsRepository
import java.util.UUID

private class ProtectedImportedDao(private val raw: ImportedDao) : ImportedDao by raw {
    private fun protect(row: Imported) = row.copy(ageSecretKey = ProfileCredentialsRepository.encrypt(row.ageSecretKey, "imported", row.uuid.toString()))
    private fun reveal(row: Imported) = row.copy(ageSecretKey = ProfileCredentialsRepository.decrypt(row.ageSecretKey, "imported", row.uuid.toString()))
    override suspend fun queryByUUID(uuid: UUID) = raw.queryByUUID(uuid)?.let(::reveal)
    override suspend fun queryAll() = raw.queryAll().map(::reveal)
    override suspend fun insert(row: Imported) = raw.insert(protect(row))
    override suspend fun insertAll(rows: List<Imported>) = raw.insertAll(rows.map(::protect))
    override suspend fun update(row: Imported) = raw.update(protect(row))
}

private class ProtectedPendingDao(private val raw: PendingDao) : PendingDao by raw {
    private fun protect(row: Pending) = row.copy(ageSecretKey = ProfileCredentialsRepository.encrypt(row.ageSecretKey, "pending", row.uuid.toString()))
    private fun reveal(row: Pending) = row.copy(ageSecretKey = ProfileCredentialsRepository.decrypt(row.ageSecretKey, "pending", row.uuid.toString()))
    override suspend fun queryByUUID(uuid: UUID) = raw.queryByUUID(uuid)?.let(::reveal)
    override suspend fun queryAll() = raw.queryAll().map(::reveal)
    override suspend fun insert(row: Pending) = raw.insert(protect(row))
    override suspend fun insertAll(rows: List<Pending>) = raw.insertAll(rows.map(::protect))
    override suspend fun update(row: Pending) = raw.update(protect(row))
}

fun ImportedDao(): ImportedDao = ProtectedImportedDao(Database.database.openImportedDao())
fun PendingDao(): PendingDao = ProtectedPendingDao(Database.database.openPendingDao())
fun SelectionDao(): SelectionDao = Database.database.openSelectionProxyDao()
