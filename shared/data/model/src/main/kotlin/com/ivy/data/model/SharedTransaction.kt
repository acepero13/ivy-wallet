package com.ivy.data.model

import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.sync.Identifiable
import com.ivy.data.model.sync.UniqueId
import java.time.Instant
import java.util.UUID

/**
 * Unique identifier for a SharedTransaction.
 */
@JvmInline
value class SharedTransactionId(override val value: UUID) : UniqueId

/**
 * Type of shared transaction.
 */
enum class SharedTransactionType {
    INCOME,
    EXPENSE,
}

/**
 * Represents a transaction within a shared account.
 * These transactions are visible to all owners of the shared account.
 *
 * @property id Unique identifier for the shared transaction
 * @property sharedAccountId ID of the shared account this transaction belongs to
 * @property type Type of transaction (income or expense)
 * @property amount Transaction amount (positive value)
 * @property title Optional title/description
 * @property description Optional detailed description
 * @property category Optional category ID
 * @property time Timestamp when the transaction occurred
 * @property createdBy User ID who created this transaction
 * @property createdAt Timestamp when the transaction was created
 * @property updatedAt Timestamp of the last update
 * @property updatedBy User ID who last updated this transaction
 * @property deleted Whether this transaction has been soft-deleted
 */
data class SharedTransaction(
    override val id: SharedTransactionId,
    val sharedAccountId: SharedAccountId,
    val type: SharedTransactionType,
    val amount: Double, // Positive value
    val title: NotBlankTrimmedString?,
    val description: NotBlankTrimmedString?,
    val category: CategoryId?,
    val time: Instant,
    val createdBy: String, // Firebase Auth UID
    val createdAt: Instant,
    val updatedAt: Instant,
    val updatedBy: String, // Firebase Auth UID
    val deleted: Boolean = false,
) : Identifiable<SharedTransactionId>
