package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.ivy.data.db.entity.SharedAccountEntity
import java.util.UUID

@Dao
interface SharedAccountDao {
    @Query("SELECT * FROM shared_accounts ORDER BY createdAt DESC")
    suspend fun findAll(): List<SharedAccountEntity>

    @Query("SELECT * FROM shared_accounts WHERE id = :id")
    suspend fun findById(id: UUID): SharedAccountEntity?

    @Query("SELECT * FROM shared_accounts WHERE owners LIKE '%' || :ownerUid || '%' ORDER BY createdAt DESC")
    suspend fun findByOwner(ownerUid: String): List<SharedAccountEntity>

    @Query("SELECT * FROM shared_accounts WHERE isSynced = :synced")
    suspend fun findBySyncStatus(synced: Boolean): List<SharedAccountEntity>

    @Query("SELECT * FROM shared_accounts WHERE remoteId = :remoteId")
    suspend fun findByRemoteId(remoteId: String): SharedAccountEntity?
}
