package com.ivy.domain.usecase.invitation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ivy.data.model.Invitation
import com.ivy.data.model.InvitationStatus
import com.ivy.data.repository.InvitationRepository
import java.time.Instant
import javax.inject.Inject

/**
 * Use case for accepting an invitation to a shared account
 */
class AcceptInvitationUseCase @Inject constructor(
    private val invitationRepository: InvitationRepository,
) {
    /**
     * Accepts an invitation using a token
     * @param token The invitation token
     * @param acceptedByUid The UID of the user accepting the invitation
     * @return Either an error message or the accepted invitation
     */
    suspend operator fun invoke(
        token: String,
        acceptedByUid: String
    ): Either<String, Invitation> = either {
        // Find invitation by token
        val invitation = invitationRepository.findByToken(token).bind()
        ensure(invitation != null) { "Invitation not found" }

        // Validate invitation status
        ensure(invitation.status == InvitationStatus.PENDING) {
            "Invitation is not pending (status: ${invitation.status})"
        }

        // Validate expiration
        ensure(!invitation.expiresAt.isBefore(Instant.now())) {
            "Invitation has expired"
        }

        // Mark as accepted
        invitationRepository.markAsAccepted(
            id = invitation.id,
            acceptedBy = acceptedByUid,
            acceptedAt = Instant.now()
        ).bind()

        // Return updated invitation
        val updatedInvitation = invitationRepository.findById(invitation.id).bind()
        ensure(updatedInvitation != null) { "Failed to retrieve updated invitation" }

        updatedInvitation
    }
}
