package com.ivy.data.sync

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionId
import com.ivy.data.repository.SharedAccountRepository
import com.ivy.data.repository.SharedTransactionRepository
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that bridges between repositories and sync operations.
 *
 * Responsibilities:
 * - Query unsynced items from repositories
 * - Convert domain models to sync operations
 * - Mark items as synced after successful push
 * - Handle remote ID assignment
 */
@Singleton
class SyncAdapter @Inject constructor(
    private val sharedAccountRepository: SharedAccountRepository,
    private val sharedTransactionRepository: SharedTransactionRepository,
    private val dispatchersProvider: DispatchersProvider,
) {

    /**
     * Get all unsynced shared accounts and create sync operations
     */
    suspend fun getUnsyncedAccountOperations(): List<SyncOperation> = withContext(dispatchersProvider.io) {
        try {
            val unsyncedAccounts = sharedAccountRepository.findBySyncStatus(synced = false)

            unsyncedAccounts.map { account ->
                createAccountSyncOperation(account)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unsynced accounts")
            emptyList()
        }
    }

    /**
     * Get all unsynced shared transactions and create sync operations
     */
    suspend fun getUnsyncedTransactionOperations(): List<SyncOperation> = withContext(dispatchersProvider.io) {
        try {
            val unsyncedTransactions = sharedTransactionRepository.findBySyncStatus(synced = false)

            unsyncedTransactions.map { transaction ->
                createTransactionSyncOperation(transaction)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unsynced transactions")
            emptyList()
        }
    }

    /**
     * Mark a shared account as synced
     */
    suspend fun markAccountAsSynced(accountId: SharedAccountId, remoteId: String? = null) {
        try {
            if (remoteId != null) {
                sharedAccountRepository.updateRemoteId(accountId, remoteId, synced = true)
            } else {
                sharedAccountRepository.updateSyncStatus(accountId, synced = true)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark account $accountId as synced")
        }
    }

    /**
     * Mark a shared transaction as synced
     */
    suspend fun markTransactionAsSynced(transactionId: SharedTransactionId, remoteId: String? = null) {
        try {
            if (remoteId != null) {
                sharedTransactionRepository.updateRemoteId(transactionId, remoteId, synced = true)
            } else {
                sharedTransactionRepository.updateSyncStatus(transactionId, synced = true)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark transaction $transactionId as synced")
        }
    }

    /**
     * Create a sync operation from a SharedAccount domain model
     */
    private fun createAccountSyncOperation(account: SharedAccount): SyncOperation {
        val data = mapOf(
            "id" to account.id.value.toString(),
            "name" to account.name.value,
            "currency" to account.currency.code,
            "owners" to account.owners,
            "createdBy" to account.createdBy,
            "createdAt" to account.createdAt.toEpochMilli(),
            "updatedAt" to account.updatedAt.toEpochMilli(),
            "deleted" to false
        )

        return SyncOperation(
            type = SyncOperationType.UPDATE, // Use UPDATE as it handles both create and update in Firestore
            entityType = "sharedAccount",
            entityId = account.id.value.toString(),
            data = data
        )
    }

    /**
     * Create a sync operation from a SharedTransaction domain model
     */
    private fun createTransactionSyncOperation(transaction: SharedTransaction): SyncOperation {
        val data = mapOf(
            "id" to transaction.id.value.toString(),
            "sharedAccountId" to transaction.sharedAccountId.value.toString(),
            "type" to transaction.type.name,
            "amount" to transaction.amount,
            "title" to transaction.title?.value,
            "description" to transaction.description?.value,
            "category" to transaction.category?.value,
            "time" to transaction.time.toEpochMilli(),
            "createdBy" to transaction.createdBy,
            "createdAt" to transaction.createdAt.toEpochMilli(),
            "updatedAt" to transaction.updatedAt.toEpochMilli(),
            "updatedBy" to transaction.updatedBy,
            "deleted" to transaction.deleted
        )

        return SyncOperation(
            type = if (transaction.deleted) SyncOperationType.DELETE else SyncOperationType.UPDATE,
            entityType = "sharedTransaction",
            entityId = transaction.id.value.toString(),
            data = data
        )
    }
}
