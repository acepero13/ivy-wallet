package com.ivy.receipts.parser

import com.ivy.data.model.Category
import com.ivy.receipts.ocr.OcrReceipt
import com.ivy.receipts.ocr.TextBlock
import java.time.Instant

interface ReceiptParseable {
    fun parse(blocks: List<TextBlock>): OcrReceipt
}

