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
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.repository.SharedAccountRepository
import com.ivy.data.repository.SharedTransactionRepository
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
        }
    }

    fun setSharedAccountId(id: SharedAccountId) {
        android.util.Log.d("SharedAccountDetail", "setSharedAccountId called with: ${id.value}")
        sharedAccountId = id
        android.util.Log.d("SharedAccountDetail", "About to load data directly")

        // Load data directly in a launched effect from the composable instead
        isLoading = true
    }

    suspend fun loadDataForAccount(id: SharedAccountId) {
        android.util.Log.d("SharedAccountDetail", "loadDataForAccount called with: ${id.value}")
        sharedAccountId = id
        loadData()
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
        isLoading = true

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
