package com.ivy.receipts.parser

import com.ivy.receipts.ocr.OcrReceipt
import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.parser.locales.EnglishReceiptPatterns
import com.ivy.receipts.parser.locales.GermanReceiptPatterns
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Multi-locale receipt parser using regex patterns.
 * Supports automatic locale detection or manual locale specification.
 *
 * @param patterns Optional locale-specific patterns. If null, will auto-detect.
 */
class RegexMlkitParser(
    private var patterns: ReceiptPatterns? = null
) : ReceiptParseable {

    override fun parse(blocks: List<TextBlock>): OcrReceipt {
        val allLines = extractTextLines(blocks)
        val currentPatterns = getOrDetectLocalizedPatterns(allLines)
        val items = parseItems(allLines, currentPatterns)
        val total = calculateTotal(items)
        val date = extractDate(allLines, currentPatterns)

        return OcrReceipt(
            total = total,
            currency = currentPatterns.defaultCurrency,
            date = date,
            categories = emptyList() // Categories would be determined by the user or ML classification
        )
    }

    /**
     * Extract all text lines from OCR blocks.
     */
    private fun extractTextLines(blocks: List<TextBlock>): List<String> {
        return blocks.flatMap { block ->
            block.lines.map { it.text }
        }
    }

    /**
     * Get existing patterns or auto-detect from text.
     */
    private fun getOrDetectLocalizedPatterns(lines: List<String>): ReceiptPatterns {
        if (patterns == null) {
            patterns = detectLocale(lines)
        }
        return patterns ?: GermanReceiptPatterns()
    }

    /**
     * Parse all lines into structured items.
     * Handles receipts where amounts are on separate lines from labels.
     */
    private fun parseItems(lines: List<String>, patterns: ReceiptPatterns): List<ParsedItem> {
        val items = mutableListOf<ParsedItem>()

        for (i in lines.indices) {
            val trimmedLine = lines[i].trim()
            if (trimmedLine.isEmpty()) continue

            // Try to parse current line
            parseLine(trimmedLine, patterns)?.let { item ->
                items.add(item)
            }

            // Check if this line is a total keyword without amount
            // and the next line(s) might contain the amount
            if (isTotalKeyword(trimmedLine, patterns) && i < lines.lastIndex) {
                // Strategy: Find the last significant positive amount before any
                // payment-like negative (which is usually the total as negative).
                // A "payment-like negative" is a large negative that matches a previous positive.

                val candidates = mutableListOf<Pair<Int, Double>>() // line number to amount
                val negatives = mutableListOf<Pair<Int, Double>>()

                for (j in (i + 1)..(i + 20).coerceAtMost(lines.lastIndex)) {
                    val nextLine = lines[j].trim()

                    // Extract negative amounts
                    val negativeMatch = Regex("^-?(\\d+[,.]\\d{2})").find(nextLine)
                    if (negativeMatch != null && nextLine.startsWith("-")) {
                        val negAmount = negativeMatch.groupValues[1]
                            .replace(",", ".")
                            .toDoubleOrNull() ?: 0.0
                        if (negAmount > 10.0) { // Large negatives are likely payments
                            negatives.add(j to negAmount)
                        }
                    }

                    // Extract positive amounts
                    val amount = extractStandaloneAmount(nextLine, patterns)
                    if (amount > 0) {
                        candidates.add(j to amount)
                    }
                }

                // Find a positive that has a matching negative payment after it
                var totalAmount: Double? = null
                for ((posIndex, posAmount) in candidates.reversed()) {
                    // Check if there's a matching negative payment after this positive
                    val hasMatchingNegative = negatives.any {
                        it.first > posIndex && abs(it.second - posAmount) < 0.01
                    }
                    if (hasMatchingNegative) {
                        totalAmount = posAmount
                        break
                    }
                }

                // Fallback: if no matching negative found, take the last positive
                if (totalAmount == null) {
                    totalAmount = candidates.lastOrNull()?.second
                }

                if (totalAmount != null && totalAmount > 0) {
                    items.add(ParsedItem("TOTAL", totalAmount, ItemType.TOTAL))
                }
            }
        }

        return items
    }

    /**
     * Check if line contains only a total keyword (without amount).
     */
    private fun isTotalKeyword(line: String, patterns: ReceiptPatterns): Boolean {
        val cleanLine = line.replace(Regex("[€$£]"), "").trim()

        // German keywords
        if (cleanLine.matches(Regex("(?i)^(summe|gesamt|total|brutto)\\s*(eur|usd|€)?\\s*$"))) {
            return true
        }

        // English keywords
        if (cleanLine.matches(Regex("(?i)^(total|amount\\s+due|balance\\s+due|sum)\\s*(eur|usd|€|\\$)?\\s*$"))) {
            return true
        }

        return false
    }

    /**
     * Extract amount from a line that contains only a number.
     */
    private fun extractStandaloneAmount(line: String, patterns: ReceiptPatterns): Double {
        val cleanLine = line.trim()

        // Try to match just a number with decimal separator and optional currency symbols
        // Handles: "27,66", "27.66", "$15.99", "15.99 €", etc.
        val amountPattern = Regex("^[€$£]?\\s*-?\\d+[,.]\\d{2}\\s*[€$£]?\\s*$")
        if (amountPattern.matches(cleanLine)) {
            val numericPart = cleanLine.replace(Regex("[€$£\\s]"), "")
            return numericPart.toMoney(
                decimalSeparator = getDecimalSeparator(patterns),
                thousandsSeparator = getThousandsSeparator(patterns)
            ).takeIf { it > 0 } ?: 0.0
        }

        return 0.0
    }

    /**
     * Parse a single line and return a ParsedItem if matched.
     */
    private fun parseLine(line: String, patterns: ReceiptPatterns): ParsedItem? {
        return when {
            shouldSkipLine(line, patterns) -> null
            else -> matchPatterns(line, patterns)
        }
    }

    /**
     * Check if line should be skipped (net amounts, cash payment indicators, etc.).
     */
    private fun shouldSkipLine(line: String, patterns: ReceiptPatterns): Boolean {
        return patterns.netPattern?.matches(line) == true ||
                patterns.cashPaymentPattern?.matches(line) == true
    }

    /**
     * Try to match line against patterns in priority order.
     */
    private fun matchPatterns(line: String, patterns: ReceiptPatterns): ParsedItem? {
        // Total pattern (highest priority)
        patterns.totalPattern.find(line)?.let { match ->
            val amount = extractAmount(match, patterns)
            if (amount > 0) {
                return ParsedItem("TOTAL", amount, ItemType.TOTAL)
            }
        }

        // Gross total pattern
        patterns.totalGrossPattern?.find(line)?.let { match ->
            val amount = extractAmount(match, patterns)
            if (amount > 0) {
                return ParsedItem("TOTAL GROSS", amount, ItemType.GROSS)
            }
        }

        // Item pattern (individual line items)
        patterns.itemPattern?.find(line)?.let { match ->
            return parseLineItem(match, patterns)
        }

        return null
    }

    /**
     * Parse an individual line item from regex match.
     */
    private fun parseLineItem(match: MatchResult, patterns: ReceiptPatterns): ParsedItem? {
        if (match.groupValues.size < 3) return null

        val itemName = match.groupValues[1].trim()
        val amountStr = match.groupValues[2]
        val amount = amountStr.toMoney(
            decimalSeparator = getDecimalSeparator(patterns),
            thousandsSeparator = getThousandsSeparator(patterns)
        )

        return if (amount > 0 && itemName.isNotBlank()) {
            ParsedItem(itemName, amount, ItemType.ITEM)
        } else {
            null
        }
    }

    /**
     * Calculate final total from parsed items.
     * Prefers TOTAL > GROSS > sum of items.
     */
    private fun calculateTotal(items: List<ParsedItem>): Double {
        return items.firstOrNull { it.type == ItemType.TOTAL }?.amount
            ?: items.firstOrNull { it.type == ItemType.GROSS }?.amount
            ?: items.filter { it.type == ItemType.ITEM }.sumOf { it.amount }
    }

    /**
     * Extract amount from regex match result.
     * Handles different group positions based on pattern structure.
     */
    private fun extractAmount(match: MatchResult, patterns: ReceiptPatterns): Double {
        // Try to find the first group that looks like a number
        for (i in 1 until match.groupValues.size) {
            val value = match.groupValues[i]
            if (value.matches(Regex("\\d+[,.]\\d+"))) {
                return value.toMoney(
                    decimalSeparator = getDecimalSeparator(patterns),
                    thousandsSeparator = getThousandsSeparator(patterns)
                )
            }
        }
        return 0.0
    }

    /**
     * Detect locale from receipt text content.
     * Uses keyword frequency to determine most likely locale.
     */
    private fun detectLocale(lines: List<String>): ReceiptPatterns {
        val fullText = lines.joinToString(" ").lowercase()

        // German indicators
        val germanScore = listOf(
            "summe", "gesamt", "brutto", "netto", "mehrwertsteuer", "mwst",
            "barzahlung", "bar", "vielen dank", "betrag", "eur", "€"
        ).count { fullText.contains(it) }

        // English indicators
        val englishScore = listOf(
            "total", "subtotal", "amount", "cash", "credit", "debit",
            "thank you", "receipt", "tax", "usd", "$", "balance"
        ).count { fullText.contains(it) }

        // Also check decimal separator usage
        val hasCommaDecimals = Regex("\\d+,\\d{2}").find(fullText) != null
        val hasPeriodDecimals = Regex("\\d+\\.\\d{2}").find(fullText) != null

        val adjustedGermanScore = germanScore + if (hasCommaDecimals) 2 else 0
        val adjustedEnglishScore = englishScore + if (hasPeriodDecimals) 2 else 0

        return if (adjustedEnglishScore > adjustedGermanScore) {
            EnglishReceiptPatterns()
        } else {
            GermanReceiptPatterns() // Default to German
        }
    }

    /**
     * Extract date from text using locale-specific patterns.
     */
    private fun extractDate(lines: List<String>, patterns: ReceiptPatterns): Instant {
        for (line in lines) {
            val matchResult = patterns.datePattern.find(line)
            if (matchResult != null) {
                val dateStr = matchResult.groupValues[1]
                for (formatPattern in patterns.dateFormats) {
                    try {
                        val formatter = DateTimeFormatter.ofPattern(formatPattern, Locale.forLanguageTag(patterns.localeCode))
                        val localDate = LocalDate.parse(dateStr, formatter)
                        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    } catch (e: Exception) {
                        // Try next pattern
                    }
                }
            }
        }

        // Default to current time if no date found
        return Instant.now()
    }

    private fun getDecimalSeparator(patterns: ReceiptPatterns): Char {
        return when (patterns) {
            is GermanReceiptPatterns -> GermanReceiptPatterns.DECIMAL_SEPARATOR
            is EnglishReceiptPatterns -> EnglishReceiptPatterns.DECIMAL_SEPARATOR
            else -> '.'
        }
    }

    private fun getThousandsSeparator(patterns: ReceiptPatterns): Char {
        return when (patterns) {
            is GermanReceiptPatterns -> GermanReceiptPatterns.THOUSANDS_SEPARATOR
            is EnglishReceiptPatterns -> EnglishReceiptPatterns.THOUSANDS_SEPARATOR
            else -> ','
        }
    }

    companion object {
        /**
         * Create parser with specific locale.
         */
        fun forLocale(localeCode: String): RegexMlkitParser {
            val patterns = when (localeCode.lowercase()) {
                "de", "de_de", "german" -> GermanReceiptPatterns()
                "en", "en_us", "en_gb", "english" -> EnglishReceiptPatterns()
                else -> GermanReceiptPatterns() // Default
            }
            return RegexMlkitParser(patterns)
        }

        /**
         * Create parser with auto-detection (default behavior).
         */
        fun withAutoDetection(): RegexMlkitParser {
            return RegexMlkitParser(null)
        }
    }
}

private enum class ItemType {
    TOTAL, ITEM, NET, GROSS, IGNORE
}

private data class ParsedItem(
    val name: String,
    val amount: Double,
    val type: ItemType
)