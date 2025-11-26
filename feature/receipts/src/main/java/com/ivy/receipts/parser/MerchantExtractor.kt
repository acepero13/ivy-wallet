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
     * Handles both portrait and landscape orientations.
     *
     * @param blocks OCR text blocks from receipt
     * @return Merchant name in title case, or null if not found
     */
    fun extractMerchantName(blocks: List<TextBlock>): String? {
        val filteredBlocks = blocks.filter { it.boundingBox != null }
        if (filteredBlocks.isEmpty()) return null

        // Detect orientation
        val allBounds = filteredBlocks.mapNotNull { it.boundingBox }
        val imageWidth = allBounds.maxOf { it.right } - allBounds.minOf { it.left }
        val imageHeight = allBounds.maxOf { it.bottom } - allBounds.minOf { it.top }
        val isLandscape = imageWidth.toDouble() / imageHeight > 1.2

        // Sort blocks by appropriate coordinate based on orientation
        val sortedBlocks = if (isLandscape) {
            // For landscape: leftmost blocks are at the "top"
            filteredBlocks.sortedBy { it.boundingBox!!.left }
        } else {
            // For portrait: topmost blocks are at the top
            filteredBlocks.sortedBy { it.boundingBox!!.top }
        }

        // Extract text from top of receipt
        // For landscape, use more blocks (10) as text might be more scattered
        val numBlocksToCheck = if (isLandscape) 10 else 5
        val topBlocks = sortedBlocks.take(numBlocksToCheck)
        val merchantText = topBlocks.joinToString(" ") { it.text }.uppercase(Locale.getDefault())

        println("=== MERCHANT EXTRACTION ===")
        println("Orientation: ${if (isLandscape) "LANDSCAPE" else "PORTRAIT"}")
        println("Top $numBlocksToCheck blocks text: ${topBlocks.map { it.text }}")
        println("Merchant text for matching: '$merchantText'")

        // Find first matching merchant (prioritize by order of appearance in patterns)
        for ((merchantName, _) in patterns.merchantMappings) {
            if (merchantText.contains(merchantName)) {
                println("Matched merchant: $merchantName")
                return merchantName.toTitleCase()
            }
        }

        println("No merchant match found in patterns")

        // If no known merchant found, try to extract from first block
        return topBlocks.firstOrNull()?.text
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
