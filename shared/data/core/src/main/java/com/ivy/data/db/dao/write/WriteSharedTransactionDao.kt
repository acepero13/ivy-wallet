package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ivy.data.db.entity.SharedTransactionEntity
import java.util.UUID

@Dao
interface WriteSharedTransactionDao {
    @Upsert
    suspend fun save(value: SharedTransactionEntity)

    @Upsert
    suspend fun saveMany(values: List<SharedTransactionEntity>)

    @Query("DELETE FROM shared_transactions WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("DELETE FROM shared_transactions WHERE sharedAccountId = :sharedAccountId")
    suspend fun deleteBySharedAccountId(sharedAccountId: UUID)

    @Query("DELETE FROM shared_transactions")
    suspend fun deleteAll()

    @Query("UPDATE shared_transactions SET deleted = :deleted WHERE id = :id")
    suspend fun markAsDeleted(id: UUID, deleted: Boolean = true)

    @Query("UPDATE shared_transactions SET isSynced = :synced WHERE id = :id")
    suspend fun updateSyncStatus(id: UUID, synced: Boolean)

    @Query("UPDATE shared_transactions SET remoteId = :remoteId, isSynced = :synced WHERE id = :id")
    suspend fun updateRemoteId(id: UUID, remoteId: String, synced: Boolean = true)
}
