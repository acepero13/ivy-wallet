package com.ivy.receipts.parser

/**
 * Interface for locale-specific receipt parsing patterns.
 * Each locale implementation provides regex patterns for common receipt elements.
 */
interface ReceiptPatterns {
    fun isTotalKeyword(text: String): Boolean

    /** Pattern for matching net/netto amounts */
    val netPattern: Regex?

    /** Pattern for matching cash payment indicators */
    val cashPaymentPattern: Regex?

    /** Pattern for matching individual line items with amounts */
    val itemPattern: Regex?

    /** Pattern for matching total/sum amounts */
    val totalPattern: Regex

    /** Pattern for matching gross/brutto amounts */
    val totalGrossPattern: Regex?

    /** Pattern for matching dates on receipts */
    val datePattern: Regex

    /** Date format patterns for parsing dates (e.g., "dd.MM.yyyy") */
    val dateFormats: List<String>

    /** Default currency code for this locale */
    val defaultCurrency: String

    /** Locale identifier (e.g., "de_DE", "en_US") */
    val localeCode: String
}

/**
 * Helper to convert matched string to monetary amount.
 * Handles locale-specific decimal separators.
 */
fun String?.toMoney(decimalSeparator: Char = '.', thousandsSeparator: Char = ','): Double {
    if (this == null) return 0.0

    // Remove thousands separator and replace decimal separator
    val normalized = this
        .replace(thousandsSeparator.toString(), "")
        .replace(decimalSeparator, '.')
        .trim()

    return normalized.toDoubleOrNull() ?: 0.0
}

/**
 * Normalizes amounts for comparison purposes.
 * For receipts (typically < 1000 EUR), we can safely assume:
 * - Any separator followed by exactly 2 digits is a decimal separator
 * - This handles OCR confusion between commas and periods (51,54 vs 51.54)
 *
 * Examples:
 * - "51,54" -> 51.54
 * - "51.54" -> 51.54
 * - "29,99" -> 29.99
 * - "29.99" -> 29.99
 * - "51,54 EUR" -> 51.54
 * - "1.234,56" -> 1234.56 (German thousands format)
 * - "1,234.56" -> 1234.56 (US thousands format)
 */
fun String.normalizeReceiptAmount(): Double {
    val trimmed = this.trim()

    // Pattern: digits (with optional thousands separators), then separator, then exactly 2 digits
    // Allows for trailing text like "EUR", "ER", etc.
    // Captures the full amount including thousands separators in the first group
    val decimalPattern = Regex("""([\d.,]+)[.,](\d{2})(?:\s|$|[A-Z])""")
    val match = decimalPattern.find(trimmed)

    if (match != null) {
        val fullAmount = match.groupValues[1]  // e.g., "1.234" or "51"
        val decimalPart = match.groupValues[2] // e.g., "56" or "54"

        // Remove all separators from the whole part (both . and ,)
        val cleanWhole = fullAmount.replace(Regex("[.,]"), "")

        return "$cleanWhole.$decimalPart".toDoubleOrNull() ?: 0.0
    }

    // Also try simple pattern at the end of string for exact matches
    val simplePattern = Regex("""([\d.,]+)[.,](\d{2})$""")
    val simpleMatch = simplePattern.find(trimmed)

    if (simpleMatch != null) {
        val fullAmount = simpleMatch.groupValues[1]
        val decimalPart = simpleMatch.groupValues[2]
        val cleanWhole = fullAmount.replace(Regex("[.,]"), "")
        return "$cleanWhole.$decimalPart".toDoubleOrNull() ?: 0.0
    }

    // No decimal part found - return 0
    return 0.0
}