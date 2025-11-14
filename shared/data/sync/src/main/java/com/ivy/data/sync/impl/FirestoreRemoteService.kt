package com.ivy.data.sync.impl

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.ivy.data.sync.RemoteService
import com.ivy.data.sync.SyncOperation
import com.ivy.data.sync.SyncOperationType
import com.ivy.data.sync.SyncResult
import com.ivy.data.sync.SyncSubscription
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore implementation of RemoteService.
 *
 * Data structure in Firestore:
 * - users/{userId} - user profile
 * - sharedAccounts/{sharedAccountId} - shared account metadata
 * - sharedAccounts/{sharedAccountId}/transactions/{transactionId} - transactions
 * - invitations/{inviteId} - invite links
 */
@Singleton
class FirestoreRemoteService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : RemoteService {

    companion object {
        private const val COLLECTION_SHARED_ACCOUNTS = "sharedAccounts"
        private const val COLLECTION_TRANSACTIONS = "transactions"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_INVITATIONS = "invitations"

        private const val ENTITY_TYPE_SHARED_ACCOUNT = "sharedAccount"
        private const val ENTITY_TYPE_SHARED_TRANSACTION = "sharedTransaction"

        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_DELETED = "deleted"
        private const val FIELD_OWNERS = "owners"
    }

    override suspend fun pushOperation(operation: SyncOperation): SyncResult {
        return try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                return SyncResult.Failure(
                    operationId = operation.id,
                    error = IllegalStateException("User not authenticated")
                )
            }

            when (operation.entityType) {
                ENTITY_TYPE_SHARED_ACCOUNT -> pushSharedAccount(operation, userId)
                ENTITY_TYPE_SHARED_TRANSACTION -> pushSharedTransaction(operation, userId)
                else -> SyncResult.Failure(
                    operationId = operation.id,
                    error = IllegalArgumentException("Unknown entity type: ${operation.entityType}")
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to push operation ${operation.id}")
            SyncResult.Failure(operation.id, e)
        }
    }

    private suspend fun pushSharedAccount(operation: SyncOperation, userId: String): SyncResult {
        val accountRef = firestore.collection(COLLECTION_SHARED_ACCOUNTS)
            .document(operation.entityId)

        return when (operation.type) {
            SyncOperationType.CREATE, SyncOperationType.UPDATE -> {
                val data = operation.data?.toMutableMap() ?: mutableMapOf()
                data[FIELD_UPDATED_AT] = FieldValue.serverTimestamp()

                accountRef.set(data, SetOptions.merge()).await()
                SyncResult.Success(operation.id)
            }
            SyncOperationType.DELETE -> {
                // Soft delete: mark as deleted
                accountRef.update(
                    mapOf(
                        FIELD_DELETED to true,
                        FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                    )
                ).await()
                SyncResult.Success(operation.id)
            }
        }
    }

    private suspend fun pushSharedTransaction(operation: SyncOperation, userId: String): SyncResult {
        val data = operation.data ?: return SyncResult.Failure(
            operation.id,
            IllegalArgumentException("Transaction data is required")
        )

        val sharedAccountId = data["sharedAccountId"] as? String
            ?: return SyncResult.Failure(
                operation.id,
                IllegalArgumentException("sharedAccountId is required")
            )

        val transactionRef = firestore
            .collection(COLLECTION_SHARED_ACCOUNTS)
            .document(sharedAccountId)
            .collection(COLLECTION_TRANSACTIONS)
            .document(operation.entityId)

        return when (operation.type) {
            SyncOperationType.CREATE, SyncOperationType.UPDATE -> {
                val firestoreData = data.toMutableMap()
                firestoreData[FIELD_UPDATED_AT] = FieldValue.serverTimestamp()

                transactionRef.set(firestoreData, SetOptions.merge()).await()
                SyncResult.Success(operation.id)
            }
            SyncOperationType.DELETE -> {
                // Soft delete: mark as deleted
                transactionRef.update(
                    mapOf(
                        FIELD_DELETED to true,
                        FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                    )
                ).await()
                SyncResult.Success(operation.id)
            }
        }
    }

    override suspend fun pullChanges(
        entityType: String,
        lastSyncTimestamp: Long?
    ): List<SyncOperation> {
        return try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                Timber.w("User not authenticated, skipping pull")
                return emptyList()
            }

            when (entityType) {
                ENTITY_TYPE_SHARED_ACCOUNT -> pullSharedAccounts(userId, lastSyncTimestamp)
                ENTITY_TYPE_SHARED_TRANSACTION -> pullSharedTransactions(userId, lastSyncTimestamp)
                else -> {
                    Timber.w("Unknown entity type for pull: $entityType")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to pull changes for $entityType")
            emptyList()
        }
    }

    private suspend fun pullSharedAccounts(
        userId: String,
        lastSyncTimestamp: Long?
    ): List<SyncOperation> {
        val query = if (lastSyncTimestamp != null) {
            firestore.collection(COLLECTION_SHARED_ACCOUNTS)
                .whereArrayContains(FIELD_OWNERS, userId)
                .whereGreaterThan(FIELD_UPDATED_AT, lastSyncTimestamp)
        } else {
            firestore.collection(COLLECTION_SHARED_ACCOUNTS)
                .whereArrayContains(FIELD_OWNERS, userId)
        }

        val snapshot = query.get().await()

        return snapshot.documents.mapNotNull { doc ->
            documentToSyncOperation(
                doc,
                ENTITY_TYPE_SHARED_ACCOUNT
            )
        }
    }

    private suspend fun pullSharedTransactions(
        userId: String,
        lastSyncTimestamp: Long?
    ): List<SyncOperation> {
        // First, get all shared accounts for this user
        val accountsSnapshot = firestore.collection(COLLECTION_SHARED_ACCOUNTS)
            .whereArrayContains(FIELD_OWNERS, userId)
            .get()
            .await()

        val operations = mutableListOf<SyncOperation>()

        // For each shared account, get its transactions
        for (accountDoc in accountsSnapshot.documents) {
            val transactionQuery = if (lastSyncTimestamp != null) {
                accountDoc.reference
                    .collection(COLLECTION_TRANSACTIONS)
                    .whereGreaterThan(FIELD_UPDATED_AT, lastSyncTimestamp)
            } else {
                accountDoc.reference.collection(COLLECTION_TRANSACTIONS)
            }

            val transactionSnapshot = transactionQuery.get().await()

            transactionSnapshot.documents.mapNotNullTo(operations) { doc ->
                documentToSyncOperation(
                    doc,
                    ENTITY_TYPE_SHARED_TRANSACTION
                )
            }
        }

        return operations
    }

    override suspend fun subscribeToChanges(
        entityType: String,
        onChanges: (List<SyncOperation>) -> Unit
    ): SyncSubscription {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Timber.w("User not authenticated, cannot subscribe to changes")
            return NoOpSyncSubscription
        }

        return when (entityType) {
            ENTITY_TYPE_SHARED_ACCOUNT -> subscribeToSharedAccounts(userId, onChanges)
            ENTITY_TYPE_SHARED_TRANSACTION -> subscribeToSharedTransactions(userId, onChanges)
            else -> {
                Timber.w("Unknown entity type for subscription: $entityType")
                NoOpSyncSubscription
            }
        }
    }

    private fun subscribeToSharedAccounts(
        userId: String,
        onChanges: (List<SyncOperation>) -> Unit
    ): SyncSubscription {
        val registration = firestore.collection(COLLECTION_SHARED_ACCOUNTS)
            .whereArrayContains(FIELD_OWNERS, userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error listening to shared accounts")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val operations = snapshot.documentChanges.mapNotNull { change ->
                        documentToSyncOperation(
                            change.document,
                            ENTITY_TYPE_SHARED_ACCOUNT
                        )
                    }
                    if (operations.isNotEmpty()) {
                        onChanges(operations)
                    }
                }
            }

        return FirestoreSyncSubscription(registration)
    }

    private fun subscribeToSharedTransactions(
        userId: String,
        onChanges: (List<SyncOperation>) -> Unit
    ): SyncSubscription {
        val registrations = mutableListOf<ListenerRegistration>()

        // First, subscribe to all shared accounts to know which ones to monitor
        val accountsRegistration = firestore.collection(COLLECTION_SHARED_ACCOUNTS)
            .whereArrayContains(FIELD_OWNERS, userId)
            .addSnapshotListener { accountSnapshot, accountError ->
                if (accountError != null) {
                    Timber.e(accountError, "Error listening to shared accounts for transactions")
                    return@addSnapshotListener
                }

                if (accountSnapshot != null) {
                    // For each account, subscribe to its transactions
                    for (accountDoc in accountSnapshot.documents) {
                        val transactionRegistration = accountDoc.reference
                            .collection(COLLECTION_TRANSACTIONS)
                            .addSnapshotListener { transactionSnapshot, transactionError ->
                                if (transactionError != null) {
                                    Timber.e(transactionError, "Error listening to transactions")
                                    return@addSnapshotListener
                                }

                                if (transactionSnapshot != null) {
                                    val operations = transactionSnapshot.documentChanges.mapNotNull { change ->
                                        documentToSyncOperation(
                                            change.document,
                                            ENTITY_TYPE_SHARED_TRANSACTION
                                        )
                                    }
                                    if (operations.isNotEmpty()) {
                                        onChanges(operations)
                                    }
                                }
                            }

                        registrations.add(transactionRegistration)
                    }
                }
            }

        registrations.add(accountsRegistration)

        return CompositeSyncSubscription(registrations)
    }

    private fun documentToSyncOperation(
        doc: DocumentSnapshot,
        entityType: String
    ): SyncOperation? {
        if (!doc.exists()) return null

        val data = doc.data ?: return null
        val isDeleted = data[FIELD_DELETED] as? Boolean ?: false

        val operationType = if (isDeleted) {
            SyncOperationType.DELETE
        } else {
            // For simplicity, treat all as UPDATE (CREATE vs UPDATE distinction handled by local logic)
            SyncOperationType.UPDATE
        }

        return SyncOperation(
            id = doc.id,
            type = operationType,
            entityType = entityType,
            entityId = doc.id,
            data = data,
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            // Simple check: try to get Firestore settings
            firestore.firestoreSettings
            auth.currentUser != null
        } catch (e: Exception) {
            Timber.e(e, "Firestore availability check failed")
            false
        }
    }
}

/**
 * Firestore-specific implementation of SyncSubscription
 */
private class FirestoreSyncSubscription(
    private val registration: ListenerRegistration
) : SyncSubscription {
    override fun cancel() {
        registration.remove()
    }
}

/**
 * Composite subscription that manages multiple Firestore listeners
 */
private class CompositeSyncSubscription(
    private val registrations: List<ListenerRegistration>
) : SyncSubscription {
    override fun cancel() {
        registrations.forEach { it.remove() }
    }
}

/**
 * No-op subscription for error cases
 */
private object NoOpSyncSubscription : SyncSubscription {
    override fun cancel() {
        // No-op
    }
}
