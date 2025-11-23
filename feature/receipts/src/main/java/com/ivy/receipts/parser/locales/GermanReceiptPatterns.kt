package com.ivy.receipts.parser.locales

import com.ivy.receipts.parser.ReceiptPatterns

/**
 * German (de_DE) receipt parsing patterns.
 * Handles common German receipt formats with comma as decimal separator.
 */
class GermanReceiptPatterns : ReceiptPatterns {
    override fun isTotalKeyword(text: String): Boolean {
        val cleanLine = text.replace(Regex("[€$£]"), "").trim()
        return cleanLine.matches(Regex("(?i).*(summe|gesamt|total|brutto|betrag).*"))
    }

    override val netPattern = Regex("(?i).*\\b(netto|nettoumsatz)\\b.*?(\\d+[,.]\\d+)")

    override val cashPaymentPattern = Regex("(?i).*\\b(barzahlung|bar)\\b.*")

    override val itemPattern = Regex("(.+?)\\s+(\\d+,\\d+)\\s*€?\\s*$")

    override val totalPattern =
        Regex("(?i)\\b(summe|gesamt|total|betrag)\\b\\s*(?:eur|€)?\\s*:?\\s*(\\d+\\s*,\\s*\\d+)")

    override val totalGrossPattern =
        Regex("(?i)\\b(brutto|bruttoumsatz)\\b\\s*(?:eur|€)?\\s*:?\\s*([+*])?\\s*(\\d+\\s*[,.]\\s*\\d+)")

    override val datePattern = Regex("\\b(\\d{1,2}[./-]\\d{1,2}[./-]\\d{4})\\b")

    override val dateFormats = listOf(
        "dd.MM.yyyy",
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "yyyy-MM-dd"
    )

    override val defaultCurrency = "EUR"

    override val localeCode = "de_DE"

    companion object {
        const val DECIMAL_SEPARATOR = ','
        const val THOUSANDS_SEPARATOR = '.'
    }
}