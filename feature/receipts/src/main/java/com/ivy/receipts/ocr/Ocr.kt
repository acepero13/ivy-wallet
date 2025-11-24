package com.ivy.receipts.ocr

import android.graphics.Rect
import android.net.Uri
import arrow.core.Either
import com.ivy.data.model.CategoryId
import java.math.BigDecimal
import java.time.Instant

interface OcrEngine {
    /**
     * Recognize text from an image URI.
     *
     * @param imageUri URI of the image file
     * @return Either error message or OCR result
     */
    suspend fun recognizeText(imageUri: Uri): Either<String, OcrResult>


    /**
     * Type of OCR engine.
     */
    val engineType: OcrEngineType
}

/**
 * Available OCR engine types.
 */
enum class OcrEngineType {
    ML_KIT,      // Google ML Kit (bundled, ~10MB)
    QWEN_2_VL    // Qwen2-VL (optional download, ~1.5GB)
}

/**
 * Result of OCR operation.
 *
 * Learning point: Structured data with confidence scores and timing.
 */

data class OcrResult(
    val fullText: String,           // Complete extracted text
    val blocks: List<TextBlock>,    // Structured blocks
    val confidence: Float,          // Overall confidence (0.0 to 1.0)
    val processingTimeMs: Long      // Processing duration
) {
    companion object
}

data class OcrReceipt(
    val total: BigDecimal,
    val currency: String,
    val date: Instant,
    val categoryId: CategoryId?,
    val merchantName: String? = null
)


/**
 * Block of text with position information.
 */
data class TextBlock(
    val text: String,
    val boundingBox: Rect?,
    val lines: List<TextLine>
)

/**
 * Single line of text with confidence.
 */
data class TextLine(
    val text: String,
    val confidence: Float?
)


