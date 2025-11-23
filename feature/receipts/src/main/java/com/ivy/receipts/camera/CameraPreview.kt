package com.ivy.receipts.camera

import android.net.Uri
import android.widget.LinearLayout
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.guava.await
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    onImageCaptured: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Remember camera-related objects
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    // Set up camera when composable enters composition
    LaunchedEffect(lifecycleOwner) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()

        // Preview use case
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }

        // ImageCapture use case
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        // Select back camera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )

            Timber.d("Camera bound successfully")
        } catch (e: Exception) {
            Timber.e(e, "Camera binding failed")
        }
    }

    Box(modifier = modifier) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Capture button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    // Capture image
                    val capture = imageCapture ?: return@FloatingActionButton

                    val photoFile = File(
                        context.cacheDir,
                        "receipt_${System.currentTimeMillis()}.jpg"
                    )

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    capture.takePicture(
                        outputOptions,
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val savedUri = Uri.fromFile(photoFile)
                                Timber.d("Image captured: $savedUri")
                                onImageCaptured(savedUri)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Timber.e(exception, "Image capture failed")
                            }
                        }
                    )
                },
                containerColor = Color.White,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capture",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }

    // Cleanup executor when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }
}