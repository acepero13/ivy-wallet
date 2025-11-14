package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ivy.data.db.entity.SharedAccountEntity
import java.util.UUID

@Dao
interface WriteSharedAccountDao {
    @Upsert
    suspend fun save(value: SharedAccountEntity)

    @Upsert
    suspend fun saveMany(values: List<SharedAccountEntity>)

    @Query("DELETE FROM shared_accounts WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("DELETE FROM shared_accounts")
    suspend fun deleteAll()

    @Query("UPDATE shared_accounts SET isSynced = :synced WHERE id = :id")
    suspend fun updateSyncStatus(id: UUID, synced: Boolean)

    @Query("UPDATE shared_accounts SET remoteId = :remoteId, isSynced = :synced WHERE id = :id")
    suspend fun updateRemoteId(id: UUID, remoteId: String, synced: Boolean = true)
}
