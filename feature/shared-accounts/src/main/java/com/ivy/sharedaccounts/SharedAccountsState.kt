package com.ivy.sharedaccounts

import androidx.compose.runtime.Immutable
import com.ivy.data.model.SharedAccount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class SharedAccountsState(
    val sharedAccounts: ImmutableList<SharedAccount> = persistentListOf(),
    val baseCurrency: String = "USD",
    val isLoading: Boolean = true,
    val currentUserUid: String? = null
)
