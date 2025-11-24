package com.ivy.receipts.category.locales

import com.ivy.receipts.category.ItemCategoryPatterns
import com.ivy.receipts.category.ItemType

/**
 * English (en_US) item keyword patterns for category detection.
 */
class EnglishItemPatterns : ItemCategoryPatterns {
    override val foodKeywords = listOf(
        "MILK", "BREAD", "BUTTER", "CHEESE", "MEAT", "SAUSAGE",
        "VEGETABLE", "FRUIT", "EGG", "EGGS", "YOGURT", "YOGHURT",
        "HAM", "SALAMI", "CREAM", "CHICKEN", "BEEF", "PORK"
    )

    override val beverageKeywords = listOf(
        "WATER", "JUICE", "BEER", "WINE", "COLA", "SODA",
        "COFFEE", "TEA", "LEMONADE", "COKE", "PEPSI", "SPRITE"
    )

    override val personalCareKeywords = listOf(
        "SHAMPOO", "SOAP", "TOOTHPASTE", "CREAM", "DEODORANT",
        "RAZOR", "SHOWER GEL", "BODY LOTION", "FACE CREAM",
        "HAND CREAM", "CONDITIONER"
    )

    override val medicineKeywords = listOf(
        "ASPIRIN", "IBUPROFEN", "VITAMIN", "TABLET", "TABLETS",
        "OINTMENT", "DROPS", "PAIN RELIEF", "COUGH SYRUP",
        "NASAL SPRAY", "BANDAGE", "PLASTER"
    )

    override val householdKeywords = listOf(
        "CLEANER", "DETERGENT", "DISH SOAP", "PAPER", "TRASH",
        "SPONGE", "CLEANING", "LAUNDRY", "SOFTENER",
        "TOILET PAPER", "KITCHEN PAPER", "TOWEL"
    )

    override val electronicsKeywords = listOf(
        "CABLE", "BATTERY", "CHARGER", "USB", "HDMI",
        "HEADPHONES", "ADAPTER", "MEMORY CARD", "EARPHONES"
    )

    override fun getCategoryKeywords(itemType: ItemType): List<String> {
        return when (itemType) {
            ItemType.FOOD -> listOf(
                "Groceries", "Food", "Shopping"
            )
            ItemType.BEVERAGES -> listOf(
                "Beverages", "Drinks", "Groceries", "Food"
            )
            ItemType.PERSONAL_CARE -> listOf(
                "Personal Care", "Beauty", "Health", "Drugstore"
            )
            ItemType.MEDICINE -> listOf(
                "Medicine", "Health", "Pharmacy", "Medical"
            )
            ItemType.HOUSEHOLD -> listOf(
                "Household", "Home", "Cleaning"
            )
            ItemType.ELECTRONICS -> listOf(
                "Electronics", "Tech", "Technology", "Gadgets"
            )
        }
    }

    override val localeCode = "en_US"
}
