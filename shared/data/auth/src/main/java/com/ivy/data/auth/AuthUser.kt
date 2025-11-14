package com.ivy.data.auth

/**
 * Represents an authenticated user in the system.
 *
 * @property uid Unique user identifier from the authentication provider
 * @property email User's email address
 * @property displayName Optional display name
 * @property isEmailVerified Whether the user's email has been verified
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean
)
