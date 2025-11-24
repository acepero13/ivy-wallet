package com.ivy.receipts.category

import com.ivy.data.model.CategoryId
import com.ivy.receipts.category.locales.EnglishMerchantPatterns
import com.ivy.receipts.category.locales.GermanMerchantPatterns
import com.ivy.receipts.ocr.TextBlock
import java.util.Locale

/**
 * Detects category based on merchant/store name recognition.
 *
 * Strategy:
 * 1. Extract merchant name from top of receipt (first few text blocks)
 * 2. Match against known merchant patterns
 * 3. Map merchant type to user's existing categories by name similarity
 *
 * Example:
 * - "ALDI", "REWE", "EDEKA" → "Groceries" or "Food"
 * - "DM", "ROSSMANN" → "Health" or "Personal Care"
 * - "SHELL", "ARAL" → "Transport" or "Gas"
 */
class MerchantCategoryDetector(
    private val patterns: MerchantCategoryPatterns = GermanMerchantPatterns()
) : CategoryDetector {

    override suspend fun detectCategory(
        blocks: List<TextBlock>,
        availableCategories: List<CategoryInfo>
    ): CategoryId? {
        // Extract merchant name from top of receipt (first 5 blocks)
        //val topBlocks = blocks.take(5)
        val merchantText = blocks.joinToString(" ") { it.text }.uppercase(Locale.getDefault())

        // Find merchant type
        val merchantType = detectMerchantType(merchantText)
            ?: return null

        // Match merchant type to user's categories
        return matchToCategory(merchantType, availableCategories)
    }

    /**
     * Detect type of merchant from text using locale-specific patterns.
     */
    private fun detectMerchantType(text: String): MerchantType? {
        // Check each merchant in the patterns
        for ((merchantName, merchantType) in patterns.merchantMappings) {
            if (text.contains(merchantName)) {
                return merchantType
            }
        }
        return null
    }

    /**
     * Match merchant type to user's existing categories by name similarity.
     */
    private fun matchToCategory(
        merchantType: MerchantType,
        availableCategories: List<CategoryInfo>
    ): CategoryId? {
        val keywords = patterns.getCategoryKeywords(merchantType)

        // Try exact match first
        for (keyword in keywords) {
            val match = availableCategories.firstOrNull {
                it.name.equals(keyword, ignoreCase = true)
            }
            if (match != null) return match.id
        }

        // Try partial match (category name contains keyword or vice versa)
        for (keyword in keywords) {
            val match = availableCategories.firstOrNull { cat ->
                cat.name.contains(keyword, ignoreCase = true) ||
                keyword.contains(cat.name, ignoreCase = true)
            }
            if (match != null) return match.id
        }

        return null
    }

    companion object {
        /**
         * Create detector with auto-detected locale from receipt text.
         */
        fun withAutoDetection(blocks: List<TextBlock>): MerchantCategoryDetector {
            val fullText = blocks.joinToString(" ") { it.text }.lowercase()

            // Detect locale based on keywords
            val germanScore = listOf(
                "summe", "gesamt", "brutto", "mwst", "betrag"
            ).count { fullText.contains(it) }

            val englishScore = listOf(
                "total", "subtotal", "amount", "tax", "balance"
            ).count { fullText.contains(it) }

            val patterns = if (englishScore > germanScore) {
                EnglishMerchantPatterns()
            } else {
                GermanMerchantPatterns() // Default to German
            }

            return MerchantCategoryDetector(patterns)
        }

        /**
         * Create detector for specific locale.
         */
        fun forLocale(localeCode: String): MerchantCategoryDetector {
            val patterns = when (localeCode.lowercase()) {
                "de", "de_de", "german" -> GermanMerchantPatterns()
                "en", "en_us", "en_gb", "english" -> EnglishMerchantPatterns()
                else -> GermanMerchantPatterns() // Default
            }
            return MerchantCategoryDetector(patterns)
        }
    }
}
