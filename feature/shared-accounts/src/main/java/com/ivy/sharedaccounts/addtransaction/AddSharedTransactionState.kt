package com.ivy.sharedaccounts.addtransaction

import androidx.compose.runtime.Immutable
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransactionType

@Immutable
data class AddSharedTransactionState(
    val sharedAccountId: SharedAccountId? = null,
    val type: SharedTransactionType = SharedTransactionType.EXPENSE,
    val amount: String = "",
    val title: String = "",
    val description: String = "",
    val currency: String = "USD",
    val isSaving: Boolean = false
)
