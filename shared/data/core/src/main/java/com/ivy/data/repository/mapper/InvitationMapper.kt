package com.ivy.data.repository.mapper

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ivy.data.db.entity.InvitationEntity
import com.ivy.data.model.Invitation
import com.ivy.data.model.InvitationId
import com.ivy.data.model.SharedAccountId
import com.ivy.data.db.entity.InvitationStatus as EntityInvitationStatus
import com.ivy.data.model.InvitationStatus as DomainInvitationStatus
import javax.inject.Inject

class InvitationMapper @Inject constructor() {

    fun InvitationEntity.toDomain(): Either<String, Invitation> = either {
        Invitation(
            id = InvitationId(id),
            sharedAccountId = SharedAccountId(sharedAccountId),
            inviterUid = inviterUid,
            inviteeEmail = inviteeEmail,
            token = token,
            status = status.toDomain(),
            createdAt = createdAt,
            expiresAt = expiresAt,
            acceptedAt = acceptedAt,
            acceptedBy = acceptedBy
        )
    }

    fun Invitation.toEntity(): InvitationEntity {
        return InvitationEntity(
            id = id.value,
            sharedAccountId = sharedAccountId.value,
            inviterUid = inviterUid,
            inviteeEmail = inviteeEmail,
            token = token,
            status = status.toEntity(),
            createdAt = createdAt,
            expiresAt = expiresAt,
            acceptedAt = acceptedAt,
            acceptedBy = acceptedBy
        )
    }

    private fun EntityInvitationStatus.toDomain(): DomainInvitationStatus {
        return when (this) {
            EntityInvitationStatus.PENDING -> DomainInvitationStatus.PENDING
            EntityInvitationStatus.ACCEPTED -> DomainInvitationStatus.ACCEPTED
            EntityInvitationStatus.EXPIRED -> DomainInvitationStatus.EXPIRED
            EntityInvitationStatus.REVOKED -> DomainInvitationStatus.REVOKED
        }
    }

    private fun DomainInvitationStatus.toEntity(): EntityInvitationStatus {
        return when (this) {
            DomainInvitationStatus.PENDING -> EntityInvitationStatus.PENDING
            DomainInvitationStatus.ACCEPTED -> EntityInvitationStatus.ACCEPTED
            DomainInvitationStatus.EXPIRED -> EntityInvitationStatus.EXPIRED
            DomainInvitationStatus.REVOKED -> EntityInvitationStatus.REVOKED
        }
    }
}
