package com.ivy.receipts.ocr

import android.content.Context
import android.net.Uri
import arrow.core.Either
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ivy.receipts.parser.ReceiptParseable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: ReceiptParseable
) :
    OcrEngine {
    // ML Kit text recognizer (lazy initialization)
    // ML Kit text recognizer (lazy initialization)
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }


    override val engineType = OcrEngineType.ML_KIT

    override suspend fun recognizeText(imageUri: Uri): Either<String, OcrResult> {

        return try {
            val startTime = System.currentTimeMillis()
            val inputImage = InputImage.fromFilePath(context, imageUri)
            Timber.d("Created InputImage from $imageUri, size: ${inputImage.width}x${inputImage.height}")
            // Process image with ML Kit
            val visionText = recognizer.process(inputImage).await()

            // Extract blocks and calculate confidence
            val blocks = extractBlocksFrom(visionText)
            val confidence = calculateConfidence(visionText)
            val processingTime = System.currentTimeMillis() - startTime
            val result = OcrResult(
                fullText = visionText.text,
                blocks = blocks,
                confidence = confidence,
                processingTimeMs = processingTime
            )


            Either.Right(result)
        } catch (e: Exception) {
            Timber.e(e, "ML Kit OCR failed for URI: $imageUri")
            Either.Left("OCR failed: ${e.message}")
        }
    }

    private fun calculateConfidence(visionText: Text): Float {
        // Calculate average confidence from all lines
        val allLines = visionText.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) return 0f

        val confidences = allLines.mapNotNull { it.confidence }
        return if (confidences.isNotEmpty()) {
            confidences.average().toFloat()
        } else {
            0.5f // Default confidence if not available
        }
    }

    private fun extractBlocksFrom(visionText: Text): List<TextBlock> {
        return visionText.textBlocks.map { block ->
            TextBlock(
                text = block.text,
                boundingBox = block.boundingBox,
                lines = block.lines.map { line ->
                    TextLine(
                        text = line.text,
                        confidence = line.confidence
                    )
                }
            )
        }

    }

    fun release() {
        recognizer.close()
    }
}


