package com.ivy.data.repository

import arrow.core.Either
import arrow.core.raise.either
import com.ivy.data.DataObserver
import com.ivy.data.DataWriteEvent
import com.ivy.data.DeleteOperation
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.db.entity.InvitationStatus as EntityInvitationStatus
import com.ivy.data.model.Invitation
import com.ivy.data.model.InvitationId
import com.ivy.data.model.InvitationStatus
import com.ivy.data.model.SharedAccountId
import com.ivy.data.repository.mapper.InvitationMapper
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvitationRepository @Inject constructor(
    private val db: IvyRoomDatabase,
    private val mapper: InvitationMapper,
    private val dataObserver: DataObserver,
) {
    suspend fun findById(id: InvitationId): Either<String, Invitation?> = either {
        val entity = db.invitationDao.findById(id.value)
        entity?.let { mapper.run { it.toDomain().bind() } }
    }

    suspend fun findByToken(token: String): Either<String, Invitation?> = either {
        val entity = db.invitationDao.findByToken(token)
        entity?.let { mapper.run { it.toDomain().bind() } }
    }

    suspend fun findBySharedAccountId(
        sharedAccountId: SharedAccountId
    ): Either<String, List<Invitation>> = either {
        val entities = db.invitationDao.findBySharedAccountId(sharedAccountId.value)
        entities.map { entity ->
            mapper.run { entity.toDomain().bind() }
        }
    }

    suspend fun findByEmailAndStatus(
        email: String,
        status: InvitationStatus
    ): Either<String, List<Invitation>> = either {
        val entityStatus = when (status) {
            InvitationStatus.PENDING -> EntityInvitationStatus.PENDING
            InvitationStatus.ACCEPTED -> EntityInvitationStatus.ACCEPTED
            InvitationStatus.EXPIRED -> EntityInvitationStatus.EXPIRED
            InvitationStatus.REVOKED -> EntityInvitationStatus.REVOKED
        }
        val entities = db.invitationDao.findByEmailAndStatus(email, entityStatus)
        entities.map { entity ->
            mapper.run { entity.toDomain().bind() }
        }
    }

    suspend fun findByStatus(
        status: InvitationStatus
    ): Either<String, List<Invitation>> = either {
        val entityStatus = when (status) {
            InvitationStatus.PENDING -> EntityInvitationStatus.PENDING
            InvitationStatus.ACCEPTED -> EntityInvitationStatus.ACCEPTED
            InvitationStatus.EXPIRED -> EntityInvitationStatus.EXPIRED
            InvitationStatus.REVOKED -> EntityInvitationStatus.REVOKED
        }
        val entities = db.invitationDao.findByStatus(entityStatus)
        entities.map { entity ->
            mapper.run { entity.toDomain().bind() }
        }
    }

    suspend fun findAll(): Either<String, List<Invitation>> = either {
        val entities = db.invitationDao.findAll()
        entities.map { entity ->
            mapper.run { entity.toDomain().bind() }
        }
    }

    suspend fun save(invitation: Invitation): Either<String, Unit> = either {
        val entity = mapper.run { invitation.toEntity() }
        db.writeInvitationDao.save(entity)
        dataObserver.post(DataWriteEvent.SaveInvitation(invitation))
    }

    suspend fun saveMany(invitations: List<Invitation>): Either<String, Unit> = either {
        val entities = invitations.map { mapper.run { it.toEntity() } }
        db.writeInvitationDao.saveMany(entities)
        invitations.forEach { invitation ->
            dataObserver.post(DataWriteEvent.SaveInvitation(invitation))
        }
    }

    suspend fun deleteById(id: InvitationId): Either<String, Unit> = either {
        db.writeInvitationDao.deleteById(id.value)
        dataObserver.post(
            DataWriteEvent.DeleteInvitation(
                DeleteOperation.Just(listOf(id))
            )
        )
    }

    suspend fun deleteAll(): Either<String, Unit> = either {
        db.writeInvitationDao.deleteAll()
    }

    suspend fun updateStatus(
        id: InvitationId,
        status: InvitationStatus
    ): Either<String, Unit> = either {
        val entityStatus = when (status) {
            InvitationStatus.PENDING -> EntityInvitationStatus.PENDING
            InvitationStatus.ACCEPTED -> EntityInvitationStatus.ACCEPTED
            InvitationStatus.EXPIRED -> EntityInvitationStatus.EXPIRED
            InvitationStatus.REVOKED -> EntityInvitationStatus.REVOKED
        }
        db.writeInvitationDao.updateStatus(id.value, entityStatus)
        // Fetch the updated entity to post the full data
        val entity = db.invitationDao.findById(id.value)
        entity?.let { inv ->
            val invitation = mapper.run { inv.toDomain().bind() }
            dataObserver.post(DataWriteEvent.SaveInvitation(invitation))
        }
    }

    suspend fun markAsAccepted(
        id: InvitationId,
        acceptedBy: String,
        acceptedAt: Instant = Instant.now()
    ): Either<String, Unit> = either {
        db.writeInvitationDao.markAsAccepted(
            id = id.value,
            status = EntityInvitationStatus.ACCEPTED,
            acceptedAt = acceptedAt,
            acceptedBy = acceptedBy
        )
        // Fetch the updated entity to post the full data
        val entity = db.invitationDao.findById(id.value)
        entity?.let { inv ->
            val invitation = mapper.run { inv.toDomain().bind() }
            dataObserver.post(DataWriteEvent.SaveInvitation(invitation))
        }
    }
}
