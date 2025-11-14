package com.ivy.data.sync

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.DataObserver
import com.ivy.data.DataWriteEvent
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransactionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates synchronization between local data changes and remote backend.
 *
 * This coordinator listens to data write events from repositories and
 * enqueues sync operations for processing by SyncManager.
 *
 * This design avoids circular dependencies by keeping sync logic
 * separate from repositories.
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val dataObserver: DataObserver,
    private val syncManager: SyncManager,
    private val syncAdapter: SyncAdapter,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.io)

    /**
     * Start observing data changes and enqueuing sync operations
     */
    fun start() {
        // Set up callback to mark items as synced after successful push
        syncManager.onOperationSynced = { operation ->
            handleOperationSynced(operation)
        }

        dataObserver.writeEvents
            .onEach { event ->
                handleDataWriteEvent(event)
            }
            .launchIn(scope)

        // Start background sync processing
        syncManager.startSync(intervalMs = 30_000L) // Sync every 30 seconds
    }

    /**
     * Stop observing data changes
     */
    fun stop() {
        syncManager.stopSync()
        syncManager.onOperationSynced = null
    }

    /**
     * Handle data write events and enqueue sync operations
     */
    private suspend fun handleDataWriteEvent(event: DataWriteEvent) {
        try {
            when (event) {
                is DataWriteEvent.SaveSharedAccounts -> {
                    Timber.d("Shared accounts saved, enqueuing unsynced items")
                    enqueueUnsyncedAccounts()
                }
                is DataWriteEvent.DeleteSharedAccounts -> {
                    Timber.d("Shared accounts deleted, enqueuing unsynced items")
                    enqueueUnsyncedAccounts()
                }
                is DataWriteEvent.SaveSharedTransactions -> {
                    Timber.d("Shared transactions saved, enqueuing unsynced items")
                    enqueueUnsyncedTransactions()
                }
                is DataWriteEvent.DeleteSharedTransactions -> {
                    Timber.d("Shared transactions deleted, enqueuing unsynced items")
                    enqueueUnsyncedTransactions()
                }
                else -> {
                    // Ignore other events for now
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to handle data write event: $event")
        }
    }

    /**
     * Enqueue all unsynced shared accounts for sync
     */
    private suspend fun enqueueUnsyncedAccounts() {
        try {
            val operations = syncAdapter.getUnsyncedAccountOperations()
            operations.forEach { operation ->
                syncManager.enqueueOperation(operation)
            }
            Timber.d("Enqueued ${operations.size} unsynced accounts")
        } catch (e: Exception) {
            Timber.e(e, "Failed to enqueue unsynced accounts")
        }
    }

    /**
     * Enqueue all unsynced shared transactions for sync
     */
    private suspend fun enqueueUnsyncedTransactions() {
        try {
            val operations = syncAdapter.getUnsyncedTransactionOperations()
            operations.forEach { operation ->
                syncManager.enqueueOperation(operation)
            }
            Timber.d("Enqueued ${operations.size} unsynced transactions")
        } catch (e: Exception) {
            Timber.e(e, "Failed to enqueue unsynced transactions")
        }
    }

    /**
     * Handle successful sync operation completion
     */
    private suspend fun handleOperationSynced(operation: SyncOperation) {
        try {
            when (operation.entityType) {
                "sharedAccount" -> {
                    val accountId = SharedAccountId(UUID.fromString(operation.entityId))
                    syncAdapter.markAccountAsSynced(accountId)
                    Timber.d("Marked shared account $accountId as synced")
                }
                "sharedTransaction" -> {
                    val transactionId = SharedTransactionId(UUID.fromString(operation.entityId))
                    syncAdapter.markTransactionAsSynced(transactionId)
                    Timber.d("Marked shared transaction $transactionId as synced")
                }
                else -> {
                    Timber.w("Unknown entity type for synced operation: ${operation.entityType}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark operation as synced: ${operation.entityId}")
        }
    }
}
