package com.ivy.receipts

import android.net.Uri
import android.util.Log
import com.ivy.base.model.TransactionType
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.Navigation
import com.ivy.receipts.ocr.MlKitOcrEngine
import com.ivy.receipts.ocr.OcrReceipt
import com.ivy.receipts.parser.ReceiptParseable
import javax.inject.Inject


class OcrResultHandler @Inject constructor(
    private val ocrEngine: MlKitOcrEngine,
    private val parser: ReceiptParseable,
    private val navigation: Navigation
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
                Log.e("OcrResultHandler", "OCR failed: $error")
            },
            ifRight = { result ->
                Log.d("OcrResultHandler", "Result from OCR: $result")
                val receipt = parser.parse(result.blocks)
                Log.d("OcrResultHandler", "Parsed receipt: $receipt")
                navigateToTransactionWithOcrData(receipt)
            }
        )
    }

    private fun navigateToTransactionWithOcrData(receipt: OcrReceipt) {
        navigation.navigateTo(
            EditTransactionScreen(
                initialTransactionId = null, // New transaction
                type = TransactionType.EXPENSE, // Receipts are typically expenses
                ocrAmount = receipt.total,
                ocrDate = receipt.date, // TODO: Add category
                ocrDescription = "Receipt scan" // Or extract from receipt if available
            )
        )
    }
}