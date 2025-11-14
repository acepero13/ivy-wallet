package com.ivy.data.sync

import com.ivy.base.threading.DispatchersProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central orchestrator for synchronization operations.
 *
 * Responsibilities:
 * - Coordinate push/pull operations with remote backend
 * - Process sync queue
 * - Manage sync lifecycle
 * - Handle conflicts and errors
 * - Provide sync status to UI
 *
 * Usage:
 * ```kotlin
 * syncManager.enqueueOperation(operation)
 * syncManager.startSync()
 * ```
 */
@Singleton
class SyncManager @Inject constructor(
    private val syncQueue: SyncQueue,
    private val remoteService: RemoteService,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.io)
    private var syncJob: Job? = null

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Enqueue a new operation to be synced
     *
     * @param operation The operation to sync
     */
    suspend fun enqueueOperation(operation: SyncOperation) {
        syncQueue.enqueue(operation)
        // Trigger processing if not already running
        if (_syncState.value == SyncState.Idle) {
            processQueue()
        }
    }

    /**
     * Start continuous sync processing.
     * This will process the queue and periodically pull changes from remote.
     *
     * @param intervalMs Interval between sync cycles in milliseconds (default: 30 seconds)
     */
    fun startSync(intervalMs: Long = 30_000L) {
        if (syncJob?.isActive == true) {
            return // Already running
        }

        syncJob = scope.launch {
            while (isActive) {
                try {
                    processQueue()
                    pullChanges()
                    delay(intervalMs)
                } catch (e: Exception) {
                    _syncState.value = SyncState.Error(e)
                    delay(intervalMs * 2) // Back off on error
                }
            }
        }
    }

    /**
     * Stop continuous sync processing
     */
    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
        _syncState.value = SyncState.Idle
    }

    /**
     * Process all pending operations in the queue
     */
    private suspend fun processQueue() {
        if (!remoteService.isAvailable()) {
            _syncState.value = SyncState.Offline
            return
        }

        _syncState.value = SyncState.Syncing

        var operation = syncQueue.dequeue()
        while (operation != null) {
            try {
                val result = remoteService.pushOperation(operation)
                when (result) {
                    is SyncResult.Success -> {
                        syncQueue.markCompleted(operation.id)
                    }
                    is SyncResult.Failure -> {
                        syncQueue.markFailed(operation.id, result.error)
                    }
                }
            } catch (e: Exception) {
                syncQueue.markFailed(operation.id, e)
            }

            operation = syncQueue.dequeue()
        }

        _syncState.value = if (syncQueue.isEmpty()) {
            SyncState.Idle
        } else {
            // Some operations failed but queue not empty
            SyncState.Error(Exception("Some operations failed to sync"))
        }
    }

    /**
     * Pull changes from remote service for all entity types
     */
    private suspend fun pullChanges() {
        // TODO: Track last sync timestamps per entity type
        // For now, we'll pull all entity types
        // This will be expanded in future PRs when we have actual entities
    }

    /**
     * Subscribe to real-time changes for a specific entity type
     *
     * @param entityType The type of entity to subscribe to
     * @param onChanges Callback invoked when changes are received
     */
    suspend fun subscribeToChanges(
        entityType: String,
        onChanges: (List<SyncOperation>) -> Unit
    ): SyncSubscription {
        return remoteService.subscribeToChanges(entityType, onChanges)
    }

    /**
     * Force a full sync cycle (push queue + pull changes)
     */
    suspend fun forceSync() {
        processQueue()
        pullChanges()
    }

    /**
     * Get the current sync queue status
     *
     * @return Queue status information
     */
    suspend fun getQueueStatus(): QueueStatus {
        return QueueStatus(
            pending = syncQueue.getPendingOperations().size,
            failed = syncQueue.getFailedOperations().size,
            total = syncQueue.size()
        )
    }

    /**
     * Retry all failed operations
     */
    suspend fun retryFailedOperations() {
        syncQueue.retryAll()
        processQueue()
    }

    /**
     * Clear all operations from the queue
     */
    suspend fun clearQueue() {
        syncQueue.clear()
    }
}

/**
 * Represents the current state of synchronization
 */
sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data object Offline : SyncState()
    data class Error(val exception: Throwable) : SyncState()
}

/**
 * Status information about the sync queue
 */
data class QueueStatus(
    val pending: Int,
    val failed: Int,
    val total: Int
)
