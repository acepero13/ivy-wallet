package com.ivy.receipts.parser.locales

import com.ivy.receipts.parser.ReceiptPatterns

/**
 * French (fr_FR) receipt parsing patterns.
 * Handles common French receipt formats with comma as decimal separator.
 */
class FrenchReceiptPatterns : ReceiptPatterns {
    override val netPattern = Regex("(?i).*\\b(net|montant net)\\b.*?(\\d+[,.]\\d+)")

    override val cashPaymentPattern = Regex("(?i).*\\b(espèces|especes|liquide)\\b.*")

    override val itemPattern = Regex("(.+?)\\s+(\\d+,\\d{2})\\s*€?\\s*$")

    override val totalPattern = Regex("(?i)\\b(total|montant|somme)\\b\\s*:?\\s*(\\d+\\s*,\\s*\\d{2})")

    override val totalGrossPattern = Regex("(?i)\\b(brut|total ttc)\\b\\s*:?\\s*(\\d+[,.]\\d{2})")

    override val datePattern = Regex("\\b(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})\\b")

    override val dateFormats = listOf(
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "dd.MM.yyyy",
        "yyyy-MM-dd"
    )

    override val defaultCurrency = "EUR"

    override val localeCode = "fr_FR"

    companion object {
        const val DECIMAL_SEPARATOR = ','
        const val THOUSANDS_SEPARATOR = ' ' // French uses space for thousands
    }
}