package com.ivy.data.model

import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.sync.Identifiable
import com.ivy.data.model.sync.UniqueId
import java.time.Instant
import java.util.UUID

/**
 * Unique identifier for a SharedAccount.
 */
@JvmInline
value class SharedAccountId(override val value: UUID) : UniqueId

/**
 * Represents a shared account between two or more users.
 * This allows multiple users (e.g., a couple) to collaboratively track expenses.
 *
 * @property id Unique identifier for the shared account
 * @property name Name of the shared account (e.g., "Joint Account", "Vacation Fund")
 * @property currency Currency code for the shared account
 * @property owners List of user IDs who own/have access to this shared account
 * @property createdBy User ID of the account creator
 * @property createdAt Timestamp when the shared account was created
 * @property updatedAt Timestamp of the last update
 */
data class SharedAccount(
    override val id: SharedAccountId,
    val name: NotBlankTrimmedString,
    val currency: AssetCode,
    val owners: List<String>, // Firebase Auth UIDs of owners
    val createdBy: String, // Firebase Auth UID
    val createdAt: Instant,
    val updatedAt: Instant,
) : Identifiable<SharedAccountId>
