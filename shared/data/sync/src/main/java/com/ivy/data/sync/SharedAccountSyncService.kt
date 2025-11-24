package com.ivy.data.sync

import com.ivy.data.model.CategoryId
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionId
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.SharedAccountRepository
import com.ivy.data.repository.SharedTransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that manages real-time synchronization of shared account transactions
 */
@Singleton
class SharedAccountSyncService @Inject constructor(
    private val firestoreInvitationRepository: FirestoreInvitationRepository,
    private val sharedAccountRepository: SharedAccountRepository,
    private val sharedTransactionRepository: SharedTransactionRepository,
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isStarted = false

    /**
     * Start listening to all shared accounts for real-time updates
     */
    fun startSync() {
        Timber.d("SharedAccountSyncService.startSync() called")

        if (isStarted) {
            Timber.d("Sync service already started")
            return
        }

        serviceScope.launch {
            try {
                Timber.d("Loading shared accounts from repository...")
                // Load all shared accounts
                val sharedAccounts = sharedAccountRepository.findAll()

                if (sharedAccounts.isEmpty()) {
                    Timber.w("No shared accounts to sync - sync service will not start")
                    return@launch
                }

                val accountIds = sharedAccounts.map { it.id }
                Timber.d("Starting real-time sync for ${accountIds.size} shared accounts: ${accountIds.map { it.value }}")

                // Set up listeners for all shared accounts
                firestoreInvitationRepository.listenToAllSharedAccountsTransactions(
                    sharedAccountIds = accountIds,
                    onTransactionsChanged = { accountId, transactionsData ->
                        Timber.d("Real-time update: ${transactionsData.size} transactions for account ${accountId.value}")

                        // Sync transactions to local database in background
                        serviceScope.launch {
                            syncTransactionsToLocal(accountId, transactionsData)
                        }
                    },
                    onError = { error ->
                        Timber.e("Listener error: $error")
                    }
                )

                isStarted = true
                Timber.d("Real-time sync service started successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start sync service")
            }
        }
    }

    private suspend fun syncTransactionsToLocal(
        accountId: SharedAccountId,
        transactionsData: List<Map<String, Any>>
    ) {
        try {
            Timber.d("Syncing ${transactionsData.size} transactions to local DB for account ${accountId.value}")

            // Get all existing local transaction IDs to avoid unnecessary processing
            val existingTransactions = sharedTransactionRepository.findBySharedAccountId(accountId)
            val existingIds = existingTransactions.associate {
                it.id.value to it.updatedAt
            }

            var newCount = 0
            var updatedCount = 0
            var skippedCount = 0

            transactionsData.forEach { data ->
                try {
                    val transactionId = UUID.fromString(data["id"] as String)
                    val remoteUpdatedAt = Instant.ofEpochMilli(data["updatedAt"] as Long)

                    val localUpdatedAt = existingIds[transactionId]

                    // Skip if local version is already up-to-date or newer
                    if (localUpdatedAt != null && !localUpdatedAt.isBefore(remoteUpdatedAt)) {
                        skippedCount++
                        return@forEach
                    }

                    val transaction = SharedTransaction(
                        id = SharedTransactionId(transactionId),
                        sharedAccountId = accountId,
                        type = SharedTransactionType.valueOf(data["type"] as String),
                        amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                        title = (data["title"] as? String)?.let { NotBlankTrimmedString.unsafe(it) },
                        description = (data["description"] as? String)?.let { NotBlankTrimmedString.unsafe(it) },
                        category = (data["category"] as? String)?.let { CategoryId(UUID.fromString(it)) },
                        time = Instant.ofEpochMilli(data["time"] as Long),
                        createdBy = data["createdBy"] as String,
                        createdAt = Instant.ofEpochMilli(data["createdAt"] as Long),
                        updatedAt = remoteUpdatedAt,
                        updatedBy = data["updatedBy"] as String,
                        deleted = data["deleted"] as? Boolean ?: false
                    )

                    // Save to SharedTransactionRepository
                    // Note: This automatically saves to TransactionRepository via saveToRegularTransactions()
                    sharedTransactionRepository.save(transaction)

                    if (localUpdatedAt == null) {
                        newCount++
                        Timber.d("Synced NEW shared transaction: ${transaction.id.value}")
                    } else {
                        updatedCount++
                        Timber.d("Synced UPDATED shared transaction: ${transaction.id.value}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing transaction: ${data["id"]}")
                }
            }

            Timber.d("Successfully synced transactions for account ${accountId.value}: $newCount new, $updatedCount updated, $skippedCount skipped")
        } catch (e: Exception) {
            Timber.e(e, "Error syncing transactions for account ${accountId.value}")
        }
    }
}
