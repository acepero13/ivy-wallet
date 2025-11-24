package com.ivy.receipts.parser

import com.ivy.receipts.ocr.OcrReceipt
import com.ivy.receipts.ocr.TextBlock

interface ReceiptParseable {
    suspend fun parse(blocks: List<TextBlock>): OcrReceipt
}

