package com.ivy.data.sync.di

import com.ivy.data.sync.SyncManager
import com.ivy.data.sync.SyncQueue
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing sync layer dependencies.
 *
 * This module is responsible for:
 * - Providing SyncQueue singleton
 * - Providing SyncManager singleton
 *
 * Note: RemoteService implementation will be provided in a future PR
 * when Firebase/backend integration is added.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideSyncQueue(): SyncQueue {
        return SyncQueue()
    }

    @Provides
    @Singleton
    fun provideSyncManager(
        syncQueue: SyncQueue,
        dispatchersProvider: com.ivy.base.threading.DispatchersProvider
    ): SyncManager {
        return SyncManager(
            syncQueue = syncQueue,
            dispatchersProvider = dispatchersProvider
        )
    }
}
