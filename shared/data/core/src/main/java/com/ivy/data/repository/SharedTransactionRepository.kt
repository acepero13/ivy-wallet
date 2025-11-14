package com.ivy.data.repository

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.DataWriteEvent
import com.ivy.data.db.dao.read.SharedTransactionDao
import com.ivy.data.db.dao.write.WriteSharedTransactionDao
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionId
import com.ivy.data.repository.mapper.SharedTransactionMapper
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedTransactionRepository @Inject constructor(
    private val mapper: SharedTransactionMapper,
    private val sharedTransactionDao: SharedTransactionDao,
    private val writeSharedTransactionDao: WriteSharedTransactionDao,
    private val dispatchersProvider: DispatchersProvider,
    memoFactory: RepositoryMemoFactory,
) {
    private val memo = memoFactory.createMemo(
        getDataWriteSaveEvent = DataWriteEvent::SaveSharedTransactions,
        getDateWriteDeleteEvent = DataWriteEvent::DeleteSharedTransactions
    )

    suspend fun findById(id: SharedTransactionId): SharedTransaction? = memo.findById(
        id = id,
        findByIdOperation = {
            sharedTransactionDao.findById(id.value)?.let {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        }
    )

    suspend fun findBySharedAccountId(
        sharedAccountId: SharedAccountId,
        deleted: Boolean = false
    ): List<SharedTransaction> = withContext(dispatchersProvider.io) {
        sharedTransactionDao.findBySharedAccountId(sharedAccountId.value, deleted).mapNotNull {
            with(mapper) { it.toDomain() }.getOrNull()
        }
    }

    suspend fun findBySharedAccountAndUser(
        sharedAccountId: SharedAccountId,
        userId: String,
        deleted: Boolean = false
    ): List<SharedTransaction> = withContext(dispatchersProvider.io) {
        sharedTransactionDao.findBySharedAccountAndUser(
            sharedAccountId.value,
            userId,
            deleted
        ).mapNotNull {
            with(mapper) { it.toDomain() }.getOrNull()
        }
    }

    suspend fun findBySharedAccountAndTimeRange(
        sharedAccountId: SharedAccountId,
        startTime: Instant,
        endTime: Instant,
        deleted: Boolean = false
    ): List<SharedTransaction> = withContext(dispatchersProvider.io) {
        sharedTransactionDao.findBySharedAccountAndTimeRange(
            sharedAccountId.value,
            startTime,
            endTime,
            deleted
        ).mapNotNull {
            with(mapper) { it.toDomain() }.getOrNull()
        }
    }

    suspend fun findBySyncStatus(synced: Boolean): List<SharedTransaction> =
        withContext(dispatchersProvider.io) {
            sharedTransactionDao.findBySyncStatus(synced).mapNotNull {
                with(mapper) { it.toDomain() }.getOrNull()
            }
        }

    suspend fun countBySharedAccountId(
        sharedAccountId: SharedAccountId,
        deleted: Boolean = false
    ): Int = withContext(dispatchersProvider.io) {
        sharedTransactionDao.countBySharedAccountId(sharedAccountId.value, deleted)
    }

    suspend fun save(value: SharedTransaction): Unit = memo.save(value) {
        writeSharedTransactionDao.save(
            with(mapper) { it.toEntity() }
        )
    }

    suspend fun saveMany(values: List<SharedTransaction>): Unit = memo.saveMany(values) {
        writeSharedTransactionDao.saveMany(
            it.map { with(mapper) { it.toEntity() } }
        )
    }

    suspend fun deleteById(id: SharedTransactionId): Unit = memo.deleteById(id) {
        writeSharedTransactionDao.deleteById(id.value)
    }

    suspend fun markAsDeleted(id: SharedTransactionId, deleted: Boolean = true) {
        withContext(dispatchersProvider.io) {
            writeSharedTransactionDao.markAsDeleted(id.value, deleted)
        }
    }

    suspend fun deleteAll(): Unit = memo.deleteAll(
        deleteAllOperation = writeSharedTransactionDao::deleteAll
    )

    suspend fun updateSyncStatus(id: SharedTransactionId, synced: Boolean) {
        withContext(dispatchersProvider.io) {
            writeSharedTransactionDao.updateSyncStatus(id.value, synced)
        }
    }

    suspend fun updateRemoteId(id: SharedTransactionId, remoteId: String, synced: Boolean = true) {
        withContext(dispatchersProvider.io) {
            writeSharedTransactionDao.updateRemoteId(id.value, remoteId, synced)
        }
    }
}
