package com.ivy.data.sync

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.DataObserver
import com.ivy.data.DataWriteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
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
    private val dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.io)

    /**
     * Start observing data changes and enqueuing sync operations
     */
    fun start() {
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
    }

    /**
     * Handle data write events and enqueue sync operations
     */
    private suspend fun handleDataWriteEvent(event: DataWriteEvent) {
        try {
            when (event) {
                is DataWriteEvent.SaveSharedAccounts -> {
                    // Sync operations for shared accounts will be handled
                    // by a future sync adapter that reads from the database
                    // and enqueues operations for unsynced items
                    Timber.d("Shared accounts saved, triggering sync check")
                    syncManager.forceSync()
                }
                is DataWriteEvent.DeleteSharedAccounts -> {
                    Timber.d("Shared accounts deleted, triggering sync check")
                    syncManager.forceSync()
                }
                is DataWriteEvent.SaveSharedTransactions -> {
                    Timber.d("Shared transactions saved, triggering sync check")
                    syncManager.forceSync()
                }
                is DataWriteEvent.DeleteSharedTransactions -> {
                    Timber.d("Shared transactions deleted, triggering sync check")
                    syncManager.forceSync()
                }
                else -> {
                    // Ignore other events for now
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to handle data write event: $event")
        }
    }
}
