package com.ivy.domain.usecase.invitation

import arrow.core.Either
import arrow.core.raise.either
import com.ivy.data.model.Invitation
import com.ivy.data.model.InvitationId
import com.ivy.data.model.InvitationStatus
import com.ivy.data.model.SharedAccountId
import com.ivy.data.repository.InvitationRepository
import com.ivy.data.sync.FirestoreInvitationRepository
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for creating secure invitations to shared accounts
 */
class CreateInvitationUseCase @Inject constructor(
    private val invitationRepository: InvitationRepository,
    private val firestoreInvitationRepository: FirestoreInvitationRepository,
) {
    /**
     * Creates a new invitation with a secure token
     * @param sharedAccountId The shared account to invite to
     * @param inviterUid The UID of the user creating the invitation
     * @param inviteeEmail The email of the user being invited
     * @param expirationDays Number of days until the invitation expires (default: 7)
     * @return Either an error message or the created invitation
     */
    suspend operator fun invoke(
        sharedAccountId: SharedAccountId,
        inviterUid: String,
        inviteeEmail: String,
        expirationDays: Long = 7
    ): Either<String, Invitation> = either {
        // Generate secure token
        val token = generateSecureToken()

        // Create invitation
        val invitation = Invitation(
            id = InvitationId(UUID.randomUUID()),
            sharedAccountId = sharedAccountId,
            inviterUid = inviterUid,
            inviteeEmail = inviteeEmail,
            token = token,
            status = InvitationStatus.PENDING,
            createdAt = Instant.now(),
            expiresAt = Instant.now().plus(expirationDays, ChronoUnit.DAYS),
            acceptedAt = null,
            acceptedBy = null
        )

        // Save to local repository
        invitationRepository.save(invitation).bind()

        // Also save to Firestore for cross-device access
        firestoreInvitationRepository.save(invitation).bind()

        invitation
    }

    /**
     * Generates a cryptographically secure random token
     * The token is URL-safe and 32 bytes (256 bits) long
     */
    private fun generateSecureToken(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
