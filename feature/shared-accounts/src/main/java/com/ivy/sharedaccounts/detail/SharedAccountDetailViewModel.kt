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
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.repository.SharedAccountRepository
import com.ivy.data.repository.SharedTransactionRepository
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
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class SharedAccountDetailViewModel @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val sharedAccountRepository: SharedAccountRepository,
    private val sharedTransactionRepository: SharedTransactionRepository,
    private val dataObserver: DataObserver,
    private val authRepository: AuthRepository,
    private val createInvitationUseCase: CreateInvitationUseCase,
    private val generateInviteLinkUseCase: GenerateInviteLinkUseCase,
) : ComposeViewModel<SharedAccountDetailState, SharedAccountDetailEvent>() {

    private var sharedAccountId by mutableStateOf<SharedAccountId?>(null)
    private var sharedAccount by mutableStateOf<SharedAccount?>(null)
    private var transactions by mutableStateOf<ImmutableList<SharedTransaction>>(emptyList<SharedTransaction>().toImmutableList())
    private var totalIncome by mutableStateOf(0.0)
    private var totalExpense by mutableStateOf(0.0)
    private var balance by mutableStateOf(0.0)
    private var isLoading by mutableStateOf(true)

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
            isLoading = isLoading
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
                // TODO: Navigate to edit account screen
            }
            is SharedAccountDetailEvent.OnShareInvite -> {
                android.util.Log.d("SharedAccountDetail", "OnShareInvite event received")
                android.widget.Toast.makeText(context, "Share button clicked!", android.widget.Toast.LENGTH_SHORT).show()
                shareInvite()
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
            loadData()
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
}
