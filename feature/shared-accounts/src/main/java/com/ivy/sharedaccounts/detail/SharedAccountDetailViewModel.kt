package com.ivy.sharedaccounts.detail

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.data.DataObserver
import com.ivy.data.DataWriteEvent
import com.ivy.data.auth.AuthRepository
import com.ivy.data.auth.AuthUser
import com.ivy.data.model.Account
import com.ivy.data.model.AccountId
import com.ivy.data.model.CategoryId
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionId
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.SharedAccountRepository
import com.ivy.data.repository.SharedTransactionRepository
import com.ivy.data.sync.FirestoreInvitationRepository
import com.ivy.domain.usecase.invitation.CreateInvitationUseCase
import com.ivy.domain.usecase.invitation.GenerateInviteLinkUseCase
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class SharedAccountDetailViewModel @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val sharedAccountRepository: SharedAccountRepository,
    private val sharedTransactionRepository: SharedTransactionRepository,
    private val accountRepository: AccountRepository,
    private val dataObserver: DataObserver,
    private val authRepository: AuthRepository,
    private val createInvitationUseCase: CreateInvitationUseCase,
    private val generateInviteLinkUseCase: GenerateInviteLinkUseCase,
    private val firestoreInvitationRepository: FirestoreInvitationRepository,
    private val sharedPrefs: com.ivy.base.legacy.SharedPrefs,
) : ComposeViewModel<SharedAccountDetailState, SharedAccountDetailEvent>() {

    private var sharedAccountId by mutableStateOf<SharedAccountId?>(null)
    private var sharedAccount by mutableStateOf<SharedAccount?>(null)
    private var transactions by mutableStateOf<ImmutableList<SharedTransaction>>(emptyList<SharedTransaction>().toImmutableList())
    private var totalIncome by mutableStateOf(0.0)
    private var totalExpense by mutableStateOf(0.0)
    private var balance by mutableStateOf(0.0)
    private var isLoading by mutableStateOf(true)
    private var showLinkAccountModal by mutableStateOf(false)
    private var availableAccounts by mutableStateOf<ImmutableList<Account>>(emptyList<Account>().toImmutableList())
    private var pendingLinkAction by mutableStateOf(false)
    private var pendingLinkAccountId by mutableStateOf<AccountId?>(null)
    private var accountDeleted by mutableStateOf(false)

    init {
        android.util.Log.d("SharedAccountDetail", "ViewModel initialized: ${this.hashCode()}")
        viewModelScope.launch {
            dataObserver.writeEvents.collectLatest { event ->
                when (event) {
                    is DataWriteEvent.SharedAccountChange,
                    is DataWriteEvent.SharedTransactionChange -> {
                        loadData()
                    }
                    else -> {
                        // do nothing
                    }
                }
            }
        }
    }

    @Composable
    override fun uiState(): SharedAccountDetailState {
        android.util.Log.d("SharedAccountDetail", "uiState() called on VM ${this.hashCode()}: isLoading=$isLoading, account=${sharedAccount?.name?.value}")

        return SharedAccountDetailState(
            sharedAccount = sharedAccount,
            transactions = transactions,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            balance = balance,
            isLoading = isLoading,
            showLinkAccountModal = showLinkAccountModal,
            availableAccounts = availableAccounts,
            pendingLinkAction = pendingLinkAction,
            pendingLinkAccountId = pendingLinkAccountId,
            accountDeleted = accountDeleted
        )
    }

    override fun onEvent(event: SharedAccountDetailEvent) {
        when (event) {
            is SharedAccountDetailEvent.OnAddTransaction -> {
                // TODO: Navigate to add transaction screen
            }
            is SharedAccountDetailEvent.OnTransactionClick -> {
                // TODO: Navigate to edit transaction screen
            }
            is SharedAccountDetailEvent.OnBack -> {
                // TODO: Navigate back
            }
            is SharedAccountDetailEvent.OnEditAccount -> {
                onEditAccount()
            }
            is SharedAccountDetailEvent.OnShareInvite -> {
                android.util.Log.d("SharedAccountDetail", "OnShareInvite event received")
                android.widget.Toast.makeText(context, "Share button clicked!", android.widget.Toast.LENGTH_SHORT).show()
                shareInvite()
            }
            is SharedAccountDetailEvent.OnDismissLinkAccountModal -> {
                showLinkAccountModal = false
            }
            is SharedAccountDetailEvent.OnLinkAccount -> {
                onLinkAccount(event.accountId)
            }
            is SharedAccountDetailEvent.OnDeleteAccount -> {
                deleteSharedAccount()
            }
        }
    }

    private fun onEditAccount() {
        android.util.Log.d("SharedAccountDetail", "onEditAccount() called - SIMPLE VERSION")
        // Just show the modal - accounts will be loaded by LaunchedEffect in the modal
        showLinkAccountModal = true
        android.util.Log.d("SharedAccountDetail", "Modal visibility set to: $showLinkAccountModal")
    }

    suspend fun loadAvailableAccounts() {
        android.util.Log.d("SharedAccountDetail", "loadAvailableAccounts() called")
        try {
            val accounts = accountRepository.findAll()
            android.util.Log.d("SharedAccountDetail", "Found ${accounts.size} accounts")
            availableAccounts = accounts.toImmutableList()
            android.util.Log.d("SharedAccountDetail", "availableAccounts updated with ${availableAccounts.size} items")
        } catch (e: Exception) {
            android.util.Log.e("SharedAccountDetail", "Error loading available accounts", e)
            e.printStackTrace()
        }
    }

    private fun onLinkAccount(accountId: AccountId?) {
        android.util.Log.d("SharedAccountDetail", "onLinkAccount called with accountId: ${accountId?.value}")
        // Store the account ID to be processed
        pendingLinkAccountId = accountId
        pendingLinkAction = true
    }

    suspend fun performLinkAccount(accountId: AccountId?) {
        android.util.Log.d("SharedAccountDetail", "performLinkAccount - starting link process with accountId: ${accountId?.value}")
        withContext(Dispatchers.IO) {
            try {
                val account = sharedAccount
                android.util.Log.d("SharedAccountDetail", "Current shared account: ${account?.name?.value}, current linkedAccountId: ${account?.linkedAccountId?.value}")
                if (account != null) {
                    val updatedAccount = account.copy(
                        linkedAccountId = accountId,
                        updatedAt = Instant.now()
                    )
                    android.util.Log.d("SharedAccountDetail", "Updated account linkedAccountId to: ${updatedAccount.linkedAccountId?.value}")
                    android.util.Log.d("SharedAccountDetail", "Saving updated account to Room...")
                    sharedAccountRepository.save(updatedAccount)

                    android.util.Log.d("SharedAccountDetail", "Saving to Firestore...")
                    // Also update in Firestore
                    firestoreInvitationRepository.saveSharedAccount(
                        id = updatedAccount.id,
                        name = updatedAccount.name.value,
                        currency = updatedAccount.currency.code,
                        owners = updatedAccount.owners,
                        createdBy = updatedAccount.createdBy,
                        createdAt = updatedAccount.createdAt.toEpochMilli(),
                        updatedAt = updatedAccount.updatedAt.toEpochMilli(),
                        linkedAccountId = accountId?.value?.toString()
                    )

                    // Update preferences for "use by default" behavior
                    if (accountId != null) {
                        // When linking: enable "use shared account by default"
                        android.util.Log.d("SharedAccountDetail", "Enabling 'use shared account by default'")
                        sharedPrefs.putBoolean(com.ivy.base.legacy.SharedPrefs.USE_SHARED_ACCOUNT_BY_DEFAULT, true)
                        sharedPrefs.putString(com.ivy.base.legacy.SharedPrefs.DEFAULT_SHARED_ACCOUNT_ID, account.id.value.toString())
                    } else {
                        // When unlinking: disable "use shared account by default"
                        android.util.Log.d("SharedAccountDetail", "Disabling 'use shared account by default'")
                        sharedPrefs.putBoolean(com.ivy.base.legacy.SharedPrefs.USE_SHARED_ACCOUNT_BY_DEFAULT, false)
                        sharedPrefs.remove(com.ivy.base.legacy.SharedPrefs.DEFAULT_SHARED_ACCOUNT_ID)
                    }

                    android.util.Log.d("SharedAccountDetail", "Linked account updated successfully")

                    // Update local state immediately before closing modal
                    sharedAccount = updatedAccount

                    withContext(Dispatchers.Main) {
                        showLinkAccountModal = false
                        pendingLinkAction = false
                        pendingLinkAccountId = null
                    }
                    loadData()
                }
            } catch (e: Exception) {
                android.util.Log.e("SharedAccountDetail", "Failed to link account", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    pendingLinkAction = false
                    pendingLinkAccountId = null
                }
            }
        }
    }

    private fun shareInvite() {
        android.util.Log.d("SharedAccountDetail", "shareInvite() called")
        val account = sharedAccount
        if (account == null) {
            android.util.Log.e("SharedAccountDetail", "Cannot share: account is null")
            return
        }
        android.util.Log.d("SharedAccountDetail", "Account found: ${account.name.value}")

        android.util.Log.d("SharedAccountDetail", "About to launch coroutine")

        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            android.util.Log.d("SharedAccountDetail", "Inside coroutine")
            try {
                android.util.Log.d("SharedAccountDetail", "Getting current user")
                // Get current user
                val currentUser = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    authRepository.getCurrentUserOnce()
                }
                val userUid = when (currentUser) {
                    is com.ivy.data.auth.AuthResult.Success -> currentUser.user.uid
                    else -> {
                        android.util.Log.e("SharedAccountDetail", "User not authenticated")
                        // Fallback to local user
                        "local-user"
                    }
                }
                android.util.Log.d("SharedAccountDetail", "User UID: $userUid")

                // Create invitation with a placeholder email (can be updated when sharing)
                android.util.Log.d("SharedAccountDetail", "Creating invitation...")
                val invitationResult = createInvitationUseCase(
                    sharedAccountId = account.id,
                    inviterUid = userUid,
                    inviteeEmail = "invite@placeholder.com", // Placeholder, user can share with anyone
                    expirationDays = 7
                )
                android.util.Log.d("SharedAccountDetail", "Invitation result received")

                invitationResult.fold(
                    ifLeft = { error ->
                        android.util.Log.e("SharedAccountDetail", "Failed to create invitation: $error")
                        // Fallback to simple share
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            shareSimpleInvite(account)
                        }
                    },
                    ifRight = { invitation ->
                        android.util.Log.d("SharedAccountDetail", "Invitation created successfully: ${invitation.token}")
                        // Generate deep link
                        val linkResult = generateInviteLinkUseCase(invitation)
                        linkResult.fold(
                            ifLeft = { error ->
                                android.util.Log.e("SharedAccountDetail", "Failed to generate link: $error")
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    shareSimpleInvite(account)
                                }
                            },
                            ifRight = { deepLink ->
                                // Create invitation text with proper token
                                val inviteText = """
                                    Join my shared account on Ivy Wallet!

                                    Account: ${account.name.value}

                                    Click this link to accept the invitation:
                                    $deepLink

                                    Or use this invitation code in the app:
                                    ${invitation.token}

                                    Download Ivy Wallet: https://github.com/Ivy-Apps/ivy-wallet
                                """.trimIndent()

                                // Create Android share intent
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Join ${account.name.value} on Ivy Wallet")
                                        putExtra(android.content.Intent.EXTRA_TEXT, inviteText)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                    context.startActivity(
                                        android.content.Intent.createChooser(shareIntent, "Share invitation via").apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            }
                        )
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("SharedAccountDetail", "Error sharing invite", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    shareSimpleInvite(account)
                }
            }
        }
    }

    private fun shareSimpleInvite(account: SharedAccount) {
        android.util.Log.d("SharedAccountDetail", "shareSimpleInvite() called")
        val accountId = account.id.value.toString()

        // Fallback simple invitation text
        val inviteText = """
            Join my shared account on Ivy Wallet!

            Account: ${account.name.value}

            To join, install Ivy Wallet and use this invitation code:
            $accountId

            Download Ivy Wallet: https://github.com/Ivy-Apps/ivy-wallet
        """.trimIndent()

        android.util.Log.d("SharedAccountDetail", "Starting share intent")
        // Create Android share intent
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Join ${account.name.value} on Ivy Wallet")
            putExtra(android.content.Intent.EXTRA_TEXT, inviteText)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(
            android.content.Intent.createChooser(shareIntent, "Share invitation via").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun setSharedAccountId(id: SharedAccountId) {
        android.util.Log.d("SharedAccountDetail", "setSharedAccountId called with: ${id.value}")
        sharedAccountId = id
        android.util.Log.d("SharedAccountDetail", "Launching loadData coroutine from setSharedAccountId")
        isLoading = true

        // Launch the data loading coroutine
        viewModelScope.launch(Dispatchers.IO) {
            loadData()
        }
    }

    suspend fun loadDataForAccount(id: SharedAccountId) {
        android.util.Log.d("SharedAccountDetail", "loadDataForAccount called with: ${id.value}")
        sharedAccountId = id
        isLoading = true
        android.util.Log.d("SharedAccountDetail", "Calling loadData with IO dispatcher")

        withContext(Dispatchers.IO) {
            // First, sync transactions from Firestore to local database
            syncTransactionsFromFirestore(id)
            // Then load from local database
            loadData()
        }
    }

    private suspend fun syncTransactionsFromFirestore(accountId: SharedAccountId) {
        android.util.Log.d("SharedAccountDetail", "Syncing transactions from Firestore for account: ${accountId.value}")

        try {
            val result = firestoreInvitationRepository.fetchSharedTransactions(accountId)

            result.fold(
                ifLeft = { error ->
                    android.util.Log.e("SharedAccountDetail", "Failed to fetch transactions: $error")
                },
                ifRight = { transactionsData ->
                    android.util.Log.d("SharedAccountDetail", "Fetched ${transactionsData.size} transactions from Firestore")

                    // Parse and save each transaction to local database
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

                            // Save to local Room database
                            sharedTransactionRepository.save(transaction)
                            android.util.Log.d("SharedAccountDetail", "Synced transaction: ${transaction.id.value}")
                        } catch (e: Exception) {
                            android.util.Log.e("SharedAccountDetail", "Error parsing transaction: ${data["id"]}", e)
                        }
                    }

                    android.util.Log.d("SharedAccountDetail", "Successfully synced all transactions from Firestore")
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("SharedAccountDetail", "Error syncing transactions from Firestore", e)
        }
    }

    private suspend fun loadData() {
        android.util.Log.d("SharedAccountDetail", "loadData started")

        val accountId = sharedAccountId
        if (accountId == null) {
            android.util.Log.w("SharedAccountDetail", "accountId is null")
            isLoading = false
            return
        }

        android.util.Log.d("SharedAccountDetail", "Loading account: ${accountId.value}")

        try {
            android.util.Log.d("SharedAccountDetail", "Calling repository.findById")
            val account = sharedAccountRepository.findById(accountId)
            android.util.Log.d("SharedAccountDetail", "Repository returned")
            android.util.Log.d("SharedAccountDetail", "Account: ${account?.name?.value ?: "NULL"}")

            val allTransactions = sharedTransactionRepository.findBySharedAccountId(accountId)
                .filter { !it.deleted }
                .sortedByDescending { it.time }

            android.util.Log.d("SharedAccountDetail", "Found ${allTransactions.size} transactions")

            val transactionsList = allTransactions.toImmutableList()

            val income = allTransactions
                .filter { it.type == SharedTransactionType.INCOME }
                .sumOf { it.amount }

            val expense = allTransactions
                .filter { it.type == SharedTransactionType.EXPENSE }
                .sumOf { it.amount }

            val calculatedBalance = income - expense

            android.util.Log.d("SharedAccountDetail", "Calculated balance: $calculatedBalance (income: $income, expense: $expense)")

            // Update all state fields
            android.util.Log.d("SharedAccountDetail", "About to update state fields on VM ${this.hashCode()}")
            sharedAccount = account
            transactions = transactionsList
            totalIncome = income
            totalExpense = expense
            balance = calculatedBalance
            isLoading = false
            android.util.Log.d("SharedAccountDetail", "State updated on VM ${this.hashCode()}. Current state: ${sharedAccount?.name?.value}, isLoading=$isLoading")
        } catch (e: Exception) {
            // Log the error for debugging
            android.util.Log.e("SharedAccountDetail", "Error loading data: ${e.message}", e)
            e.printStackTrace()
            sharedAccount = null
            transactions = emptyList<SharedTransaction>().toImmutableList()
            isLoading = false
        }
    }

    private fun deleteSharedAccount() {
        android.util.Log.d("SharedAccountDetail", "deleteSharedAccount() called")
        val account = sharedAccount
        if (account == null) {
            android.util.Log.e("SharedAccountDetail", "Cannot delete: account is null")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("SharedAccountDetail", "Starting deletion of account ${account.id.value}")

                // 1. Delete from Firestore
                android.util.Log.d("SharedAccountDetail", "Deleting from Firestore...")
                val firestoreResult = firestoreInvitationRepository.deleteSharedAccount(account.id)
                firestoreResult.fold(
                    ifLeft = { error ->
                        android.util.Log.e("SharedAccountDetail", "Failed to delete from Firestore: $error")
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                context,
                                "Failed to delete from cloud: $error",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    ifRight = {
                        android.util.Log.d("SharedAccountDetail", "Successfully deleted from Firestore")
                    }
                )

                // 2. Delete all transactions from local database
                android.util.Log.d("SharedAccountDetail", "Deleting transactions from local database...")
                val localTransactions = sharedTransactionRepository.findBySharedAccountId(account.id)
                localTransactions.forEach { transaction ->
                    sharedTransactionRepository.deleteById(transaction.id)
                }
                android.util.Log.d("SharedAccountDetail", "Deleted ${localTransactions.size} local transactions")

                // 3. Delete the account from local database
                android.util.Log.d("SharedAccountDetail", "Deleting account from local database...")
                sharedAccountRepository.deleteById(account.id)
                android.util.Log.d("SharedAccountDetail", "Successfully deleted account from local database")

                // 4. Clear preferences if this was the default account
                val defaultSharedAccountId = sharedPrefs.getString(
                    com.ivy.base.legacy.SharedPrefs.DEFAULT_SHARED_ACCOUNT_ID,
                    null
                )
                if (defaultSharedAccountId == account.id.value.toString()) {
                    android.util.Log.d("SharedAccountDetail", "Clearing default shared account preferences")
                    sharedPrefs.putBoolean(com.ivy.base.legacy.SharedPrefs.USE_SHARED_ACCOUNT_BY_DEFAULT, false)
                    sharedPrefs.remove(com.ivy.base.legacy.SharedPrefs.DEFAULT_SHARED_ACCOUNT_ID)
                }

                // 5. Set accountDeleted flag to trigger navigation
                withContext(Dispatchers.Main) {
                    android.util.Log.d("SharedAccountDetail", "Setting accountDeleted flag")
                    accountDeleted = true
                    showLinkAccountModal = false
                    android.widget.Toast.makeText(
                        context,
                        "Shared account deleted successfully",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("SharedAccountDetail", "Error deleting shared account", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to delete account: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
