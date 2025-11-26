package com.ivy.receipts.category.locales

import com.ivy.receipts.category.MerchantCategoryPatterns
import com.ivy.receipts.category.MerchantType

/**
 * German (de_DE) merchant patterns for category detection.
 * Focused on common German/European stores.
 */
class GermanMerchantPatterns : MerchantCategoryPatterns {
    override val merchantMappings = mapOf(
        // Grocery stores
        "ALDI" to MerchantType.GROCERIES,
        "LIDL" to MerchantType.GROCERIES,
        "REWE" to MerchantType.GROCERIES,
        "EDEKA" to MerchantType.GROCERIES,
        "PENNY" to MerchantType.GROCERIES,
        "NETTO" to MerchantType.GROCERIES,
        "KAUFLAND" to MerchantType.GROCERIES,
        "REAL" to MerchantType.GROCERIES,

        // Drugstores
        "DM" to MerchantType.HEALTH_PERSONAL_CARE,
        "ROSSMANN" to MerchantType.HEALTH_PERSONAL_CARE,
        "MÜLLER" to MerchantType.HEALTH_PERSONAL_CARE,
        "MUELLER" to MerchantType.HEALTH_PERSONAL_CARE,

        // Gas stations
        "SHELL" to MerchantType.GAS_TRANSPORT,
        "ARAL" to MerchantType.GAS_TRANSPORT,
        "ESSO" to MerchantType.GAS_TRANSPORT,
        "TOTAL" to MerchantType.GAS_TRANSPORT,
        "JET" to MerchantType.GAS_TRANSPORT,
        "AGIP" to MerchantType.GAS_TRANSPORT,

        // Restaurants / Fast Food
        "MCDONALD" to MerchantType.RESTAURANT,
        "BURGER KING" to MerchantType.RESTAURANT,
        "KFC" to MerchantType.RESTAURANT,
        "SUBWAY" to MerchantType.RESTAURANT,
        "PIZZA" to MerchantType.RESTAURANT,

        // Pharmacies
        "APOTHEKE" to MerchantType.PHARMACY,

        // Hardware stores
        "BAUHAUS" to MerchantType.HARDWARE_HOME,
        "OBI" to MerchantType.HARDWARE_HOME,
        "HORNBACH" to MerchantType.HARDWARE_HOME,
        "TOOM" to MerchantType.HARDWARE_HOME,

        // Electronics
        "MEDIA MARKT" to MerchantType.ELECTRONICS,
        "SATURN" to MerchantType.ELECTRONICS,
        "CONRAD" to MerchantType.ELECTRONICS,

        // Clothing
        "H&M" to MerchantType.CLOTHING,
        "ZARA" to MerchantType.CLOTHING,
        "C&A" to MerchantType.CLOTHING,
        "PRIMARK" to MerchantType.CLOTHING,
        "Ernstngi family" to MerchantType.CLOTHING,
        "Ernstings family" to MerchantType.CLOTHING,
        "Ernsting's family" to MerchantType.CLOTHING,
        "Ernsting's famiļy GmbH" to MerchantType.CLOTHING,
    )

    override fun getCategoryKeywords(merchantType: MerchantType): List<String> {
        return when (merchantType) {
            MerchantType.GROCERIES -> listOf(
                "Lebensmittel", "Groceries", "Food", "Einkauf", "Shopping"
            )
            MerchantType.HEALTH_PERSONAL_CARE -> listOf(
                "Körperpflege", "Personal Care", "Beauty", "Drogerie", "Health"
            )
            MerchantType.GAS_TRANSPORT -> listOf(
                "Transport", "Tankstelle", "Auto", "Car", "Gas", "Fuel"
            )
            MerchantType.RESTAURANT -> listOf(
                "Restaurant", "Essen", "Food", "Dining", "Gastronomie"
            )
            MerchantType.PHARMACY -> listOf(
                "Apotheke", "Pharmacy", "Health", "Medizin", "Medicine", "Medical"
            )
            MerchantType.HARDWARE_HOME -> listOf(
                "Baumarkt", "Haus", "Home", "Hardware", "DIY", "Garden"
            )
            MerchantType.ELECTRONICS -> listOf(
                "Elektronik", "Electronics", "Tech", "Technology", "Gadgets"
            )
            MerchantType.CLOTHING -> listOf(
                "Kleidung", "Clothing", "Fashion", "Mode", "Apparel"
            )
        }
    }

    override val localeCode = "de_DE"
}
