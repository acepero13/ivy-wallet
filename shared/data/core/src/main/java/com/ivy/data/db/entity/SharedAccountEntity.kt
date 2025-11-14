package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ivy.base.kotlinxserilzation.KSerializerInstant
import com.ivy.base.kotlinxserilzation.KSerializerUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Room entity representing a shared account.
 * A shared account allows multiple users to collaboratively track expenses.
 *
 * @property name Name of the shared account
 * @property currency Currency code (e.g., "USD", "EUR")
 * @property owners JSON array of Firebase Auth UIDs of account owners
 * @property createdBy Firebase Auth UID of the user who created this account
 * @property createdAt Timestamp when the account was created
 * @property updatedAt Timestamp of the last update
 * @property remoteId Optional Firebase Firestore document ID (null until synced)
 * @property isSynced Whether this entity has been synced to Firestore
 * @property id Local UUID primary key
 */
@Suppress("DataClassDefaultValues")
@Keep
@Serializable
@Entity(tableName = "shared_accounts")
data class SharedAccountEntity(
    @SerialName("name")
    val name: String,

    @SerialName("currency")
    val currency: String,

    @SerialName("owners")
    val owners: String, // JSON array of owner UIDs: ["uid1", "uid2"]

    @SerialName("createdBy")
    val createdBy: String,

    @SerialName("createdAt")
    @Serializable(with = KSerializerInstant::class)
    val createdAt: Instant,

    @SerialName("updatedAt")
    @Serializable(with = KSerializerInstant::class)
    val updatedAt: Instant,

    @SerialName("remoteId")
    val remoteId: String? = null,

    @SerialName("isSynced")
    val isSynced: Boolean = false,

    @PrimaryKey
    @SerialName("id")
    @Serializable(with = KSerializerUUID::class)
    val id: UUID = UUID.randomUUID()
)
