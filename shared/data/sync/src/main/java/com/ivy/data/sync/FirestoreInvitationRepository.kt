package com.ivy.data.sync

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ivy.data.model.Invitation
import com.ivy.data.model.InvitationId
import com.ivy.data.model.InvitationStatus
import com.ivy.data.model.SharedAccountId
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-based repository for managing invitations that need to be shared across devices/users
 */
@Singleton
class FirestoreInvitationRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val COLLECTION_INVITATIONS = "invitations"
        private const val COLLECTION_SHARED_ACCOUNTS = "sharedAccounts"
    }

    suspend fun save(invitation: Invitation): Either<String, Unit> = either {
        try {
            val data = mapOf(
                "id" to invitation.id.value.toString(),
                "sharedAccountId" to invitation.sharedAccountId.value.toString(),
                "inviterUid" to invitation.inviterUid,
                "inviteeEmail" to invitation.inviteeEmail,
                "token" to invitation.token,
                "status" to invitation.status.name,
                "createdAt" to invitation.createdAt.toEpochMilli(),
                "expiresAt" to invitation.expiresAt.toEpochMilli(),
                "acceptedAt" to invitation.acceptedAt?.toEpochMilli(),
                "acceptedBy" to invitation.acceptedBy
            )

            firestore.collection(COLLECTION_INVITATIONS)
                .document(invitation.id.value.toString())
                .set(data, SetOptions.merge())
                .await()

            Timber.d("Saved invitation ${invitation.id.value} to Firestore")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save invitation to Firestore")
            raise("Failed to save invitation: ${e.message}")
        }
    }

    suspend fun findByToken(token: String): Either<String, Invitation?> = either {
        try {
            Timber.d("Searching for invitation with token: ${token.take(20)}...")

            val snapshot = firestore.collection(COLLECTION_INVITATIONS)
                .whereEqualTo("token", token)
                .get()
                .await()

            if (snapshot.isEmpty) {
                Timber.w("No invitation found with token: ${token.take(20)}...")
                return@either null
            }

            val doc = snapshot.documents.first()
            val data = doc.data ?: run {
                Timber.w("Invitation document ${doc.id} has no data")
                return@either null
            }

            Timber.d("Found invitation: ${doc.id}")

            Invitation(
                id = InvitationId(UUID.fromString(data["id"] as String)),
                sharedAccountId = SharedAccountId(UUID.fromString(data["sharedAccountId"] as String)),
                inviterUid = data["inviterUid"] as String,
                inviteeEmail = data["inviteeEmail"] as String,
                token = data["token"] as String,
                status = InvitationStatus.valueOf(data["status"] as String),
                createdAt = Instant.ofEpochMilli(data["createdAt"] as Long),
                expiresAt = Instant.ofEpochMilli(data["expiresAt"] as Long),
                acceptedAt = (data["acceptedAt"] as? Long)?.let { Instant.ofEpochMilli(it) },
                acceptedBy = data["acceptedBy"] as? String
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to find invitation by token")
            raise("Failed to find invitation: ${e.message}")
        }
    }

    suspend fun findById(id: InvitationId): Either<String, Invitation?> = either {
        try {
            val doc = firestore.collection(COLLECTION_INVITATIONS)
                .document(id.value.toString())
                .get()
                .await()

            if (!doc.exists()) {
                return@either null
            }

            val data = doc.data ?: return@either null

            Invitation(
                id = InvitationId(UUID.fromString(data["id"] as String)),
                sharedAccountId = SharedAccountId(UUID.fromString(data["sharedAccountId"] as String)),
                inviterUid = data["inviterUid"] as String,
                inviteeEmail = data["inviteeEmail"] as String,
                token = data["token"] as String,
                status = InvitationStatus.valueOf(data["status"] as String),
                createdAt = Instant.ofEpochMilli(data["createdAt"] as Long),
                expiresAt = Instant.ofEpochMilli(data["expiresAt"] as Long),
                acceptedAt = (data["acceptedAt"] as? Long)?.let { Instant.ofEpochMilli(it) },
                acceptedBy = data["acceptedBy"] as? String
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to find invitation by ID")
            raise("Failed to find invitation: ${e.message}")
        }
    }

    suspend fun markAsAccepted(
        id: InvitationId,
        acceptedBy: String,
        acceptedAt: Instant = Instant.now()
    ): Either<String, Unit> = either {
        try {
            val updates = mapOf(
                "status" to InvitationStatus.ACCEPTED.name,
                "acceptedAt" to acceptedAt.toEpochMilli(),
                "acceptedBy" to acceptedBy
            )

            firestore.collection(COLLECTION_INVITATIONS)
                .document(id.value.toString())
                .update(updates)
                .await()

            Timber.d("Marked invitation ${id.value} as accepted by $acceptedBy")

            // Also add the accepting user to the shared account's owners
            val invitation = findById(id).bind()
            ensure(invitation != null) { "Invitation not found after updating" }

            addOwnerToSharedAccount(invitation.sharedAccountId, acceptedBy).bind()
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark invitation as accepted")
            raise("Failed to accept invitation: ${e.message}")
        }
    }

    private suspend fun addOwnerToSharedAccount(
        sharedAccountId: SharedAccountId,
        userId: String
    ): Either<String, Unit> = either {
        try {
            val accountRef = firestore.collection(COLLECTION_SHARED_ACCOUNTS)
                .document(sharedAccountId.value.toString())

            val doc = accountRef.get().await()
            val currentOwners = (doc.get("owners") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            if (userId !in currentOwners) {
                val newOwners = currentOwners + userId
                accountRef.update("owners", newOwners).await()
                Timber.d("Added user $userId to shared account ${sharedAccountId.value}")
            } else {
                Timber.d("User $userId is already an owner of shared account ${sharedAccountId.value}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to add owner to shared account")
            raise("Failed to add owner: ${e.message}")
        }
    }

    suspend fun fetchSharedAccount(sharedAccountId: SharedAccountId): Either<String, Map<String, Any>?> = either {
        try {
            val doc = firestore.collection(COLLECTION_SHARED_ACCOUNTS)
                .document(sharedAccountId.value.toString())
                .get()
                .await()

            if (!doc.exists()) {
                Timber.w("Shared account ${sharedAccountId.value} not found in Firestore")
                return@either null
            }

            doc.data
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch shared account from Firestore")
            raise("Failed to fetch shared account: ${e.message}")
        }
    }

    suspend fun saveSharedAccount(
        id: SharedAccountId,
        name: String,
        currency: String,
        owners: List<String>,
        createdBy: String,
        createdAt: Long,
        updatedAt: Long,
        linkedAccountId: String?
    ): Either<String, Unit> = either {
        try {
            val data = mutableMapOf<String, Any>(
                "id" to id.value.toString(),
                "name" to name,
                "currency" to currency,
                "owners" to owners,
                "createdBy" to createdBy,
                "createdAt" to createdAt,
                "updatedAt" to updatedAt
            )

            if (linkedAccountId != null) {
                data["linkedAccountId"] = linkedAccountId
            }

            firestore.collection(COLLECTION_SHARED_ACCOUNTS)
                .document(id.value.toString())
                .set(data, SetOptions.merge())
                .await()

            Timber.d("Saved shared account ${id.value} to Firestore")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save shared account to Firestore")
            raise("Failed to save shared account: ${e.message}")
        }
    }
}
