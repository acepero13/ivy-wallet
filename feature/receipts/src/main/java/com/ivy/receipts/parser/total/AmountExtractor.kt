package com.ivy.receipts.parser.total

import android.util.Log
import com.ivy.receipts.parser.ParserConstants
import com.ivy.receipts.parser.ReceiptPatterns
import com.ivy.receipts.parser.decimalSeparator
import com.ivy.receipts.parser.thousandsSeparator
import com.ivy.receipts.parser.toMoney

/**
 * Extracts and parses monetary amounts from receipt text.
 * Handles different decimal separators, currency symbols, and edge cases.
 */
class AmountExtractor {

    /**
     * Extracts a standalone amount from text with validation.
     * Filters out dates, percentages, and negative amounts.
     *
     * @param text The text containing a potential amount
     * @param patterns Locale-specific patterns for parsing
     * @return The extracted amount, or 0.0 if invalid or not found
     */
    fun extractStandaloneAmount(text: String, patterns: ReceiptPatterns): Double {
        val trimmed = text.trim()

        // Don't extract negative amounts (these are usually change/return amounts)
        if (trimmed.startsWith("-")) {
            Log.d(TAG, "Rejecting negative amount: $trimmed")
            return 0.0
        }

        // Filter out dates that look like amounts
        // Reject patterns like: "25.11.2025", "25.11. 20", "25.11.", ": 25.11", "25.11.20"
        // Key characteristic: 2 dots with 2 digits on each side (day.month pattern)
        if (trimmed.contains(Regex("\\d{1,2}\\.\\d{1,2}\\."))) {
            Log.d(TAG, "Rejecting date pattern: $trimmed")
            return 0.0
        }

        // Filter out tax percentages
        // Reject patterns like: "A:19,00%", "B 19,O% Net to", "19.0%", or any text containing %
        // Also reject patterns like "A:19,00" (tax rate prefix format)
        if (trimmed.contains("%") || trimmed.matches(Regex(".*[A-Z]:\\s*\\d+[,.].*"))) {
            Log.d(TAG, "Rejecting tax/percentage pattern: $trimmed")
            return 0.0
        }

        // More flexible regex that handles:
        // - "51,54 ER" (amount with suffix)
        // - "84,25 EUR" (amount with currency)
        // - "51.54" (amount with different decimal separator)
        // - "1.234,56" (German format with thousand separator)
        // - "84,25" (simple format)
        val regex = Regex("[€$£]?\\s*\\d{1,3}(?:[.,]\\d{3})*[,.]\\d{2}(?:\\s*[A-Z]{1,4})?\\s*[€$£]?")
        val match = regex.find(trimmed)

        if (match == null) {
            Log.d(TAG, "No amount pattern found in: $trimmed")
            return 0.0
        }

        // Extract just the numeric part, removing currency symbols and extra text
        val raw = match.value
            .replace(Regex("[€$£\\s]"), "")
            .replace(Regex("[A-Z]+$"), "") // Remove trailing letters like "ER" or "EUR"
            .trim()

        // Double-check: if it contains date patterns, reject it
        // Reject if it has 2+ dots (e.g., "25.11.20" would become "2511.20" after extraction)
        if (raw.count { it == '.' } >= ParserConstants.MIN_DOTS_FOR_DATE_REJECTION) {
            Log.d(TAG, "Rejecting multi-dot pattern (date): $raw")
            return 0.0
        }

        // Reject if it looks like a year (4 consecutive digits)
        if (raw.contains(Regex("\\d{${ParserConstants.MIN_DIGITS_FOR_YEAR_REJECTION}}"))) {
            Log.d(TAG, "Rejecting year pattern: $raw")
            return 0.0
        }

        val amount = parseMoney(raw, patterns)
        Log.d(TAG, "Extracted amount $amount from: $trimmed")
        return amount
    }

    /**
     * Extracts a negative amount (typically from payment confirmation lines).
     *
     * @param text The text containing a negative amount
     * @return The absolute value of the amount
     */
    fun extractNegativeAmount(text: String): Double {
        val amount = text.replace("-", "")
            .replace(Regex("[€$£\\s]"), "")
            .replace(",", ".")
            .toDoubleOrNull() ?: 0.0

        Log.d(TAG, "Extracted negative amount $amount from: $text")
        return amount
    }

    /**
     * Parses a monetary string using locale-specific separators.
     *
     * @param raw The raw numeric string (e.g., "1.234,56" or "1,234.56")
     * @param patterns Locale-specific patterns providing separator info
     * @return The parsed amount as a Double
     */
    fun parseMoney(raw: String, patterns: ReceiptPatterns): Double =
        raw.toMoney(
            decimalSeparator = patterns.decimalSeparator(),
            thousandsSeparator = patterns.thousandsSeparator()
        )

    companion object {
        private const val TAG = "AmountExtractor"
    }
}