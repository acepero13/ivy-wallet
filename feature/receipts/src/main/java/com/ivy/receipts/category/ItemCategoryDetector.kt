package com.ivy.receipts.category

import com.ivy.data.model.CategoryId
import com.ivy.receipts.category.locales.EnglishItemPatterns
import com.ivy.receipts.category.locales.GermanItemPatterns
import com.ivy.receipts.ocr.TextBlock
import java.util.Locale

/**
 * Detects category based on analyzing items in the receipt.
 *
 * Strategy:
 * 1. Extract item names from middle section of receipt
 * 2. Classify each item into a category type
 * 3. Find the dominant category type
 * 4. Match to user's existing categories
 *
 * Example:
 * - Items: "Milch", "Brot", "Butter" → FOOD → "Groceries"
 * - Items: "Aspirin", "Vitamin C" → MEDICINE → "Health"
 */
class ItemCategoryDetector(
    private val patterns: ItemCategoryPatterns = GermanItemPatterns()
) : CategoryDetector {

    override suspend fun detectCategory(
        blocks: List<TextBlock>,
        availableCategories: List<CategoryInfo>
    ): CategoryId? {
        // Extract item text (skip first 5 blocks which are usually header/merchant info)
        val itemBlocks = blocks.drop(5).take(20)
        val itemTexts = itemBlocks.map { it.text.uppercase(Locale.getDefault()) }

        // Classify each item
        val itemTypes = itemTexts.mapNotNull { classifyItem(it) }

        if (itemTypes.isEmpty()) return null

        // Find dominant category type
        val dominantType = itemTypes.groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: return null

        // Match to user's categories
        return matchToCategory(dominantType, availableCategories)
    }

    /**
     * Classify a single item text into a category type using locale-specific patterns.
     */
    private fun classifyItem(itemText: String): ItemType? {
        return when {
            patterns.foodKeywords.any { itemText.contains(it) } -> ItemType.FOOD
            patterns.beverageKeywords.any { itemText.contains(it) } -> ItemType.BEVERAGES
            patterns.personalCareKeywords.any { itemText.contains(it) } -> ItemType.PERSONAL_CARE
            patterns.medicineKeywords.any { itemText.contains(it) } -> ItemType.MEDICINE
            patterns.householdKeywords.any { itemText.contains(it) } -> ItemType.HOUSEHOLD
            patterns.electronicsKeywords.any { itemText.contains(it) } -> ItemType.ELECTRONICS
            else -> null
        }
    }

    /**
     * Match item type to user's existing categories.
     */
    private fun matchToCategory(
        itemType: ItemType,
        availableCategories: List<CategoryInfo>
    ): CategoryId? {
        val keywords = patterns.getCategoryKeywords(itemType)

        // Try exact match first
        for (keyword in keywords) {
            val match = availableCategories.firstOrNull {
                it.name.equals(keyword, ignoreCase = true)
            }
            if (match != null) return match.id
        }

        // Try partial match
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
        fun withAutoDetection(blocks: List<TextBlock>): ItemCategoryDetector {
            val fullText = blocks.joinToString(" ") { it.text }.lowercase()

            // Detect locale based on keywords
            val germanScore = listOf(
                "milch", "brot", "butter", "käse", "wurst", "gemüse"
            ).count { fullText.contains(it) }

            val englishScore = listOf(
                "milk", "bread", "butter", "cheese", "meat", "vegetable"
            ).count { fullText.contains(it) }

            val patterns = if (englishScore > germanScore) {
                EnglishItemPatterns()
            } else {
                GermanItemPatterns() // Default to German
            }

            return ItemCategoryDetector(patterns)
        }

        /**
         * Create detector for specific locale.
         */
        fun forLocale(localeCode: String): ItemCategoryDetector {
            val patterns = when (localeCode.lowercase()) {
                "de", "de_de", "german" -> GermanItemPatterns()
                "en", "en_us", "en_gb", "english" -> EnglishItemPatterns()
                else -> GermanItemPatterns() // Default
            }
            return ItemCategoryDetector(patterns)
        }
    }
}
