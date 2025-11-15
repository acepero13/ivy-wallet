package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.ivy.data.db.entity.InvitationEntity
import com.ivy.data.db.entity.InvitationStatus
import java.util.UUID

@Dao
interface InvitationDao {
    @Query("SELECT * FROM invitations WHERE id = :id")
    suspend fun findById(id: UUID): InvitationEntity?

    @Query("SELECT * FROM invitations WHERE token = :token")
    suspend fun findByToken(token: String): InvitationEntity?

    @Query("SELECT * FROM invitations WHERE sharedAccountId = :sharedAccountId")
    suspend fun findBySharedAccountId(sharedAccountId: UUID): List<InvitationEntity>

    @Query("SELECT * FROM invitations WHERE inviteeEmail = :email AND status = :status")
    suspend fun findByEmailAndStatus(email: String, status: InvitationStatus): List<InvitationEntity>

    @Query("SELECT * FROM invitations WHERE status = :status")
    suspend fun findByStatus(status: InvitationStatus): List<InvitationEntity>

    @Query("SELECT * FROM invitations")
    suspend fun findAll(): List<InvitationEntity>
}
