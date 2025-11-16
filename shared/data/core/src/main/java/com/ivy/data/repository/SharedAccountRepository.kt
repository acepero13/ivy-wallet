package com.ivy.data.repository

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.DataWriteEvent
import com.ivy.data.db.dao.read.SharedAccountDao
import com.ivy.data.db.dao.write.WriteSharedAccountDao
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedAccountId
import com.ivy.data.repository.mapper.SharedAccountMapper
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedAccountRepository @Inject constructor(
    private val mapper: SharedAccountMapper,
    private val sharedAccountDao: SharedAccountDao,
    private val writeSharedAccountDao: WriteSharedAccountDao,
    private val dispatchersProvider: DispatchersProvider,
    memoFactory: RepositoryMemoFactory,
) {
    private val memo = memoFactory.createMemo(
        getDataWriteSaveEvent = DataWriteEvent::SaveSharedAccounts,
        getDateWriteDeleteEvent = DataWriteEvent::DeleteSharedAccounts
    )

    suspend fun findById(id: SharedAccountId): SharedAccount? = memo.findById(
        id = id,
        findByIdOperation = { accountId ->
            sharedAccountDao.findById(accountId.value)?.let {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        }
    )

    suspend fun findAll(): List<SharedAccount> = memo.findAll(
        findAllOperation = {
            sharedAccountDao.findAll().mapNotNull {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        },
        sortMemo = { sortedByDescending { it.createdAt } }
    )

    suspend fun findByOwner(ownerUid: String): List<SharedAccount> =
        withContext(dispatchersProvider.io) {
            sharedAccountDao.findByOwner(ownerUid).mapNotNull {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        }

    suspend fun findBySyncStatus(synced: Boolean): List<SharedAccount> =
        withContext(dispatchersProvider.io) {
            sharedAccountDao.findBySyncStatus(synced).mapNotNull {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        }

    suspend fun save(value: SharedAccount): Unit = memo.save(value) {
        writeSharedAccountDao.save(
            with(mapper) { it.toEntity() }
        )
    }

    suspend fun saveMany(values: List<SharedAccount>): Unit = memo.saveMany(values) {
        writeSharedAccountDao.saveMany(
            it.map { with(mapper) { it.toEntity() } }
        )
    }

    suspend fun deleteById(id: SharedAccountId): Unit = memo.deleteById(id) {
        writeSharedAccountDao.deleteById(id.value)
    }

    suspend fun deleteAll(): Unit = memo.deleteAll(
        deleteAllOperation = writeSharedAccountDao::deleteAll
    )

    suspend fun updateSyncStatus(id: SharedAccountId, synced: Boolean) {
        withContext(dispatchersProvider.io) {
            writeSharedAccountDao.updateSyncStatus(id.value, synced)
        }
    }

    suspend fun updateRemoteId(id: SharedAccountId, remoteId: String, synced: Boolean = true) {
        withContext(dispatchersProvider.io) {
            writeSharedAccountDao.updateRemoteId(id.value, remoteId, synced)
        }
    }
}
