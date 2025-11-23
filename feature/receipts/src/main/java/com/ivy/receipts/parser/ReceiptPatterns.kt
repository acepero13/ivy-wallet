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