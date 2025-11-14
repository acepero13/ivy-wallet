package com.ivy.wallet.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.auth.AuthRepository
import com.ivy.data.auth.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the authentication screen.
 * Handles authentication logic and state management.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    var state by mutableStateOf(AuthState())
        private set

    var onAuthSuccess: (() -> Unit)? = null

    @Composable
    fun uiState() = state

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.EmailChanged -> {
                state = state.copy(email = event.email, errorMessage = null)
            }

            is AuthEvent.PasswordChanged -> {
                state = state.copy(password = event.password, errorMessage = null)
            }

            is AuthEvent.DisplayNameChanged -> {
                state = state.copy(displayName = event.displayName, errorMessage = null)
            }

            AuthEvent.ToggleSignUpMode -> {
                state = state.copy(
                    isSignUpMode = !state.isSignUpMode,
                    errorMessage = null
                )
            }

            AuthEvent.ToggleEmailLinkMode -> {
                state = state.copy(
                    useEmailLink = !state.useEmailLink,
                    errorMessage = null,
                    emailLinkSent = false
                )
            }

            AuthEvent.SignIn -> {
                signIn()
            }

            AuthEvent.SignUp -> {
                signUp()
            }

            AuthEvent.SendEmailLink -> {
                sendEmailLink()
            }

            AuthEvent.DismissError -> {
                state = state.copy(errorMessage = null)
            }
        }
    }

    private fun signIn() {
        if (!validateEmail()) return
        if (!state.useEmailLink && !validatePassword()) return

        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null)

            val result = authRepository.signInWithEmailAndPassword(
                email = state.email.trim(),
                password = state.password
            )

            handleAuthResult(result)
        }
    }

    private fun signUp() {
        if (!validateEmail()) return
        if (!validatePassword()) return

        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null)

            val result = authRepository.createUserWithEmailAndPassword(
                email = state.email.trim(),
                password = state.password,
                displayName = state.displayName.takeIf { it.isNotBlank() }
            )

            handleAuthResult(result)
        }
    }

    private fun sendEmailLink() {
        if (!validateEmail()) return

        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null)

            val result = authRepository.sendSignInLinkToEmail(state.email.trim())

            state = state.copy(isLoading = false)

            when (result) {
                is AuthResult.Success -> {
                    state = state.copy(emailLinkSent = true)
                }

                is AuthResult.Error -> {
                    state = state.copy(errorMessage = result.message)
                }

                AuthResult.NotAuthenticated -> {
                    // Should not happen for send email link
                }
            }
        }
    }

    private fun handleAuthResult(result: AuthResult) {
        state = state.copy(isLoading = false)

        when (result) {
            is AuthResult.Success -> {
                onAuthSuccess?.invoke()
            }

            is AuthResult.Error -> {
                state = state.copy(errorMessage = result.message)
            }

            AuthResult.NotAuthenticated -> {
                state = state.copy(errorMessage = "Authentication failed")
            }
        }
    }

    private fun validateEmail(): Boolean {
        val email = state.email.trim()
        if (email.isBlank()) {
            state = state.copy(errorMessage = "Email is required")
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            state = state.copy(errorMessage = "Invalid email address")
            return false
        }
        return true
    }

    private fun validatePassword(): Boolean {
        if (state.password.isBlank()) {
            state = state.copy(errorMessage = "Password is required")
            return false
        }
        if (state.password.length < 6) {
            state = state.copy(errorMessage = "Password must be at least 6 characters")
            return false
        }
        return true
    }
}
