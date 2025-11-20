package com.ivy.data.sync.di

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.ivy.data.sync.RemoteService
import com.ivy.data.sync.SyncManager
import com.ivy.data.sync.SyncQueue
import com.ivy.data.sync.impl.FirestoreRemoteService
import dagger.Binds
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
 * - Providing Firebase dependencies (Firestore, Auth)
 * - Binding RemoteService implementation
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
        remoteService: RemoteService,
        dispatchersProvider: com.ivy.base.threading.DispatchersProvider
    ): SyncManager {
        return SyncManager(
            syncQueue = syncQueue,
            remoteService = remoteService,
            dispatchersProvider = dispatchersProvider
        )
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()

        // Enable offline persistence
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()

        firestore.firestoreSettings = settings

        return firestore
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindsModule {

    @Binds
    @Singleton
    abstract fun bindRemoteService(
        impl: FirestoreRemoteService
    ): RemoteService
}
