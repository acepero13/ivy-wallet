package com.ivy.sharedaccounts.detail

import com.ivy.data.model.AccountId
import com.ivy.data.model.SharedTransactionId

sealed interface SharedAccountDetailEvent {
    data object OnAddTransaction : SharedAccountDetailEvent
    data class OnTransactionClick(val transactionId: SharedTransactionId) : SharedAccountDetailEvent
    data object OnBack : SharedAccountDetailEvent
    data object OnEditAccount : SharedAccountDetailEvent
    data object OnShareInvite : SharedAccountDetailEvent
    data object OnDismissLinkAccountModal : SharedAccountDetailEvent
    data class OnLinkAccount(val accountId: AccountId?) : SharedAccountDetailEvent
}
