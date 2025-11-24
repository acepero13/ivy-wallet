package com.ivy.receipts.category

/**
 * Defines locale-specific item keywords for category detection.
 */
interface ItemCategoryPatterns {
    /**
     * Keywords for food items in this locale.
     */
    val foodKeywords: List<String>

    /**
     * Keywords for beverage items in this locale.
     */
    val beverageKeywords: List<String>

    /**
     * Keywords for personal care items in this locale.
     */
    val personalCareKeywords: List<String>

    /**
     * Keywords for medicine/pharmacy items in this locale.
     */
    val medicineKeywords: List<String>

    /**
     * Keywords for household items in this locale.
     */
    val householdKeywords: List<String>

    /**
     * Keywords for electronics/tech items in this locale.
     */
    val electronicsKeywords: List<String>

    /**
     * Category keywords for matching to user's categories.
     * Maps item type to possible category names in this locale.
     */
    fun getCategoryKeywords(itemType: ItemType): List<String>

    /**
     * Locale code for this pattern set (e.g., "de_DE", "en_US").
     */
    val localeCode: String
}

/**
 * Types of items that can be recognized.
 */
enum class ItemType {
    FOOD,
    BEVERAGES,
    PERSONAL_CARE,
    MEDICINE,
    HOUSEHOLD,
    ELECTRONICS
}
