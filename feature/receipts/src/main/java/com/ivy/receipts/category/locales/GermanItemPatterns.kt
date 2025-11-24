package com.ivy.receipts.category.locales

import com.ivy.receipts.category.ItemCategoryPatterns
import com.ivy.receipts.category.ItemType

/**
 * German (de_DE) item keyword patterns for category detection.
 */
class GermanItemPatterns : ItemCategoryPatterns {
    override val foodKeywords = listOf(
        "MILCH", "BROT", "BUTTER", "KÄSE", "FLEISCH", "WURST",
        "GEMÜSE", "OBST", "EI", "EIER", "JOGHURT", "QUARK",
        "SCHINKEN", "SALAMI", "KÄSE", "SAHNE", "SCHMAND"
    )

    override val beverageKeywords = listOf(
        "WASSER", "SAFT", "BIER", "WEIN", "COLA", "LIMO",
        "KAFFEE", "TEE", "MINERALWASSER", "LIMONADE", "SPRUDEL"
    )

    override val personalCareKeywords = listOf(
        "SHAMPOO", "SEIFE", "ZAHNPASTA", "CREME", "DEO",
        "DEODORANT", "RASIER", "DUSCHGEL", "BODYLOTION",
        "GESICHTSCREME", "HANDCREME"
    )

    override val medicineKeywords = listOf(
        "ASPIRIN", "IBUPROFEN", "VITAMIN", "TABLETTEN",
        "SALBE", "TROPFEN", "SCHMERZMITTEL", "HUSTENSAFT",
        "NASENSPRAY", "PFLASTER"
    )

    override val householdKeywords = listOf(
        "REINIGER", "WASCHMITTEL", "SPÜLMITTEL", "PAPIER",
        "MÜLL", "SCHWAMM", "PUTZMITTEL", "WASCHPULVER",
        "WEICHSPÜLER", "TOILETTENPAPIER", "KÜCHENPAPIER"
    )

    override val electronicsKeywords = listOf(
        "KABEL", "BATTERIE", "LADEGERÄT", "USB", "HDMI",
        "KOPFHÖRER", "NETZTEIL", "SPEICHERKARTE"
    )

    override fun getCategoryKeywords(itemType: ItemType): List<String> {
        return when (itemType) {
            ItemType.FOOD -> listOf(
                "Lebensmittel", "Groceries", "Food", "Einkauf"
            )
            ItemType.BEVERAGES -> listOf(
                "Getränke", "Beverages", "Drinks", "Lebensmittel", "Groceries"
            )
            ItemType.PERSONAL_CARE -> listOf(
                "Körperpflege", "Personal Care", "Beauty", "Drogerie", "Health"
            )
            ItemType.MEDICINE -> listOf(
                "Medizin", "Medicine", "Health", "Apotheke", "Pharmacy"
            )
            ItemType.HOUSEHOLD -> listOf(
                "Haushalt", "Household", "Home", "Cleaning"
            )
            ItemType.ELECTRONICS -> listOf(
                "Elektronik", "Electronics", "Tech", "Technology"
            )
        }
    }

    override val localeCode = "de_DE"
}
