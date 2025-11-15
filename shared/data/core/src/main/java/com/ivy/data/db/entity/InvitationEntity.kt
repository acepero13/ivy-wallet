package com.ivy.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "invitations",
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
        Index(value = ["token"], unique = true),
        Index(value = ["inviteeEmail"]),
        Index(value = ["status"])
    ]
)
data class InvitationEntity(
    @PrimaryKey
    val id: UUID,
    val sharedAccountId: UUID,
    val inviterUid: String,
    val inviteeEmail: String,
    val token: String,
    val status: InvitationStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
    val acceptedAt: Instant? = null,
    val acceptedBy: String? = null
)

enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    EXPIRED,
    REVOKED
}
