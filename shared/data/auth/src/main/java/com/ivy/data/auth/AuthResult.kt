package com.ivy.data.auth

/**
 * Represents the result of an authentication operation.
 */
sealed class AuthResult {
    /**
     * Authentication operation succeeded.
     *
     * @property user The authenticated user
     */
    data class Success(val user: AuthUser) : AuthResult()

    /**
     * Authentication operation failed.
     *
     * @property message Error message describing what went wrong
     * @property exception Optional exception for debugging
     */
    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : AuthResult()

    /**
     * User is not authenticated (signed out).
     */
    data object NotAuthenticated : AuthResult()
}
