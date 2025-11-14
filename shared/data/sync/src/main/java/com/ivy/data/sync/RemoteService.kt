package com.ivy.data.sync

/**
 * Interface for remote data synchronization service.
 *
 * This abstraction allows for different backend implementations
 * (Firebase Firestore, Supabase, custom REST API, etc.)
 * without changing the sync layer logic.
 *
 * Implementations should handle:
 * - Authentication
 * - Network error handling
 * - Retry logic (if not handled by SyncQueue)
 * - Data serialization/deserialization
 */
interface RemoteService {
    /**
     * Push a single operation to the remote backend
     *
     * @param operation The sync operation to push
     * @return Result indicating success or failure
     */
    suspend fun pushOperation(operation: SyncOperation): SyncResult

    /**
     * Pull changes from remote backend for a specific entity type
     *
     * @param entityType The type of entity to pull (e.g., "transaction", "account")
     * @param lastSyncTimestamp The timestamp of the last successful sync (null for initial sync)
     * @return List of operations from the remote backend
     */
    suspend fun pullChanges(
        entityType: String,
        lastSyncTimestamp: Long? = null
    ): List<SyncOperation>

    /**
     * Subscribe to real-time changes from the remote backend
     *
     * @param entityType The type of entity to listen for
     * @param onChanges Callback invoked when changes are received
     * @return A handle to cancel the subscription
     */
    suspend fun subscribeToChanges(
        entityType: String,
        onChanges: (List<SyncOperation>) -> Unit
    ): SyncSubscription

    /**
     * Check if the remote service is available
     *
     * @return true if the service is reachable, false otherwise
     */
    suspend fun isAvailable(): Boolean
}

/**
 * Handle for a real-time sync subscription
 */
interface SyncSubscription {
    /**
     * Cancel this subscription
     */
    fun cancel()
}
