package com.ivy.data.auth

import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase implementation of authentication operations.
 * Uses Firebase Auth SDK to handle user authentication.
 */
@Singleton
class FirebaseAuthSource @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override fun getCurrentUser(): Flow<AuthResult> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val result = auth.currentUser?.toAuthUser()?.let { AuthResult.Success(it) }
                ?: AuthResult.NotAuthenticated
            trySend(result)
        }

        firebaseAuth.addAuthStateListener(authStateListener)

        awaitClose {
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
    }

    override suspend fun getCurrentUserOnce(): AuthResult {
        return firebaseAuth.currentUser?.toAuthUser()?.let { AuthResult.Success(it) }
            ?: AuthResult.NotAuthenticated
    }

    override suspend fun signInWithEmailAndPassword(
        email: String,
        password: String
    ): AuthResult {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            authResult.user?.toAuthUser()?.let { AuthResult.Success(it) }
                ?: AuthResult.Error("Sign in failed: User is null")
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Sign in failed with unknown error",
                exception = e
            )
        }
    }

    override suspend fun createUserWithEmailAndPassword(
        email: String,
        password: String,
        displayName: String?
    ): AuthResult {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user

            // Update display name if provided
            if (user != null && displayName != null) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                user.updateProfile(profileUpdates).await()
            }

            user?.toAuthUser()?.let { AuthResult.Success(it) }
                ?: AuthResult.Error("Account creation failed: User is null")
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Account creation failed with unknown error",
                exception = e
            )
        }
    }

    override suspend fun sendSignInLinkToEmail(email: String): AuthResult {
        return try {
            // Configure action code settings for email link sign-in
            // Note: Replace with your actual deep link configuration
            val actionCodeSettings = ActionCodeSettings.newBuilder()
                .setUrl("https://ivywallet.page.link/auth") // Replace with your dynamic link
                .setHandleCodeInApp(true)
                .setAndroidPackageName(
                    "com.ivy.wallet",
                    true, // Install if not available
                    null // Minimum version
                )
                .build()

            firebaseAuth.sendSignInLinkToEmail(email, actionCodeSettings).await()
            AuthResult.Success(
                AuthUser(
                    uid = "",
                    email = email,
                    displayName = null,
                    isEmailVerified = false
                )
            )
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Failed to send sign-in link",
                exception = e
            )
        }
    }

    override suspend fun signInWithEmailLink(email: String, emailLink: String): AuthResult {
        return try {
            val authResult = firebaseAuth.signInWithEmailLink(email, emailLink).await()
            authResult.user?.toAuthUser()?.let { AuthResult.Success(it) }
                ?: AuthResult.Error("Sign in with email link failed: User is null")
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Sign in with email link failed with unknown error",
                exception = e
            )
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            AuthResult.Success(
                AuthUser(
                    uid = "",
                    email = email,
                    displayName = null,
                    isEmailVerified = false
                )
            )
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Failed to send password reset email",
                exception = e
            )
        }
    }

    override suspend fun signOut(): AuthResult {
        return try {
            firebaseAuth.signOut()
            AuthResult.NotAuthenticated
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Sign out failed with unknown error",
                exception = e
            )
        }
    }

    override suspend fun deleteAccount(): AuthResult {
        return try {
            val user = firebaseAuth.currentUser
            if (user != null) {
                user.delete().await()
                AuthResult.NotAuthenticated
            } else {
                AuthResult.Error("Cannot delete account: No user is signed in")
            }
        } catch (e: Exception) {
            AuthResult.Error(
                message = e.message ?: "Account deletion failed with unknown error",
                exception = e
            )
        }
    }

    /**
     * Extension function to convert FirebaseUser to AuthUser domain model.
     */
    private fun FirebaseUser.toAuthUser(): AuthUser {
        return AuthUser(
            uid = this.uid,
            email = this.email,
            displayName = this.displayName,
            isEmailVerified = this.isEmailVerified
        )
    }
}
