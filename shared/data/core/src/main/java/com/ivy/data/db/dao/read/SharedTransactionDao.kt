package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.ivy.data.db.entity.SharedTransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface SharedTransactionDao {
    @Query("SELECT * FROM shared_transactions WHERE sharedAccountId = :sharedAccountId AND deleted = :deleted ORDER BY time DESC")
    suspend fun findBySharedAccountId(
        sharedAccountId: UUID,
        deleted: Boolean = false
    ): List<SharedTransactionEntity>

    @Query("SELECT * FROM shared_transactions WHERE sharedAccountId = :sharedAccountId AND deleted = :deleted ORDER BY time DESC")
    fun observeBySharedAccountId(
        sharedAccountId: UUID,
        deleted: Boolean = false
    ): Flow<List<SharedTransactionEntity>>

    @Query("SELECT * FROM shared_transactions WHERE id = :id")
    suspend fun findById(id: UUID): SharedTransactionEntity?

    @Query("SELECT * FROM shared_transactions WHERE sharedAccountId = :sharedAccountId AND createdBy = :userId AND deleted = :deleted ORDER BY time DESC")
    suspend fun findBySharedAccountAndUser(
        sharedAccountId: UUID,
        userId: String,
        deleted: Boolean = false
    ): List<SharedTransactionEntity>

    @Query("SELECT * FROM shared_transactions WHERE sharedAccountId = :sharedAccountId AND time BETWEEN :startTime AND :endTime AND deleted = :deleted ORDER BY time DESC")
    suspend fun findBySharedAccountAndTimeRange(
        sharedAccountId: UUID,
        startTime: Instant,
        endTime: Instant,
        deleted: Boolean = false
    ): List<SharedTransactionEntity>

    @Query("SELECT * FROM shared_transactions WHERE isSynced = :synced")
    suspend fun findBySyncStatus(synced: Boolean): List<SharedTransactionEntity>

    @Query("SELECT * FROM shared_transactions WHERE remoteId = :remoteId")
    suspend fun findByRemoteId(remoteId: String): SharedTransactionEntity?

    @Query("SELECT COUNT(*) FROM shared_transactions WHERE sharedAccountId = :sharedAccountId AND deleted = :deleted")
    suspend fun countBySharedAccountId(
        sharedAccountId: UUID,
        deleted: Boolean = false
    ): Int
}
