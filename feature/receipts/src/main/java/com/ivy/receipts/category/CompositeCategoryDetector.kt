package com.ivy.receipts.category

import com.ivy.data.model.CategoryId
import com.ivy.receipts.ocr.TextBlock
import javax.inject.Inject

/**
 * Composite category detector that tries multiple strategies.
 *
 * Strategy priority:
 * 1. Try merchant name detection first (most reliable)
 * 2. If no match, try item-based detection
 * 3. Return null if both fail
 */
class CompositeCategoryDetector @Inject constructor() : CategoryDetector {

    private val merchantDetector = MerchantCategoryDetector()
    private val itemDetector = ItemCategoryDetector()

    override suspend fun detectCategory(
        blocks: List<TextBlock>,
        availableCategories: List<CategoryInfo>
    ): CategoryId? {
        // Try merchant detection first
        merchantDetector.detectCategory(blocks, availableCategories)?.let {
            return it
        }

        // Fallback to item-based detection
        return itemDetector.detectCategory(blocks, availableCategories)
    }
}
