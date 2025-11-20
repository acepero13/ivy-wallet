package com.ivy.domain.usecase.invitation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ivy.data.model.Invitation
import com.ivy.data.model.InvitationStatus
import com.ivy.data.repository.InvitationRepository
import com.ivy.data.repository.SharedAccountRepository
import com.ivy.data.sync.FirestoreInvitationRepository
import java.time.Instant
import javax.inject.Inject

/**
 * Use case for accepting an invitation to a shared account
 */
class AcceptInvitationUseCase @Inject constructor(
    private val invitationRepository: InvitationRepository,
    private val firestoreInvitationRepository: FirestoreInvitationRepository,
    private val sharedAccountRepository: SharedAccountRepository,
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
        // Find invitation by token from Firestore (for cross-device access)
        val invitation = firestoreInvitationRepository.findByToken(token).bind()
        ensure(invitation != null) { "Invitation not found" }

        // Validate invitation status
        ensure(invitation.status == InvitationStatus.PENDING) {
            "Invitation is not pending (status: ${invitation.status})"
        }

        // Validate expiration
        ensure(!invitation.expiresAt.isBefore(Instant.now())) {
            "Invitation has expired"
        }

        // Mark as accepted in Firestore (will also add user to shared account owners)
        firestoreInvitationRepository.markAsAccepted(
            id = invitation.id,
            acceptedBy = acceptedByUid,
            acceptedAt = Instant.now()
        ).bind()

        // Also save to local repository
        invitationRepository.markAsAccepted(
            id = invitation.id,
            acceptedBy = acceptedByUid,
            acceptedAt = Instant.now()
        ).bind()

        // Fetch the shared account from Firestore and save it locally
        val sharedAccountData = firestoreInvitationRepository.fetchSharedAccount(invitation.sharedAccountId).bind()
        if (sharedAccountData != null) {
            try {
                // Convert Firestore data to SharedAccount domain model
                val sharedAccount = com.ivy.data.model.SharedAccount(
                    id = invitation.sharedAccountId,
                    name = com.ivy.data.model.primitive.NotBlankTrimmedString.unsafe(
                        sharedAccountData["name"] as? String ?: "Shared Account"
                    ),
                    currency = com.ivy.data.model.primitive.AssetCode.unsafe(
                        sharedAccountData["currency"] as? String ?: "USD"
                    ),
                    owners = (sharedAccountData["owners"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    createdBy = sharedAccountData["createdBy"] as? String ?: "",
                    createdAt = Instant.ofEpochMilli(sharedAccountData["createdAt"] as? Long ?: 0L),
                    updatedAt = Instant.ofEpochMilli(sharedAccountData["updatedAt"] as? Long ?: 0L),
                    linkedAccountId = (sharedAccountData["linkedAccountId"] as? String)?.let {
                        com.ivy.data.model.AccountId(java.util.UUID.fromString(it))
                    }
                )

                // Save to local repository
                sharedAccountRepository.save(sharedAccount)
            } catch (e: Exception) {
                // Log but don't fail - the account can be synced later
                android.util.Log.e("AcceptInvitation", "Failed to save shared account locally", e)
            }
        }

        // Return updated invitation
        val updatedInvitation = firestoreInvitationRepository.findById(invitation.id).bind()
        ensure(updatedInvitation != null) { "Failed to retrieve updated invitation" }

        updatedInvitation
    }
}
