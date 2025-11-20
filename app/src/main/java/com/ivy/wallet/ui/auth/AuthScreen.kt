package com.ivy.wallet.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.ivy.ui.R

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
    val context = LocalContext.current

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("AuthScreen", "Google Sign-In result: resultCode=${result.resultCode}, RESULT_OK=${Activity.RESULT_OK}")

        // Always try to get the account from the intent, even if cancelled
        // This will give us more detailed error information
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            android.util.Log.d("AuthScreen", "Google account: ${account?.email}, idToken=${account?.idToken?.take(20)}...")
            viewModel.onEvent(AuthEvent.GoogleSignInResult(account?.idToken))
        } catch (e: ApiException) {
            android.util.Log.e("AuthScreen", "Google Sign-In ApiException: statusCode=${e.statusCode}, message=${e.message}", e)
            android.util.Log.e("AuthScreen", "Common error codes: 10=DEVELOPER_ERROR (SHA-1/package mismatch), 12501=SIGN_IN_CANCELLED, 7=NETWORK_ERROR")

            when (e.statusCode) {
                10 -> {
                    android.util.Log.e("AuthScreen", "DEVELOPER_ERROR: This usually means:")
                    android.util.Log.e("AuthScreen", "1. SHA-1 fingerprint not registered in Firebase/Google Cloud")
                    android.util.Log.e("AuthScreen", "2. Wrong package name")
                    android.util.Log.e("AuthScreen", "3. OAuth client not properly configured")
                }
                12501 -> android.util.Log.e("AuthScreen", "User cancelled sign-in")
                7 -> android.util.Log.e("AuthScreen", "Network error")
            }

            viewModel.onEvent(AuthEvent.GoogleSignInResult(null))
        }
    }

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

        // Google Sign-In Button
        OutlinedButton(
            onClick = {
                android.util.Log.d("AuthScreen", "Google Sign-In button clicked")
                try {
                    // Get default web client ID from google-services.json
                    // This should now be auto-generated properly with the new Firebase app
                    val webClientId = try {
                        val resourceId = context.resources.getIdentifier(
                            "default_web_client_id",
                            "string",
                            context.packageName
                        )
                        if (resourceId != 0) {
                            context.getString(resourceId)
                        } else {
                            // Fallback to web client ID from google-services.json
                            "273203605390-23696i8v2hpe8196iji2k2ejir57iup6.apps.googleusercontent.com"
                        }
                    } catch (e: Exception) {
                        "273203605390-23696i8v2hpe8196iji2k2ejir57iup6.apps.googleusercontent.com"
                    }

                    android.util.Log.d("AuthScreen", "Web client ID: ${webClientId.take(20)}...")
                    android.util.Log.d("AuthScreen", "Creating GoogleSignInOptions and launching sign-in")

                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(webClientId)
                        .requestEmail()
                        .build()

                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                } catch (e: Exception) {
                    android.util.Log.e("AuthScreen", "Exception during Google Sign-In", e)
                    viewModel.onEvent(AuthEvent.GoogleSignInResult(null))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = "Google logo",
                    modifier = Modifier.size(20.dp),
                    tint = androidx.compose.ui.graphics.Color.Unspecified
                )
                Spacer(modifier = Modifier.padding(8.dp))
                Text("Sign in with Google")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Divider with "OR"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "  OR  ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

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
