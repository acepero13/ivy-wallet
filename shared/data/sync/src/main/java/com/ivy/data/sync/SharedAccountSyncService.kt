package com.ivy.data.sync

import com.ivy.data.model.AccountId
import com.ivy.data.model.CategoryId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.PositiveValue
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionId
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.model.TransactionId
import com.ivy.data.model.TransactionMetadata
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.primitive.PositiveDouble
import com.ivy.data.repository.SharedAccountRepository
import com.ivy.data.repository.SharedTransactionRepository
import com.ivy.data.repository.TransactionRepository
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
    private val transactionRepository: TransactionRepository,
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

            // Get the shared account to find the linked account
            val sharedAccount = sharedAccountRepository.findById(accountId)
            val linkedAccountId = sharedAccount?.linkedAccountId

            if (linkedAccountId == null) {
                Timber.w("Shared account ${accountId.value} has no linked account, transactions will only be saved to SharedTransactionRepository")
            }

            transactionsData.forEach { data ->
                try {
                    val transaction = SharedTransaction(
                        id = SharedTransactionId(UUID.fromString(data["id"] as String)),
                        sharedAccountId = accountId,
                        type = SharedTransactionType.valueOf(data["type"] as String),
                        amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                        title = (data["title"] as? String)?.let { NotBlankTrimmedString.unsafe(it) },
                        description = (data["description"] as? String)?.let { NotBlankTrimmedString.unsafe(it) },
                        category = (data["category"] as? String)?.let { CategoryId(UUID.fromString(it)) },
                        time = Instant.ofEpochMilli(data["time"] as Long),
                        createdBy = data["createdBy"] as String,
                        createdAt = Instant.ofEpochMilli(data["createdAt"] as Long),
                        updatedAt = Instant.ofEpochMilli(data["updatedAt"] as Long),
                        updatedBy = data["updatedBy"] as String,
                        deleted = data["deleted"] as? Boolean ?: false
                    )

                    // Save to SharedTransactionRepository
                    sharedTransactionRepository.save(transaction)
                    Timber.d("Synced shared transaction: ${transaction.id.value}")

                    // Also save to regular TransactionRepository if there's a linked account
                    if (linkedAccountId != null && sharedAccount != null) {
                        val regularTransaction = convertToRegularTransaction(
                            sharedTransaction = transaction,
                            linkedAccountId = linkedAccountId,
                            currency = sharedAccount.currency
                        )

                        if (regularTransaction != null) {
                            transactionRepository.save(regularTransaction)
                            Timber.d("Synced to TransactionRepository: ${transaction.id.value}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing transaction: ${data["id"]}")
                }
            }

            Timber.d("Successfully synced all transactions for account ${accountId.value}")
        } catch (e: Exception) {
            Timber.e(e, "Error syncing transactions for account ${accountId.value}")
        }
    }

    private fun convertToRegularTransaction(
        sharedTransaction: SharedTransaction,
        linkedAccountId: AccountId,
        currency: AssetCode
    ): com.ivy.data.model.Transaction? {
        return try {
            val amount = PositiveDouble.unsafe(sharedTransaction.amount)
            val value = PositiveValue(amount = amount, asset = currency)
            val metadata = TransactionMetadata(
                recurringRuleId = null,
                paidForDateTime = null,
                loanId = null,
                loanRecordId = null
            )

            when (sharedTransaction.type) {
                SharedTransactionType.EXPENSE -> Expense(
                    id = TransactionId(sharedTransaction.id.value),
                    title = sharedTransaction.title,
                    description = sharedTransaction.description,
                    category = sharedTransaction.category,
                    time = sharedTransaction.time,
                    settled = true,
                    metadata = metadata,
                    tags = emptyList(),
                    value = value,
                    account = linkedAccountId
                )
                SharedTransactionType.INCOME -> Income(
                    id = TransactionId(sharedTransaction.id.value),
                    title = sharedTransaction.title,
                    description = sharedTransaction.description,
                    category = sharedTransaction.category,
                    time = sharedTransaction.time,
                    settled = true,
                    metadata = metadata,
                    tags = emptyList(),
                    value = value,
                    account = linkedAccountId
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error converting shared transaction to regular transaction")
            null
        }
    }
}
