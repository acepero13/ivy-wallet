package com.ivy.sharedaccounts.addtransaction

import com.ivy.data.model.SharedTransactionType

sealed interface AddSharedTransactionEvent {
    data class OnAmountChange(val amount: String) : AddSharedTransactionEvent
    data class OnTitleChange(val title: String) : AddSharedTransactionEvent
    data class OnDescriptionChange(val description: String) : AddSharedTransactionEvent
    data class OnTypeChange(val type: SharedTransactionType) : AddSharedTransactionEvent
    data object OnSave : AddSharedTransactionEvent
    data object OnDismiss : AddSharedTransactionEvent
}
