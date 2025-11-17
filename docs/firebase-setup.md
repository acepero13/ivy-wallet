# Firebase Setup Guide for Ivy Wallet

This guide explains how to configure Firebase for the Ivy Wallet project to enable authentication and sync features.

## Overview

Ivy Wallet uses Firebase for:
- **Firebase Authentication**: User sign-in with email/password and email link (passwordless)
- **Cloud Firestore**: Real-time data sync for shared accounts (future PRs)
- **Firebase Crashlytics**: Crash reporting (already configured)

## Prerequisites

- Android Studio
- Google Account
- Basic understanding of Firebase console

## Step 1: Create a Firebase Project

1. Go to the [Firebase Console](https://console.firebase.google.com/)
2. Click **Add Project** (or use an existing project)
3. Enter project name: `ivy-wallet-dev` (or your preferred name)
4. Optionally enable Google Analytics (recommended for production)
5. Click **Create Project**

## Step 2: Add Android App to Firebase Project

1. In the Firebase Console, select your project
2. Click the **Android icon** to add an Android app
3. Fill in the app details:
   - **Android package name**:
     - For debug builds: `com.ivy.wallet.debug`
     - For release builds: `com.ivy.wallet`
   - **App nickname** (optional): `Ivy Wallet Debug` or `Ivy Wallet`
   - **Debug signing certificate SHA-1**:
     - For debug, get from Android Studio or run:
       ```bash
       keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
       ```
4. Click **Register app**

## Step 3: Download google-services.json

1. Download the `google-services.json` file from Firebase Console
2. Place it in the `app/` directory of your project:
   ```
   ivy-wallet/
   └── app/
       └── google-services.json
   ```
3. **Important**: Add `google-services.json` to `.gitignore` to avoid committing credentials:
   ```
   # In .gitignore
   google-services.json
   ```

## Step 4: Enable Firebase Authentication

1. In Firebase Console, go to **Build** > **Authentication**
2. Click **Get Started**
3. Select the **Sign-in method** tab
4. Enable the following sign-in providers:

### Email/Password Authentication

1. Click **Email/Password**
2. Toggle **Enable**
3. (Optional) Toggle **Email link (passwordless sign-in)** if you want to support this feature
4. Click **Save**

### Email Link Configuration (Optional)

If enabling email link sign-in:

1. Go to **Authentication** > **Templates**
2. Select **Email address sign-in**
3. Customize the email template as needed
4. Set up Firebase Dynamic Links:
   - Go to **Engage** > **Dynamic Links**
   - Click **Get Started**
   - Add a URL prefix (e.g., `ivywallet.page.link`)
   - Update the URL in `FirebaseAuthSource.kt`:
     ```kotlin
     .setUrl("https://your-dynamic-link.page.link/auth")
     ```

### Google OAuth Sign-In

To enable Google Sign-In (recommended for production):

1. **Enable Google Sign-In Provider**:
   - In Firebase Console, go to **Authentication** > **Sign-in method**
   - Click **Google**
   - Toggle **Enable**
   - Enter project support email
   - Click **Save**

2. **Configure OAuth Consent Screen** (Google Cloud Console):
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Select your Firebase project
   - Navigate to **APIs & Services** > **OAuth consent screen**
   - Configure the consent screen:
     - **User Type**: External (for public apps) or Internal (for G Suite orgs)
     - **App name**: Ivy Wallet
     - **User support email**: Your email
     - **Developer contact email**: Your email
   - Add scopes (optional):
     - `email`
     - `profile`
     - `openid`
   - Click **Save and Continue**

3. **Get OAuth 2.0 Client IDs**:
   - In Firebase Console, go to **Project Settings** > **General**
   - Under **Your apps**, find your Android app
   - Click **Add fingerprint** and add your SHA-1 certificates:

     **Debug SHA-1:**
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```

     **Release SHA-1** (for production):
     ```bash
     keytool -list -v -keystore /path/to/your/release.keystore -alias your-alias
     ```

4. **Download Updated google-services.json**:
   - After adding SHA-1 fingerprints, download the updated `google-services.json`
   - Replace the file in your `app/` directory

5. **Add Google Sign-In Dependencies** (if not already present):
   ```kotlin
   // In shared/data/auth/build.gradle.kts
   dependencies {
       implementation("com.google.android.gms:play-services-auth:20.7.0")
       implementation("com.google.firebase:firebase-auth-ktx")
   }
   ```

6. **Update AuthRepository Implementation**:
   The `FirebaseAuthSource.kt` should already support Google Sign-In. If implementing from scratch:
   ```kotlin
   suspend fun signInWithGoogle(idToken: String): AuthResult {
       val credential = GoogleAuthProvider.getCredential(idToken, null)
       return try {
           val result = auth.signInWithCredential(credential).await()
           // Return success with user
       } catch (e: Exception) {
           // Handle error
       }
   }
   ```

7. **Test Google Sign-In**:
   - Run the app on a physical device or emulator with Google Play Services
   - Navigate to Settings > Sign In (or Auth screen)
   - Click "Sign in with Google" button
   - Select a Google account
   - Verify successful authentication in Firebase Console > Authentication > Users

**Important Notes for Google Sign-In:**
- Requires Google Play Services on the device
- Must add SHA-1 fingerprints for each build variant (debug, release)
- For production, publish your app on Google Play or add authorized domains
- Test thoroughly with different Google accounts

## Step 5: Enable Cloud Firestore (for future PRs)

1. In Firebase Console, go to **Build** > **Firestore Database**
2. Click **Create database**
3. Select **Start in test mode** (for development)
   - **Note**: Update security rules for production (covered in PR 11)
4. Choose a Firestore location (e.g., `us-central`)
5. Click **Enable**

## Step 6: Configure Firestore Security Rules (Initial)

While in test mode for development, you'll update these in PR 11. For now, use these basic rules:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Allow authenticated users to read/write their own data
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Temporary test mode - RESTRICT BEFORE PRODUCTION
    match /{document=**} {
      allow read, write: if request.time < timestamp.date(2025, 12, 31);
    }
  }
}
```

## Step 7: Test Firebase Configuration

### Build and Run

1. Sync Gradle files in Android Studio
2. Build the project:
   ```bash
   ./gradlew :shared:data:auth:assemble
   ```
3. Run the app on a device or emulator
4. **Access the Authentication Screen**:

   **Option 1 - From Settings (Recommended):**
   - Open Ivy Wallet
   - Navigate to **Settings** (bottom navigation or drawer menu)
   - Scroll down to find **"Sign In"** or **"Authentication"** button
   - Tap to open the Auth screen

   **Option 2 - Direct Navigation (for developers):**
   - The auth screen is registered at route: `AuthScreen`
   - You can navigate programmatically:
     ```kotlin
     navigation.navigateTo(AuthScreen)
     ```

   **Option 3 - Deep Link (future):**
   - Will support deep links like: `ivywallet://auth`

5. Try signing up with a test email/password:
   - Enter your email
   - Enter a password (min 6 characters recommended)
   - Click "Sign Up"
   - Or toggle to "Use email link (passwordless)" for passwordless auth

### Verify in Firebase Console

1. Go to **Authentication** > **Users**
2. You should see newly created test users appear here
3. Verify user details (email, UID, creation timestamp)
4. Check **Firestore Database** for any data (after sync implementation is complete)

## Step 8: Set Up Multiple Environments (Optional)

For production vs. development environments:

### Create Separate Firebase Projects

1. Create `ivy-wallet-dev` for development
2. Create `ivy-wallet-prod` for production

### Use Build Variants

1. Place dev `google-services.json` in `app/src/debug/`
2. Place prod `google-services.json` in `app/src/release/`
3. Android will automatically pick the correct config per build variant

## Environment Variables (Optional)

For CI/CD or team development, you can use environment variables:

```bash
# Example: Store Firebase config in environment
export FIREBASE_PROJECT_ID="your-project-id"
export FIREBASE_API_KEY="your-api-key"
```

Then reference in `build.gradle.kts`:
```kotlin
android {
    defaultConfig {
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${System.getenv("FIREBASE_PROJECT_ID")}\"")
    }
}
```

## Troubleshooting

### Issue: "google-services.json is missing"

**Solution**: Make sure `google-services.json` is in the `app/` directory and you've synced Gradle.

### Issue: "FirebaseApp initialization unsuccessful"

**Solution**:
1. Check that package name in `google-services.json` matches your `applicationId`
2. Verify the file is in the correct location
3. Clean and rebuild: `./gradlew clean build`

### Issue: "Authentication failed" errors

**Solution**:
1. Verify Email/Password provider is enabled in Firebase Console
2. Check network connectivity
3. Review Firebase Console logs for specific error messages

### Issue: "SHA-1 fingerprint mismatch"

**Solution**:
1. Add the correct SHA-1 to Firebase Console under Project Settings > Your Apps
2. Download the updated `google-services.json`
3. Replace the old file and rebuild

## Testing with Firebase Emulator (Advanced)

For local development without using Firebase cloud services:

1. Install Firebase CLI:
   ```bash
   npm install -g firebase-tools
   ```

2. Initialize Firebase Emulator:
   ```bash
   firebase init emulators
   ```

3. Start emulators:
   ```bash
   firebase emulators:start
   ```

4. Connect your app to emulators (add to app initialization):
   ```kotlin
   if (BuildConfig.DEBUG) {
       Firebase.auth.useEmulator("10.0.2.2", 9099)
       Firebase.firestore.useEmulator("10.0.2.2", 8080)
   }
   ```

## Security Best Practices

1. **Never commit** `google-services.json` to version control
2. Use **Firestore security rules** to restrict data access
3. Enable **App Check** in production to prevent abuse
4. Set up **authenticated domains** in Firebase Console
5. Review **Firebase Usage** regularly to detect anomalies

## Next Steps

After completing this setup:
- **PR 3**: Implement Firestore data models for shared accounts
- **PR 6**: Integrate Firestore sync service
- **PR 8**: Implement invite flow with Firebase Dynamic Links
- **PR 11**: Add comprehensive Firestore security rules

## Additional Resources

- [Firebase Authentication Docs](https://firebase.google.com/docs/auth)
- [Cloud Firestore Docs](https://firebase.google.com/docs/firestore)
- [Firebase Android Setup Guide](https://firebase.google.com/docs/android/setup)
- [Security Rules Guide](https://firebase.google.com/docs/firestore/security/get-started)

## Support

If you encounter issues:
1. Check the [Firebase Status Dashboard](https://status.firebase.google.com/)
2. Review [StackOverflow Firebase tag](https://stackoverflow.com/questions/tagged/firebase)
3. Open an issue in the project repository with logs and error messages
