package com.ivy.domain.usecase.invitation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ivy.data.model.InvitationId
import com.ivy.data.model.InvitationStatus
import com.ivy.data.repository.InvitationRepository
import javax.inject.Inject

/**
 * Use case for revoking an invitation
 */
class RevokeInvitationUseCase @Inject constructor(
    private val invitationRepository: InvitationRepository,
) {
    /**
     * Revokes an invitation
     * @param invitationId The ID of the invitation to revoke
     * @param revokerUid The UID of the user revoking the invitation
     * @return Either an error message or Unit
     */
    suspend operator fun invoke(
        invitationId: InvitationId,
        revokerUid: String
    ): Either<String, Unit> = either {
        // Find invitation
        val invitation = invitationRepository.findById(invitationId).bind()
        ensure(invitation != null) { "Invitation not found" }

        // Validate that only the inviter can revoke
        ensure(invitation.inviterUid == revokerUid) {
            "Only the invitation creator can revoke it"
        }

        // Validate status
        ensure(invitation.status == InvitationStatus.PENDING) {
            "Can only revoke pending invitations (current status: ${invitation.status})"
        }

        // Update status to REVOKED
        invitationRepository.updateStatus(
            id = invitationId,
            status = InvitationStatus.REVOKED
        ).bind()
    }
}
