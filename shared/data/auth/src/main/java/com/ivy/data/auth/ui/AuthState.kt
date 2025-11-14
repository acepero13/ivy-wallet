package com.ivy.data.auth.ui

/**
 * Represents the authentication UI state.
 */
data class AuthState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSignUpMode: Boolean = false, // Toggle between sign in and sign up
    val useEmailLink: Boolean = false, // Toggle between password and email link
    val emailLinkSent: Boolean = false
)

/**
 * Events that can be triggered from the auth UI.
 */
sealed class AuthEvent {
    data class EmailChanged(val email: String) : AuthEvent()
    data class PasswordChanged(val password: String) : AuthEvent()
    data class DisplayNameChanged(val displayName: String) : AuthEvent()
    data object ToggleSignUpMode : AuthEvent()
    data object ToggleEmailLinkMode : AuthEvent()
    data object SignIn : AuthEvent()
    data object SignUp : AuthEvent()
    data object SendEmailLink : AuthEvent()
    data object DismissError : AuthEvent()
}
