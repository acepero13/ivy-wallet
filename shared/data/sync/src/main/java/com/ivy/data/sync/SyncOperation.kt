package com.ivy.data.sync

import java.util.UUID

/**
 * Represents a single synchronization operation to be performed.
 *
 * @property id Unique identifier for this operation
 * @property type The type of sync operation (CREATE, UPDATE, DELETE)
 * @property entityType The type of entity being synced (e.g., "transaction", "account")
 * @property entityId The ID of the entity being synced
 * @property data The data payload for the operation (nullable for DELETE)
 * @property timestamp When this operation was created
 * @property retryCount How many times this operation has been retried
 * @property status Current status of the operation
 */
data class SyncOperation(
    val id: String = UUID.randomUUID().toString(),
    val type: SyncOperationType,
    val entityType: String,
    val entityId: String,
    val data: Map<String, Any?>? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val status: SyncOperationStatus = SyncOperationStatus.PENDING
)

/**
 * Type of synchronization operation
 */
enum class SyncOperationType {
    CREATE,
    UPDATE,
    DELETE
}

/**
 * Status of a sync operation
 */
enum class SyncOperationStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * Result of a sync operation
 */
sealed class SyncResult {
    data class Success(val operationId: String) : SyncResult()
    data class Failure(val operationId: String, val error: Throwable) : SyncResult()
}
