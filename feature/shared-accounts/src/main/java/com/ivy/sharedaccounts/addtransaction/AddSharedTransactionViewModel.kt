package com.ivy.sharedaccounts.addtransaction

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionId
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.SharedTransactionRepository
import com.ivy.data.sync.FirestoreInvitationRepository
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@Stable
@SuppressLint("StaticFieldLeak")
@HiltViewModel
class AddSharedTransactionViewModel @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val sharedTransactionRepository: SharedTransactionRepository,
    private val firestoreInvitationRepository: FirestoreInvitationRepository,
) : ComposeViewModel<AddSharedTransactionState, AddSharedTransactionEvent>() {

    private var sharedAccountId by mutableStateOf<SharedAccountId?>(null)
    private var type by mutableStateOf(SharedTransactionType.EXPENSE)
    private var amount by mutableStateOf("")
    private var title by mutableStateOf("")
    private var description by mutableStateOf("")
    private var currency by mutableStateOf("USD")
    private var isSaving by mutableStateOf(false)

    var onTransactionSaved: (() -> Unit)? = null

    @Composable
    override fun uiState(): AddSharedTransactionState {
        return AddSharedTransactionState(
            sharedAccountId = sharedAccountId,
            type = type,
            amount = amount,
            title = title,
            description = description,
            currency = currency,
            isSaving = isSaving
        )
    }

    override fun onEvent(event: AddSharedTransactionEvent) {
        when (event) {
            is AddSharedTransactionEvent.OnAmountChange -> {
                amount = event.amount
            }
            is AddSharedTransactionEvent.OnTitleChange -> {
                title = event.title
            }
            is AddSharedTransactionEvent.OnDescriptionChange -> {
                description = event.description
            }
            is AddSharedTransactionEvent.OnTypeChange -> {
                type = event.type
            }
            AddSharedTransactionEvent.OnSave -> {
                saveTransaction()
            }
            AddSharedTransactionEvent.OnDismiss -> {
                resetForm()
            }
        }
    }

    fun setSharedAccountId(id: SharedAccountId, accountCurrency: String) {
        sharedAccountId = id
        currency = accountCurrency
    }

    private fun saveTransaction() {
        val accountId = sharedAccountId ?: return
        val amountValue = amount.toDoubleOrNull() ?: return

        if (amountValue <= 0.0) return

        isSaving = true

        viewModelScope.launch {
            try {
                val transaction = SharedTransaction(
                    id = SharedTransactionId(UUID.randomUUID()),
                    sharedAccountId = accountId,
                    type = type,
                    amount = amountValue,
                    title = if (title.isNotBlank()) NotBlankTrimmedString.unsafe(title.trim()) else null,
                    description = if (description.isNotBlank()) NotBlankTrimmedString.unsafe(description.trim()) else null,
                    category = null,
                    time = Instant.now(),
                    createdBy = "currentUser", // TODO: Get from AuthRepository
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    updatedBy = "currentUser", // TODO: Get from AuthRepository
                    deleted = false
                )

                // Save to local database
                sharedTransactionRepository.save(transaction)

                // Also save to Firestore for cross-device sync
                firestoreInvitationRepository.saveSharedTransaction(
                    sharedAccountId = transaction.sharedAccountId,
                    transactionId = transaction.id.value.toString(),
                    type = transaction.type.name,
                    amount = transaction.amount,
                    title = transaction.title?.value,
                    description = transaction.description?.value,
                    category = transaction.category?.value?.toString(),
                    time = transaction.time.toEpochMilli(),
                    createdBy = transaction.createdBy,
                    createdAt = transaction.createdAt.toEpochMilli(),
                    updatedAt = transaction.updatedAt.toEpochMilli(),
                    updatedBy = transaction.updatedBy,
                    deleted = transaction.deleted
                )

                isSaving = false
                resetForm()
                onTransactionSaved?.invoke()
            } catch (e: Exception) {
                isSaving = false
                android.util.Log.e("AddSharedTransaction", "Error saving transaction", e)
                // TODO: Show error to user
            }
        }
    }

    private fun resetForm() {
        amount = ""
        title = ""
        description = ""
        type = SharedTransactionType.EXPENSE
    }
}
