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
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.SharedAccountRepository
import com.ivy.navigation.Navigation
import com.ivy.navigation.SharedAccountDetailScreen
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
    private val baseCurrencyAct: BaseCurrencyAct,
    private val dataObserver: DataObserver,
    private val navigation: Navigation,
) : ComposeViewModel<SharedAccountsState, SharedAccountsEvent>() {

    private var sharedAccounts by mutableStateOf<List<SharedAccount>>(emptyList())
    private var baseCurrency by mutableStateOf("")
    private var isLoading by mutableStateOf(true)
    private var currentUserUid by mutableStateOf<String?>(null)
    private var showCreateModal by mutableStateOf(false)

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
            baseCurrency = baseCurrency,
            isLoading = isLoading,
            currentUserUid = currentUserUid,
            showCreateModal = showCreateModal
        )
    }

    override fun onEvent(event: SharedAccountsEvent) {
        when (event) {
            is SharedAccountsEvent.OnSharedAccountClick -> onSharedAccountClick(event.accountId)
            SharedAccountsEvent.OnCreateSharedAccount -> onCreateSharedAccount()
            SharedAccountsEvent.OnDismissCreateModal -> onDismissCreateModal()
            is SharedAccountsEvent.OnCreateAccount -> onCreateAccount(event.name, event.currency)
        }
    }

    private fun onStart() {
        viewModelScope.launch {
            loadSharedAccounts()
            loadBaseCurrency()
            // TODO: Load current user UID from AuthRepository
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

    private fun onCreateAccount(name: String, currency: String) {
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
                updatedAt = Instant.now()
            )

            sharedAccountRepository.save(newAccount)
            showCreateModal = false
            loadSharedAccounts()
        }
    }
}
