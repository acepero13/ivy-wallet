package com.ivy.receipts.category.locales

import com.ivy.receipts.category.MerchantCategoryPatterns
import com.ivy.receipts.category.MerchantType

/**
 * English (en_US) merchant patterns for category detection.
 * Focused on common US/UK stores.
 */
class EnglishMerchantPatterns : MerchantCategoryPatterns {
    override val merchantMappings = mapOf(
        // Grocery stores (US/UK)
        "WALMART" to MerchantType.GROCERIES,
        "TARGET" to MerchantType.GROCERIES,
        "KROGER" to MerchantType.GROCERIES,
        "SAFEWAY" to MerchantType.GROCERIES,
        "TESCO" to MerchantType.GROCERIES,
        "SAINSBURY" to MerchantType.GROCERIES,
        "ASDA" to MerchantType.GROCERIES,
        "WHOLE FOODS" to MerchantType.GROCERIES,
        "TRADER JOE" to MerchantType.GROCERIES,

        // Drugstores
        "CVS" to MerchantType.HEALTH_PERSONAL_CARE,
        "WALGREENS" to MerchantType.HEALTH_PERSONAL_CARE,
        "BOOTS" to MerchantType.HEALTH_PERSONAL_CARE,
        "SUPERDRUG" to MerchantType.HEALTH_PERSONAL_CARE,

        // Gas stations
        "SHELL" to MerchantType.GAS_TRANSPORT,
        "BP" to MerchantType.GAS_TRANSPORT,
        "CHEVRON" to MerchantType.GAS_TRANSPORT,
        "EXXON" to MerchantType.GAS_TRANSPORT,
        "MOBIL" to MerchantType.GAS_TRANSPORT,
        "TEXACO" to MerchantType.GAS_TRANSPORT,

        // Restaurants / Fast Food
        "MCDONALD" to MerchantType.RESTAURANT,
        "BURGER KING" to MerchantType.RESTAURANT,
        "KFC" to MerchantType.RESTAURANT,
        "SUBWAY" to MerchantType.RESTAURANT,
        "STARBUCKS" to MerchantType.RESTAURANT,
        "CHIPOTLE" to MerchantType.RESTAURANT,
        "TACO BELL" to MerchantType.RESTAURANT,

        // Pharmacies
        "PHARMACY" to MerchantType.PHARMACY,
        "CHEMIST" to MerchantType.PHARMACY,

        // Hardware stores
        "HOME DEPOT" to MerchantType.HARDWARE_HOME,
        "LOWE" to MerchantType.HARDWARE_HOME,
        "B&Q" to MerchantType.HARDWARE_HOME,
        "WICKES" to MerchantType.HARDWARE_HOME,

        // Electronics
        "BEST BUY" to MerchantType.ELECTRONICS,
        "APPLE STORE" to MerchantType.ELECTRONICS,
        "CURRYS" to MerchantType.ELECTRONICS,
        "PC WORLD" to MerchantType.ELECTRONICS,

        // Clothing
        "H&M" to MerchantType.CLOTHING,
        "ZARA" to MerchantType.CLOTHING,
        "GAP" to MerchantType.CLOTHING,
        "OLD NAVY" to MerchantType.CLOTHING,
        "UNIQLO" to MerchantType.CLOTHING,
        "PRIMARK" to MerchantType.CLOTHING
    )

    override fun getCategoryKeywords(merchantType: MerchantType): List<String> {
        return when (merchantType) {
            MerchantType.GROCERIES -> listOf(
                "Groceries", "Food", "Shopping"
            )
            MerchantType.HEALTH_PERSONAL_CARE -> listOf(
                "Personal Care", "Beauty", "Health", "Drugstore"
            )
            MerchantType.GAS_TRANSPORT -> listOf(
                "Transport", "Gas", "Fuel", "Car", "Auto"
            )
            MerchantType.RESTAURANT -> listOf(
                "Restaurant", "Food", "Dining", "Eating Out"
            )
            MerchantType.PHARMACY -> listOf(
                "Pharmacy", "Health", "Medicine", "Medical"
            )
            MerchantType.HARDWARE_HOME -> listOf(
                "Home", "Hardware", "DIY", "Garden"
            )
            MerchantType.ELECTRONICS -> listOf(
                "Electronics", "Tech", "Technology", "Gadgets"
            )
            MerchantType.CLOTHING -> listOf(
                "Clothing", "Fashion", "Apparel"
            )
        }
    }

    override val localeCode = "en_US"
}
