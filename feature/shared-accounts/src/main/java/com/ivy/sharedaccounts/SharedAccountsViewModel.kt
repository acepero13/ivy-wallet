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
        // Observe shared accounts reactively for real-time updates
        viewModelScope.launch {
            sharedAccountRepository.observeAll().collectLatest { accounts ->
                android.util.Log.d("SharedAccounts", "Shared accounts updated from Flow: ${accounts.size} items")
                sharedAccounts = accounts
                isLoading = false

                // Set up real-time listeners when accounts change
                setupRealtimeListeners()
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
            loadAccounts()
            loadBaseCurrency()
            // TODO: Load current user UID from AuthRepository
            // Note: Shared accounts are now loaded reactively via Flow in init
        }
    }

    private fun setupRealtimeListeners() {
        // Note: Real-time listeners are now managed by SharedAccountSyncService
        // This is a no-op but kept for reference
        android.util.Log.d("SharedAccounts", "Listeners managed by SharedAccountSyncService")
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

                        // Shared accounts will reload automatically via Flow
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
            // Shared accounts will reload automatically via Flow
        }
    }
}
