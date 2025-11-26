package com.ivy.receipts.parser.total

import android.util.Log
import com.ivy.receipts.parser.ReceiptPatterns
import timber.log.Timber

/**
 * Extracts totals using text-based fallback logic when spatial parsing fails.
 * This is a simple pattern-matching approach that looks for total keywords
 * followed by amounts in the same line.
 */
class FallbackExtractor {

    /**
     * Searches for total amounts in text lines using pattern matching.
     * Falls back to this when spatial parsing strategies fail.
     *
     * @param lines The text lines from the receipt
     * @param patterns Locale-specific patterns including total pattern
     * @param amountExtractor For parsing monetary amounts
     * @return The extracted total, or 0.0 if not found
     */
    fun extractTotal(
        lines: List<String>,
        patterns: ReceiptPatterns,
        amountExtractor: AmountExtractor
    ): Double {
        Timber.tag(TAG).d("Attempting fallback extraction from ${lines.size} lines")

        for (line in lines) {
            val match = patterns.totalPattern.find(line)
            if (match != null) {
                Timber.tag(TAG).d("Found total pattern match in line: $line")
                val amount = extractAmountFromMatch(match, patterns, amountExtractor)
                if (amount > 0) {
                    Timber.tag(TAG).d("Extracted total from fallback: $amount")
                    return amount
                }
            }
        }

        Timber.tag(TAG).d("No total found in fallback extraction")
        return 0.0
    }

    /**
     * Extracts the amount from a regex match result.
     * Iterates through match groups to find a numeric value.
     */
    private fun extractAmountFromMatch(
        match: MatchResult,
        patterns: ReceiptPatterns,
        amountExtractor: AmountExtractor
    ): Double {
        // Check each capture group for a numeric value
        for (i in 1 until match.groupValues.size) {
            val value = match.groupValues[i]
            if (value.matches(Regex("\\d+[,.]\\d+"))) {
                val amount = amountExtractor.parseMoney(value, patterns)
                Timber.tag(TAG).d("Parsed amount $amount from match group $i: $value")
                return amount
            }
        }

        Timber.tag(TAG).d("No numeric value found in match groups")
        return 0.0
    }

    companion object {
        private const val TAG = "FallbackExtractor"
    }
}