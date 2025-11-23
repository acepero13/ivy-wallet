package com.ivy.receipts

import androidx.compose.runtime.Composable
import com.ivy.navigation.ReceiptCameraScreen
import com.ivy.navigation.navigation
import com.ivy.receipts.camera.CameraScreen


/**
 * Composable function for receipt camera screen.
 *
 * This is called from IvyNavGraph when navigating to ReceiptCameraScreen.
 */
@Composable
fun ReceiptCameraScreen(screen: ReceiptCameraScreen) {
    val nav = navigation()

    CameraScreen(
        onImageCaptured = { uri ->
            // TODO: Process image with OCR (PR 3)
            // For now, just log and go back
            android.util.Log.d("ReceiptCameraScreen", "Image captured: $uri")
            nav.back()
        },
        onBack = {
            nav.back()
        }
    )
}