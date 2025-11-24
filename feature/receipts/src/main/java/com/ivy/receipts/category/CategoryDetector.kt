package com.ivy.receipts.category

import com.ivy.data.model.CategoryId
import com.ivy.receipts.ocr.TextBlock

/**
 * Interface for detecting categories from receipt text.
 *
 * Implementations can use different strategies:
 * - Merchant name matching (ALDI → Groceries)
 * - Item analysis (milk, bread → Groceries)
 * - Combined approaches
 */
interface CategoryDetector {
    /**
     * Detect the most likely category for a receipt.
     *
     * @param blocks OCR text blocks from the receipt
     * @param availableCategories List of user's existing categories to match against
     * @return CategoryId if a match is found, null otherwise
     */
    suspend fun detectCategory(
        blocks: List<TextBlock>,
        availableCategories: List<CategoryInfo>
    ): CategoryId?
}

/**
 * Simplified category info for matching.
 */
data class CategoryInfo(
    val id: CategoryId,
    val name: String
)
