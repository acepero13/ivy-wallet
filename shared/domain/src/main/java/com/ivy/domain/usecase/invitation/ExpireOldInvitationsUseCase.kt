package com.ivy.domain.usecase.invitation

import arrow.core.Either
import arrow.core.raise.either
import com.ivy.data.model.InvitationStatus
import com.ivy.data.repository.InvitationRepository
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * Use case for expiring old invitations
 * This should be called periodically to mark expired invitations
 */
class ExpireOldInvitationsUseCase @Inject constructor(
    private val invitationRepository: InvitationRepository,
) {
    /**
     * Finds all pending invitations that have passed their expiration date
     * and marks them as expired
     * @return Either an error message or the count of expired invitations
     */
    suspend operator fun invoke(): Either<String, Int> = either {
        val now = Instant.now()

        // Get all pending invitations
        val pendingInvitations = invitationRepository.findByStatus(
            InvitationStatus.PENDING
        ).bind()

        // Filter expired ones
        val expiredInvitations = pendingInvitations.filter { invitation ->
            invitation.expiresAt.isBefore(now)
        }

        // Mark each as expired
        expiredInvitations.forEach { invitation ->
            try {
                invitationRepository.updateStatus(
                    id = invitation.id,
                    status = InvitationStatus.EXPIRED
                ).bind()
            } catch (e: Exception) {
                Timber.e(e, "Failed to expire invitation ${invitation.id}")
            }
        }

        Timber.d("Expired ${expiredInvitations.size} invitations")
        expiredInvitations.size
    }
}
