package com.ivy.data.model

import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.sync.UniqueId
import java.time.Instant
import java.util.UUID

/**
 * Represents an invitation to join a shared account
 */
data class Invitation(
    val id: InvitationId,
    val sharedAccountId: SharedAccountId,
    val inviterUid: String,
    val inviteeEmail: String,
    val token: String,
    val status: InvitationStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
    val acceptedAt: Instant? = null,
    val acceptedBy: String? = null
)

/**
 * Unique identifier for an Invitation
 */
@JvmInline
value class InvitationId(override val value: UUID) : UniqueId {
    override fun toString(): String = value.toString()
}

/**
 * Status of an invitation
 */
enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    EXPIRED,
    REVOKED
}
