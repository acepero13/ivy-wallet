package com.ivy.data.auth.di

import com.google.firebase.auth.FirebaseAuth
import com.ivy.data.auth.AuthRepository
import com.ivy.data.auth.FirebaseAuthSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing authentication dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    /**
     * Binds FirebaseAuthSource implementation to AuthRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        firebaseAuthSource: FirebaseAuthSource
    ): AuthRepository

    companion object {
        /**
         * Provides Firebase Auth instance.
         */
        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth {
            return FirebaseAuth.getInstance()
        }
    }
}
