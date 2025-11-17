package com.ivy.data.auth

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for authentication operations.
 * Provides an abstraction over the authentication provider (Firebase Auth).
 */
interface AuthRepository {
    /**
     * Get the current authenticated user as a Flow.
     * Emits updates when auth state changes (sign in, sign out, etc.).
     *
     * @return Flow of AuthResult representing current auth state
     */
    fun getCurrentUser(): Flow<AuthResult>

    /**
     * Get the current authenticated user synchronously.
     *
     * @return AuthResult representing current auth state
     */
    suspend fun getCurrentUserOnce(): AuthResult

    /**
     * Sign in with email and password.
     *
     * @param email User's email address
     * @param password User's password
     * @return AuthResult indicating success or failure
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult

    /**
     * Create a new user account with email and password.
     *
     * @param email User's email address
     * @param password User's password
     * @param displayName Optional display name for the user
     * @return AuthResult indicating success or failure
     */
    suspend fun createUserWithEmailAndPassword(
        email: String,
        password: String,
        displayName: String? = null
    ): AuthResult

    /**
     * Send a sign-in link to the user's email.
     * User can click the link to sign in without a password.
     *
     * @param email User's email address
     * @return AuthResult indicating success or failure of sending the email
     */
    suspend fun sendSignInLinkToEmail(email: String): AuthResult

    /**
     * Sign in with an email link received from sendSignInLinkToEmail.
     *
     * @param email User's email address
     * @param emailLink The sign-in link received via email
     * @return AuthResult indicating success or failure
     */
    suspend fun signInWithEmailLink(email: String, emailLink: String): AuthResult

    /**
     * Send a password reset email to the user.
     *
     * @param email User's email address
     * @return AuthResult indicating success or failure of sending the email
     */
    suspend fun sendPasswordResetEmail(email: String): AuthResult

    /**
     * Sign out the current user.
     *
     * @return AuthResult indicating success or failure
     */
    suspend fun signOut(): AuthResult

    /**
     * Delete the current user's account.
     *
     * @return AuthResult indicating success or failure
     */
    suspend fun deleteAccount(): AuthResult

    /**
     * Sign in with Google using an ID token.
     *
     * @param idToken The Google ID token obtained from Google Sign-In
     * @return AuthResult indicating success or failure
     */
    suspend fun signInWithGoogle(idToken: String): AuthResult
}
