package com.ivy.receipts.parser

import com.ivy.receipts.category.MerchantCategoryPatterns
import com.ivy.receipts.category.locales.GermanMerchantPatterns
import com.ivy.receipts.ocr.TextBlock
import java.util.Locale

/**
 * Extracts merchant/store name from receipt.
 */
class MerchantExtractor(
    private val patterns: MerchantCategoryPatterns = GermanMerchantPatterns()
) {
    /**
     * Extract merchant name from top of receipt.
     * Looks at first 5 text blocks and finds known merchant names.
     *
     * @param blocks OCR text blocks from receipt
     * @return Merchant name in title case, or null if not found
     */
    fun extractMerchantName(blocks: List<TextBlock>): String? {
        // Extract text from top of receipt (first 5 blocks)
        val merchantText = blocks.joinToString(" ") { it.text }.uppercase(Locale.getDefault())

        // Find first matching merchant
        for ((merchantName, _) in patterns.merchantMappings) {
            if (merchantText.contains(merchantName)) {
                return merchantName.toTitleCase()
            }
        }

        // If no known merchant found, try to extract from first block
        return blocks.firstOrNull()?.text
            ?.trim()
            ?.takeIf { it.length in 2..30 } // Reasonable merchant name length
            ?.toTitleCase()
    }

    /**
     * Convert uppercase text to title case.
     * "ALDI SÜD" → "Aldi Süd"
     * "DM-DROGERIE MARKT" → "Dm-Drogerie Markt"
     */
    private fun String.toTitleCase(): String {
        return this.split(" ", "-").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }.replace(" - ", "-") // Fix hyphenated words
    }
}
