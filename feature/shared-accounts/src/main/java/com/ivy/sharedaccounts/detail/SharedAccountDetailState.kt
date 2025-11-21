package com.ivy.sharedaccounts.detail

import androidx.compose.runtime.Immutable
import com.ivy.data.model.Account
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedTransaction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class SharedAccountDetailState(
    val sharedAccount: SharedAccount? = null,
    val transactions: ImmutableList<SharedTransaction> = persistentListOf(),
    val baseCurrency: String = "USD",
    val isLoading: Boolean = true,
    val currentUserUid: String? = null,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
    val showLinkAccountModal: Boolean = false,
    val availableAccounts: ImmutableList<Account> = persistentListOf(),
    val pendingLinkAction: Boolean = false,
    val pendingLinkAccountId: com.ivy.data.model.AccountId? = null
)
