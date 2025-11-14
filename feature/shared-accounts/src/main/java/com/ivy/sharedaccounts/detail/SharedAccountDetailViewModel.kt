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
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.repository.SharedAccountRepository
import com.ivy.data.repository.SharedTransactionRepository
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
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
    private var sharedAccount by mutableStateOf(SharedAccountDetailState().sharedAccount)
    private var transactions by mutableStateOf(SharedAccountDetailState().transactions)
    private var totalIncome by mutableStateOf(0.0)
    private var totalExpense by mutableStateOf(0.0)
    private var balance by mutableStateOf(0.0)
    private var isLoading by mutableStateOf(true)

    init {
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
        LaunchedEffect(sharedAccountId) {
            if (sharedAccountId != null) {
                loadData()
            }
        }

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
        sharedAccountId = id
    }

    private suspend fun loadData() {
        val accountId = sharedAccountId ?: return

        isLoading = true

        sharedAccount = try {
            sharedAccountRepository.findById(accountId)
        } catch (e: Exception) {
            null
        }

        val allTransactions = try {
            sharedTransactionRepository.findBySharedAccountId(accountId)
                .filter { !it.deleted }
                .sortedByDescending { it.time }
        } catch (e: Exception) {
            emptyList()
        }

        transactions = allTransactions.toImmutableList()

        totalIncome = allTransactions
            .filter { it.type == SharedTransactionType.INCOME }
            .sumOf { it.amount }

        totalExpense = allTransactions
            .filter { it.type == SharedTransactionType.EXPENSE }
            .sumOf { it.amount }

        balance = totalIncome - totalExpense
        isLoading = false
    }
}
