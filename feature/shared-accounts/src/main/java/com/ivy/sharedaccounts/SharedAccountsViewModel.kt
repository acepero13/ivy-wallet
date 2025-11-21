package com.ivy.sharedaccounts

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
import com.ivy.data.model.Account
import com.ivy.data.model.AccountId
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.CategoryId
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransactionId
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.SharedAccountRepository
import com.ivy.data.repository.SharedTransactionRepository
import com.ivy.data.sync.FirestoreInvitationRepository
import com.ivy.domain.usecase.invitation.AcceptInvitationUseCase
import com.ivy.navigation.Navigation
import com.ivy.navigation.SharedAccountDetailScreen
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@Stable
@SuppressLint("StaticFieldLeak")
@HiltViewModel
class SharedAccountsViewModel @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val sharedAccountRepository: SharedAccountRepository,
    private val accountRepository: AccountRepository,
    private val sharedTransactionRepository: SharedTransactionRepository,
    private val baseCurrencyAct: BaseCurrencyAct,
    private val dataObserver: DataObserver,
    private val navigation: Navigation,
    private val authRepository: AuthRepository,
    private val acceptInvitationUseCase: AcceptInvitationUseCase,
    private val firestoreInvitationRepository: FirestoreInvitationRepository,
) : ComposeViewModel<SharedAccountsState, SharedAccountsEvent>() {

    private var sharedAccounts by mutableStateOf<List<SharedAccount>>(emptyList())
    private var accounts by mutableStateOf<List<Account>>(emptyList())
    private var baseCurrency by mutableStateOf("")
    private var isLoading by mutableStateOf(true)
    private var currentUserUid by mutableStateOf<String?>(null)
    private var showCreateModal by mutableStateOf(false)
    private var showAcceptInviteModal by mutableStateOf(false)

    init {
        viewModelScope.launch {
            dataObserver.writeEvents.collectLatest { event ->
                when (event) {
                    is DataWriteEvent.SharedAccountChange -> {
                        loadSharedAccounts()
                    }

                    else -> {
                        // do nothing
                    }
                }
            }
        }
    }

    @Composable
    override fun uiState(): SharedAccountsState {
        LaunchedEffect(Unit) {
            onStart()
        }

        return SharedAccountsState(
            sharedAccounts = sharedAccounts.toImmutableList(),
            accounts = accounts.toImmutableList(),
            baseCurrency = baseCurrency,
            isLoading = isLoading,
            currentUserUid = currentUserUid,
            showCreateModal = showCreateModal,
            showAcceptInviteModal = showAcceptInviteModal
        )
    }

    override fun onEvent(event: SharedAccountsEvent) {
        when (event) {
            is SharedAccountsEvent.OnSharedAccountClick -> onSharedAccountClick(event.accountId)
            SharedAccountsEvent.OnCreateSharedAccount -> onCreateSharedAccount()
            SharedAccountsEvent.OnDismissCreateModal -> onDismissCreateModal()
            is SharedAccountsEvent.OnCreateAccount -> onCreateAccount(
                event.name,
                event.currency,
                event.linkedAccountId
            )

            SharedAccountsEvent.OnAcceptInvite -> onAcceptInvite()
            SharedAccountsEvent.OnDismissAcceptInviteModal -> onDismissAcceptInviteModal()
            is SharedAccountsEvent.OnAcceptInviteCode -> onAcceptInviteCode(
                event.invitationCode,
                event.linkedAccountId
            )
        }
    }

    private fun onStart() {
        viewModelScope.launch {
            loadSharedAccounts()
            loadAccounts()
            loadBaseCurrency()
            // TODO: Load current user UID from AuthRepository

            // Set up real-time listeners for all shared accounts
            setupRealtimeListeners()
        }
    }

    private suspend fun loadSharedAccounts() {
        isLoading = true
        sharedAccounts = try {
            // For now, load all shared accounts
            // TODO: Filter by current user when auth is integrated
            sharedAccountRepository.findAll()
        } catch (e: Exception) {
            emptyList()
        } finally {
            isLoading = false
        }
    }

    private fun setupRealtimeListeners() {
        // Get all shared account IDs
        val accountIds = sharedAccounts.map { it.id }

        if (accountIds.isEmpty()) {
            android.util.Log.d("SharedAccounts", "No shared accounts to listen to")
            return
        }

        android.util.Log.d("SharedAccounts", "Setting up real-time listeners for ${accountIds.size} shared accounts")

        // Set up listeners for all shared accounts
        // Note: Listeners are managed by FirestoreInvitationRepository
        firestoreInvitationRepository.listenToAllSharedAccountsTransactions(
            sharedAccountIds = accountIds,
            onTransactionsChanged = { accountId, transactionsData ->
                android.util.Log.d("SharedAccounts", "Real-time update: ${transactionsData.size} transactions for account ${accountId.value}")

                // Sync transactions to local database in background
                viewModelScope.launch(Dispatchers.IO) {
                    syncTransactionsToLocal(accountId, transactionsData)
                }
            },
            onError = { error ->
                android.util.Log.e("SharedAccounts", "Listener error: $error")
            }
        )

        android.util.Log.d("SharedAccounts", "Real-time listeners set up successfully")
    }

    private suspend fun syncTransactionsToLocal(
        accountId: SharedAccountId,
        transactionsData: List<Map<String, Any>>
    ) {
        try {
            android.util.Log.d("SharedAccounts", "Syncing ${transactionsData.size} transactions to local DB for account ${accountId.value}")

            transactionsData.forEach { data ->
                try {
                    val transaction = com.ivy.data.model.SharedTransaction(
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
                    android.util.Log.d("SharedAccounts", "Synced transaction: ${transaction.id.value}")
                } catch (e: Exception) {
                    android.util.Log.e("SharedAccounts", "Error parsing transaction: ${data["id"]}", e)
                }
            }

            android.util.Log.d("SharedAccounts", "Successfully synced all transactions for account ${accountId.value}")
        } catch (e: Exception) {
            android.util.Log.e("SharedAccounts", "Error syncing transactions for account ${accountId.value}", e)
        }
    }


    private suspend fun loadBaseCurrency() {
        baseCurrency = baseCurrencyAct(Unit)
    }

    private fun onSharedAccountClick(accountId: SharedAccountId) {
        viewModelScope.launch {
            navigation.navigateTo(SharedAccountDetailScreen(sharedAccountId = accountId.value))
        }
    }

    private fun onCreateSharedAccount() {
        showCreateModal = true
    }

    private fun onDismissCreateModal() {
        showCreateModal = false
    }

    private fun onAcceptInvite() {
        showAcceptInviteModal = true
    }

    private fun onDismissAcceptInviteModal() {
        showAcceptInviteModal = false
    }

    private fun onAcceptInviteCode(invitationCode: String, linkedAccountId: AccountId?) {
        viewModelScope.launch {
            try {
                // Get current user
                val currentUser = authRepository.getCurrentUserOnce()
                val userUid = when (currentUser) {
                    is com.ivy.data.auth.AuthResult.Success -> currentUser.user.uid
                    else -> {
                        android.util.Log.e("SharedAccounts", "User not authenticated")
                        // Fallback to local user
                        "local-user"
                    }
                }

                // Accept the invitation
                val result = acceptInvitationUseCase(
                    token = invitationCode,
                    acceptedByUid = userUid
                )

                result.fold(
                    ifLeft = { error ->
                        android.util.Log.e("SharedAccounts", "Failed to accept invitation: $error")
                        // TODO: Show error toast/snackbar
                    },
                    ifRight = { invitation ->
                        android.util.Log.d("SharedAccounts", "Invitation accepted successfully")

                        // If a linked account was selected, update the shared account
                        if (linkedAccountId != null) {
                            try {
                                val sharedAccount =
                                    sharedAccountRepository.findById(invitation.sharedAccountId)
                                if (sharedAccount != null) {
                                    val updated = sharedAccount.copy(
                                        linkedAccountId = linkedAccountId,
                                        updatedAt = Instant.now()
                                    )
                                    sharedAccountRepository.save(updated)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SharedAccounts", "Failed to link account", e)
                            }
                        }

                        // Reload shared accounts
                        loadSharedAccounts()

                        // Navigate to the shared account detail
                        navigation.navigateTo(
                            SharedAccountDetailScreen(sharedAccountId = invitation.sharedAccountId.value)
                        )
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("SharedAccounts", "Error accepting invitation", e)
                // TODO: Show error toast/snackbar
            } finally {
                showAcceptInviteModal = false
            }
        }
    }

    private suspend fun loadAccounts() {
        accounts = try {
            accountRepository.findAll()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun onCreateAccount(name: String, currency: String, linkedAccountId: AccountId?) {
        viewModelScope.launch {
            // Create the account with proper types
            val trimmedName = NotBlankTrimmedString.unsafe(name)
            val assetCode = AssetCode.unsafe(currency)
            val userUid = currentUserUid ?: "local-user"

            val newAccount = SharedAccount(
                id = SharedAccountId(UUID.randomUUID()),
                name = trimmedName,
                currency = assetCode,
                owners = listOf(userUid),
                createdBy = userUid,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                linkedAccountId = linkedAccountId
            )

            // Save to local repository
            sharedAccountRepository.save(newAccount)

            // Also save to Firestore for cross-device access
            firestoreInvitationRepository.saveSharedAccount(
                id = newAccount.id,
                name = newAccount.name.value,
                currency = newAccount.currency.code,
                owners = newAccount.owners,
                createdBy = newAccount.createdBy,
                createdAt = newAccount.createdAt.toEpochMilli(),
                updatedAt = newAccount.updatedAt.toEpochMilli(),
                linkedAccountId = newAccount.linkedAccountId?.value?.toString()
            )

            showCreateModal = false
            loadSharedAccounts()
        }
    }
}
