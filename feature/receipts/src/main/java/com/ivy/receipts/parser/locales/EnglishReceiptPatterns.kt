package com.ivy.receipts.parser.locales

import com.ivy.receipts.parser.ReceiptPatterns

/**
 * English (en_US) receipt parsing patterns.
 * Handles common US/UK receipt formats with period as decimal separator.
 */
class EnglishReceiptPatterns : ReceiptPatterns {
    override fun isTotalKeyword(text: String): Boolean {
        val cleanLine = text.replace(Regex("[€$£]"), "").trim()
        return cleanLine.matches(Regex("(?i).*(total|amount\\s+due|balance\\s+due|sum).*"))
    }

    override val netPattern = Regex("(?i).*\\b(net|subtotal)\\b.*?(\\d+[,.]\\d+)")

    override val cashPaymentPattern = Regex("(?i).*\\b(cash|cash payment)\\b.*")

    override val itemPattern = Regex("(.+?)\\s+\\$?(\\d+\\.\\d{2})\\s*$")

    override val totalPattern = Regex("(?i)\\b(total|amount due|balance due)\\b\\s*:?\\s*\\$?\\s*(\\d+[,.]?\\d*\\.\\d{2})")

    override val totalGrossPattern = Regex("(?i)\\b(gross|grand total)\\b\\s*:?\\s*\\$?\\s*(\\d+[,.]?\\d+\\.\\d{2})")

    override val datePattern = Regex("\\b(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})\\b")

    override val dateFormats = listOf(
        "MM/dd/yyyy",
        "MM-dd-yyyy",
        "dd/MM/yyyy",
        "yyyy-MM-dd",
        "MM/dd/yy"
    )

    override val defaultCurrency = "USD"

    override val localeCode = "en_US"

    companion object {
        const val DECIMAL_SEPARATOR = '.'
        const val THOUSANDS_SEPARATOR = ','
    }
}