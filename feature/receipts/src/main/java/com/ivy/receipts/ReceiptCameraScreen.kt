package com.ivy.receipts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.ivy.navigation.ReceiptCameraScreen
import com.ivy.navigation.navigation
import com.ivy.receipts.camera.CameraScreen
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import timber.log.Timber


/**
 * Composable function for receipt camera screen.
 *
 * This is called from IvyNavGraph when navigating to ReceiptCameraScreen.
 */
@Composable
fun ReceiptCameraScreen(screen: ReceiptCameraScreen) {
    val nav = navigation()
    val context = LocalContext.current

    // Get OcrResultHandler from Hilt entry point
    val ocrResultHandler = EntryPointAccessors.fromApplication(
        context.applicationContext,
        OcrResultHandlerEntryPoint::class.java
    ).ocrResultHandler()

    val coroutineScope = rememberCoroutineScope()

    CameraScreen(
        onImageCaptured = { uri ->
            Timber.tag("ReceiptCameraScreen").d("Image captured: $uri")
            coroutineScope.launch {
                ocrResultHandler.processReceiptAndCreateTransaction(uri)
            }
        },
        onBack = {
            nav.back()
        }
    )
}