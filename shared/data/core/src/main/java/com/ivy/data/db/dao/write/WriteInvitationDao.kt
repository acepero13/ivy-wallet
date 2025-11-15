package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivy.data.db.entity.InvitationEntity
import com.ivy.data.db.entity.InvitationStatus
import java.time.Instant
import java.util.UUID

@Dao
interface WriteInvitationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: InvitationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMany(entities: List<InvitationEntity>)

    @Query("DELETE FROM invitations WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("DELETE FROM invitations")
    suspend fun deleteAll()

    @Query("UPDATE invitations SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: UUID, status: InvitationStatus)

    @Query("UPDATE invitations SET status = :status, acceptedAt = :acceptedAt, acceptedBy = :acceptedBy WHERE id = :id")
    suspend fun markAsAccepted(id: UUID, status: InvitationStatus, acceptedAt: Instant, acceptedBy: String)
}
