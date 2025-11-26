package com.ivy.receipts

import android.net.Uri
import android.util.Log
import com.ivy.base.model.TransactionType
import com.ivy.data.repository.CategoryRepository
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.Navigation
import com.ivy.receipts.ocr.MlKitOcrEngine
import com.ivy.receipts.ocr.OcrReceipt
import com.ivy.receipts.parser.ReceiptParseable
import timber.log.Timber
import javax.inject.Inject


class OcrResultHandler @Inject constructor(
    private val ocrEngine: MlKitOcrEngine,
    private val parser: ReceiptParseable,
    private val navigation: Navigation,
    private val categoryRepository: CategoryRepository
) {
    /**
     * Process receipt image and navigate to EditTransactionScreen with pre-filled data.
     *
     * @param imageUri URI of the captured/selected receipt image
     */
    suspend fun processReceiptAndCreateTransaction(imageUri: Uri) {
        // Step 1: Perform OCR
        val ocResult = ocrEngine.recognizeText(imageUri)

        ocResult.fold(
            ifLeft = { error ->
                // Handle OCR error
                // TODO: Show error to user
                Timber.tag("OcrResultHandler").e("OCR failed: $error")
            },
            ifRight = { result ->
                Timber.tag("OcrResultHandler").d("Result from OCR: $result")
                val receipt = parser.parse(result.blocks)
                Timber.tag("OcrResultHandler").d("Parsed receipt: $receipt")
                navigateToTransactionWithOcrData(receipt)
            }
        )
    }

    /**
     * Generate title combining category and merchant name.
     * Format: "Category - Merchant" (e.g., "Groceries - Edeka", "Shopping - Zara")
     *
     * @param receipt Parsed receipt data
     * @return Generated title string
     */
    private suspend fun generateTitle(receipt: OcrReceipt): String {
        val categoryName = receipt.categoryId?.let {
            categoryRepository.findById(it)?.name?.value
        }
        val merchantName = receipt.merchantName

        return when {
            categoryName != null && merchantName != null -> "$categoryName - $merchantName"
            categoryName != null -> categoryName
            merchantName != null -> merchantName
            else -> "Receipt scan"
        }
    }

    private suspend fun navigateToTransactionWithOcrData(receipt: OcrReceipt) {
        val title = generateTitle(receipt)

        navigation.navigateTo(
            EditTransactionScreen(
                initialTransactionId = null, // New transaction
                type = TransactionType.EXPENSE, // Receipts are typically expenses
                categoryId = receipt.categoryId?.value, // Auto-detected category
                ocrAmount = receipt.total,
                ocrDate = receipt.date,
                ocrDescription = "Scanned from receipt",
                ocrTitle = title // Generated from category and merchant
            )
        )
    }
}