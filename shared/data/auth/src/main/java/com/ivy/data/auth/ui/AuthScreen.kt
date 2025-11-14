package com.ivy.data.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Authentication screen that provides sign-in and sign-up functionality.
 *
 * @param onAuthSuccess Callback invoked when authentication succeeds
 * @param viewModel ViewModel for managing auth state
 */
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    viewModel.onAuthSuccess = onAuthSuccess
    val state = viewModel.uiState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title
        Text(
            text = if (state.isSignUpMode) "Create Account" else "Sign In",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Email field
        OutlinedTextField(
            value = state.email,
            onValueChange = { viewModel.onEvent(AuthEvent.EmailChanged(it)) },
            label = { Text("Email") },
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = if (state.useEmailLink) ImeAction.Done else ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Display name field (only in sign-up mode with password)
        if (state.isSignUpMode && !state.useEmailLink) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = { viewModel.onEvent(AuthEvent.DisplayNameChanged(it)) },
                label = { Text("Display Name (Optional)") },
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Password field (only when not using email link)
        if (!state.useEmailLink) {
            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.onEvent(AuthEvent.PasswordChanged(it)) },
                label = { Text("Password") },
                enabled = !state.isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (state.isSignUpMode) {
                            viewModel.onEvent(AuthEvent.SignUp)
                        } else {
                            viewModel.onEvent(AuthEvent.SignIn)
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Error message
        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Email link sent message
        if (state.emailLinkSent) {
            Text(
                text = "Sign-in link sent to ${state.email}. Check your email!",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Main action button
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    when {
                        state.useEmailLink -> viewModel.onEvent(AuthEvent.SendEmailLink)
                        state.isSignUpMode -> viewModel.onEvent(AuthEvent.SignUp)
                        else -> viewModel.onEvent(AuthEvent.SignIn)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when {
                        state.useEmailLink -> "Send Sign-In Link"
                        state.isSignUpMode -> "Sign Up"
                        else -> "Sign In"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Toggle between sign in and sign up (only for password mode)
        if (!state.useEmailLink) {
            TextButton(
                onClick = { viewModel.onEvent(AuthEvent.ToggleSignUpMode) },
                enabled = !state.isLoading
            ) {
                Text(
                    text = if (state.isSignUpMode) {
                        "Already have an account? Sign In"
                    } else {
                        "Don't have an account? Sign Up"
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Toggle email link mode
        TextButton(
            onClick = { viewModel.onEvent(AuthEvent.ToggleEmailLinkMode) },
            enabled = !state.isLoading
        ) {
            Text(
                text = if (state.useEmailLink) {
                    "Use password instead"
                } else {
                    "Use email link (passwordless)"
                }
            )
        }
    }
}
