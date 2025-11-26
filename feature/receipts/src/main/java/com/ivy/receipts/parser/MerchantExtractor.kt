package com.ivy.receipts.parser

import android.util.Log
import com.ivy.receipts.category.MerchantCategoryPatterns
import com.ivy.receipts.category.locales.GermanMerchantPatterns
import com.ivy.receipts.ocr.TextBlock
import timber.log.Timber
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
        if (filteredBlocks.isEmpty()) {
            Timber.tag(TAG).d("No blocks with bounding boxes for merchant extraction")
            return null
        }

        // Detect orientation
        val allBounds = filteredBlocks.mapNotNull { it.boundingBox }
        val imageWidth = allBounds.maxOfOrNull { it.right }?.minus(allBounds.minOfOrNull { it.left } ?: 0) ?: 0
        val imageHeight = allBounds.maxOfOrNull { it.bottom }?.minus(allBounds.minOfOrNull { it.top } ?: 0) ?: 0

        if (imageHeight == 0) {
            Timber.tag(TAG).w("Invalid image dimensions for merchant extraction")
            return null
        }

        val isLandscape = imageWidth.toDouble() / imageHeight > ParserConstants.LANDSCAPE_RATIO_THRESHOLD

        // Sort blocks by appropriate coordinate based on orientation
        val sortedBlocks = if (isLandscape) {
            // For landscape: leftmost blocks are at the "top"
            filteredBlocks.sortedBy { it.boundingBox?.left ?: 0 }
        } else {
            // For portrait: topmost blocks are at the top
            filteredBlocks.sortedBy { it.boundingBox?.top ?: 0 }
        }

        // Extract text from top of receipt
        // For landscape, use more blocks (10) as text might be more scattered
        val numBlocksToCheck = if (isLandscape) 10 else 5
        val topBlocks = sortedBlocks.take(numBlocksToCheck)
        val merchantText = topBlocks.joinToString(" ") { it.text }.uppercase(Locale.getDefault())

        Timber.tag(TAG).d("Orientation: ${if (isLandscape) "LANDSCAPE" else "PORTRAIT"}")
        Timber.tag(TAG).d("Top $numBlocksToCheck blocks text: ${topBlocks.map { it.text }}")
        Timber.tag(TAG).d("Merchant text for matching: '$merchantText'")

        // Find first matching merchant (prioritize by order of appearance in patterns)
        for ((merchantName, _) in patterns.merchantMappings) {
            // Compare in uppercase for case-insensitive matching
            if (merchantText.contains(merchantName.uppercase(Locale.getDefault()))) {
                Timber.tag(TAG).i("Matched merchant: $merchantName")
                return merchantName.toTitleCase()
            }
        }

        Timber.tag(TAG).d("No merchant match found in patterns")

        // If no known merchant found, try to extract from first block
        val fallbackName = topBlocks.firstOrNull()?.text
            ?.trim()
            ?.takeIf { it.length in 2..30 } // Reasonable merchant name length
            ?.toTitleCase()

        if (fallbackName != null) {
            Timber.tag(TAG).d("Using fallback merchant name: $fallbackName")
        }

        return fallbackName
    }

    companion object {
        private const val TAG = "MerchantExtractor"
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
