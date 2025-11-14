package com.ivy.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe queue for managing sync operations.
 *
 * Responsibilities:
 * - Enqueue new operations
 * - Dequeue operations for processing
 * - Track operation status
 * - Handle retry logic
 * - Persist queue state (future: could be backed by Room)
 *
 * This implementation uses in-memory storage for simplicity.
 * For production, consider persisting to Room database to survive app restarts.
 */
@Singleton
class SyncQueue @Inject constructor() {
    private val mutex = Mutex()
    private val _operations = MutableStateFlow<List<SyncOperation>>(emptyList())

    /**
     * Observable list of all operations in the queue
     */
    val operations: StateFlow<List<SyncOperation>> = _operations.asStateFlow()

    /**
     * Maximum number of retry attempts for failed operations
     */
    private val maxRetries = 3

    /**
     * Enqueue a new sync operation
     *
     * @param operation The operation to add to the queue
     */
    suspend fun enqueue(operation: SyncOperation) = mutex.withLock {
        _operations.update { current ->
            current + operation
        }
    }

    /**
     * Dequeue the next pending operation for processing
     *
     * @return The next operation to process, or null if queue is empty or all operations are in progress
     */
    suspend fun dequeue(): SyncOperation? = mutex.withLock {
        val nextOp = _operations.value.firstOrNull { it.status == SyncOperationStatus.PENDING }

        if (nextOp != null) {
            val updatedOp = nextOp.copy(status = SyncOperationStatus.IN_PROGRESS)
            _operations.update { current ->
                current.map { op ->
                    if (op.id == nextOp.id) {
                        updatedOp
                    } else {
                        op
                    }
                }
            }
            return@withLock updatedOp
        }

        return@withLock null
    }

    /**
     * Mark an operation as completed and remove it from the queue
     *
     * @param operationId The ID of the completed operation
     */
    suspend fun markCompleted(operationId: String) = mutex.withLock {
        _operations.update { current ->
            current.filter { it.id != operationId }
        }
    }

    /**
     * Mark an operation as failed and optionally retry it
     *
     * @param operationId The ID of the failed operation
     * @param error The error that caused the failure
     */
    suspend fun markFailed(operationId: String, error: Throwable) = mutex.withLock {
        _operations.update { current ->
            current.map { op ->
                if (op.id == operationId) {
                    val newRetryCount = op.retryCount + 1
                    if (newRetryCount < maxRetries) {
                        // Retry with exponential backoff
                        op.copy(
                            status = SyncOperationStatus.PENDING,
                            retryCount = newRetryCount
                        )
                    } else {
                        // Max retries exceeded, mark as permanently failed
                        op.copy(
                            status = SyncOperationStatus.FAILED,
                            retryCount = newRetryCount
                        )
                    }
                } else {
                    op
                }
            }
        }
    }

    /**
     * Get all pending operations
     *
     * @return List of operations with PENDING status
     */
    suspend fun getPendingOperations(): List<SyncOperation> = mutex.withLock {
        _operations.value.filter { it.status == SyncOperationStatus.PENDING }
    }

    /**
     * Get all failed operations (those that exceeded retry limit)
     *
     * @return List of operations with FAILED status
     */
    suspend fun getFailedOperations(): List<SyncOperation> = mutex.withLock {
        _operations.value.filter { it.status == SyncOperationStatus.FAILED }
    }

    /**
     * Clear all operations from the queue
     */
    suspend fun clear() = mutex.withLock {
        _operations.update { emptyList() }
    }

    /**
     * Get the total count of operations in the queue
     *
     * @return Number of operations
     */
    suspend fun size(): Int = mutex.withLock {
        _operations.value.size
    }

    /**
     * Check if the queue is empty
     *
     * @return true if no operations in queue, false otherwise
     */
    suspend fun isEmpty(): Boolean = mutex.withLock {
        _operations.value.isEmpty()
    }

    /**
     * Retry a specific failed operation
     *
     * @param operationId The ID of the operation to retry
     */
    suspend fun retry(operationId: String) = mutex.withLock {
        _operations.update { current ->
            current.map { op ->
                if (op.id == operationId && op.status == SyncOperationStatus.FAILED) {
                    op.copy(
                        status = SyncOperationStatus.PENDING,
                        retryCount = 0
                    )
                } else {
                    op
                }
            }
        }
    }

    /**
     * Retry all failed operations
     */
    suspend fun retryAll() = mutex.withLock {
        _operations.update { current ->
            current.map { op ->
                if (op.status == SyncOperationStatus.FAILED) {
                    op.copy(
                        status = SyncOperationStatus.PENDING,
                        retryCount = 0
                    )
                } else {
                    op
                }
            }
        }
    }
}
