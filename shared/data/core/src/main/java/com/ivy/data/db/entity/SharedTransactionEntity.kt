package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ivy.base.kotlinxserilzation.KSerializerInstant
import com.ivy.base.kotlinxserilzation.KSerializerUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Type of shared transaction.
 */
enum class SharedTransactionType {
    INCOME,
    EXPENSE
}

/**
 * Room entity representing a transaction within a shared account.
 * These transactions are visible to all owners of the shared account.
 *
 * @property sharedAccountId ID of the shared account this transaction belongs to
 * @property type Type of transaction (INCOME or EXPENSE)
 * @property amount Transaction amount (positive value)
 * @property title Optional title/description
 * @property description Optional detailed description
 * @property categoryId Optional category ID
 * @property time Timestamp when the transaction occurred
 * @property createdBy Firebase Auth UID of user who created this transaction
 * @property createdAt Timestamp when the transaction was created
 * @property updatedAt Timestamp of the last update
 * @property updatedBy Firebase Auth UID of user who last updated this transaction
 * @property deleted Whether this transaction has been soft-deleted
 * @property remoteId Optional Firebase Firestore document ID (null until synced)
 * @property isSynced Whether this entity has been synced to Firestore
 * @property id Local UUID primary key
 */
@Suppress("DataClassDefaultValues")
@Keep
@Serializable
@Entity(
    tableName = "shared_transactions",
    foreignKeys = [
        ForeignKey(
            entity = SharedAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["sharedAccountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sharedAccountId"]),
        Index(value = ["time"]),
        Index(value = ["deleted"])
    ]
)
data class SharedTransactionEntity(
    @SerialName("sharedAccountId")
    @Serializable(with = KSerializerUUID::class)
    val sharedAccountId: UUID,

    @SerialName("type")
    val type: SharedTransactionType,

    @SerialName("amount")
    val amount: Double,

    @SerialName("title")
    val title: String? = null,

    @SerialName("description")
    val description: String? = null,

    @SerialName("categoryId")
    @Serializable(with = KSerializerUUID::class)
    val categoryId: UUID? = null,

    @SerialName("time")
    @Serializable(with = KSerializerInstant::class)
    val time: Instant,

    @SerialName("createdBy")
    val createdBy: String,

    @SerialName("createdAt")
    @Serializable(with = KSerializerInstant::class)
    val createdAt: Instant,

    @SerialName("updatedAt")
    @Serializable(with = KSerializerInstant::class)
    val updatedAt: Instant,

    @SerialName("updatedBy")
    val updatedBy: String,

    @SerialName("deleted")
    val deleted: Boolean = false,

    @SerialName("remoteId")
    val remoteId: String? = null,

    @SerialName("isSynced")
    val isSynced: Boolean = false,

    @PrimaryKey
    @SerialName("id")
    @Serializable(with = KSerializerUUID::class)
    val id: UUID = UUID.randomUUID()
)
