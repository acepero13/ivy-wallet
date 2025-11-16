package com.ivy.sharedaccounts

import com.ivy.data.model.SharedAccountId

sealed interface SharedAccountsEvent {
    data class OnSharedAccountClick(val accountId: SharedAccountId) : SharedAccountsEvent
    data object OnCreateSharedAccount : SharedAccountsEvent
    data object OnDismissCreateModal : SharedAccountsEvent
    data class OnCreateAccount(val name: String, val currency: String) : SharedAccountsEvent
}
