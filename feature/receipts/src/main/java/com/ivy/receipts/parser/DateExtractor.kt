package com.ivy.receipts.parser

import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Extracts dates from receipt text lines.
 * Tries multiple date formats based on locale patterns.
 */
class DateExtractor {

    /**
     * Extracts a date from receipt lines using locale-specific patterns.
     *
     * @param lines The text lines from the receipt
     * @param patterns Locale-specific patterns including date formats
     * @return The extracted date as an Instant, or current time if not found
     */
    fun extract(lines: List<String>, patterns: ReceiptPatterns): Instant {
        for (line in lines) {
            val match = patterns.datePattern.find(line) ?: continue
            val raw = match.groupValues.getOrNull(1) ?: continue

            Log.d(TAG, "Found potential date: $raw in line: $line")

            // Try each date format until one works
            for (fmt in patterns.dateFormats) {
                try {
                    val formatter = DateTimeFormatter.ofPattern(
                        fmt,
                        Locale.forLanguageTag(patterns.localeCode)
                    )
                    val date = LocalDate.parse(raw, formatter)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()

                    Log.d(TAG, "Successfully parsed date: $date using format: $fmt")
                    return date
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to parse '$raw' with format '$fmt': ${e.message}")
                }
            }
        }

        Log.d(TAG, "No valid date found in receipt, using current time")
        return Instant.now()
    }

    companion object {
        private const val TAG = "DateExtractor"
    }
}
