package com.ivy.receipts.category

/**
 * Defines locale-specific merchant keywords for category detection.
 */
interface MerchantCategoryPatterns {
    /**
     * Map of merchant names to their business type.
     * Keys should be uppercase for case-insensitive matching.
     */
    val merchantMappings: Map<String, MerchantType>

    /**
     * Category keywords for matching to user's categories.
     * Maps merchant type to possible category names in this locale.
     */
    fun getCategoryKeywords(merchantType: MerchantType): List<String>

    /**
     * Locale code for this pattern set (e.g., "de_DE", "en_US").
     */
    val localeCode: String
}

/**
 * Types of merchants we can recognize.
 */
enum class MerchantType {
    GROCERIES,
    HEALTH_PERSONAL_CARE,
    GAS_TRANSPORT,
    RESTAURANT,
    PHARMACY,
    HARDWARE_HOME,
    ELECTRONICS,
    CLOTHING
}
