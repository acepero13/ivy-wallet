package com.ivy.receipts.parser

import android.graphics.Rect
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
 * Spatial-aware receipt parser that uses bounding box coordinates
 * to understand receipt layout and associate labels with amounts.
 *
 * This solves the problem of text-only parsing where "SUMME EUR" and "27,66"
 * appear on different lines but are spatially aligned or nearby.
 */
class SpatialReceiptParser(
    private val patterns: ReceiptPatterns = GermanReceiptPatterns()
) : ReceiptParseable {

    override fun parse(blocks: List<TextBlock>): OcrReceipt {
        // Sort blocks by vertical position (top to bottom)
        val sortedBlocks = blocks
            .filter { it.boundingBox != null }
            .sortedBy { it.boundingBox!!.top }

        // Group blocks by horizontal rows (same Y-coordinate range)
        val rows = groupIntoRows(sortedBlocks)

        // Find the total keyword row
        val totalRow = rows.find { row ->
            row.any { block -> patterns.isTotalKeyword(block.text) }
        }

        var total = 0.0
        var date = Instant.now()

        if (totalRow != null) {
            // Look for amount in the same row (right side)
            total = findAmountInRow(totalRow, patterns)

            // If not found in same row, look in nearby rows (spatial proximity)
            if (total == 0.0) {
                total = findAmountNearRow(totalRow, rows, patterns)
            }
        }

        // Fallback to text-only parsing if spatial approach fails
        if (total == 0.0) {
            val allLines = blocks.flatMap { block ->
                block.lines.map { it.text }
            }
            date = extractDate(allLines, patterns)
            total = extractTotalFromLines(allLines, patterns)
        } else {
            val allLines = blocks.flatMap { block ->
                block.lines.map { it.text }
            }
            date = extractDate(allLines, patterns)
        }

        return OcrReceipt(
            total = total,
            currency = patterns.defaultCurrency,
            date = date,
            categories = emptyList(),
        )
    }

    /**
     * Group text blocks into horizontal rows based on Y-coordinate proximity.
     */
    private fun groupIntoRows(blocks: List<TextBlock>): List<List<TextBlock>> {
        if (blocks.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<TextBlock>>()
        var currentRow = mutableListOf(blocks[0])

        for (i in 1 until blocks.size) {
            val prevBlock = blocks[i - 1]
            val currentBlock = blocks[i]

            val prevY = prevBlock.boundingBox!!.centerY()
            val currentY = currentBlock.boundingBox!!.centerY()

            // If blocks are within 20 pixels vertically, consider them same row
            if (abs(currentY - prevY) < 20) {
                currentRow.add(currentBlock)
            } else {
                // Start new row
                rows.add(currentRow)
                currentRow = mutableListOf(currentBlock)
            }
        }
        rows.add(currentRow)

        // Sort each row by X-coordinate (left to right)
        return rows.map { row ->
            row.sortedBy { it.boundingBox!!.left }
        }
    }

    /**
     * Check if text contains a total keyword.
     */


    /**
     * Find an amount in the same row as the keyword (to the right).
     */
    private fun findAmountInRow(row: List<TextBlock>, patterns: ReceiptPatterns): Double {
        // Find the rightmost number in the row
        for (block in row.reversed()) {
            val amount = extractStandaloneAmount(block.text, patterns)
            if (amount > 0) {
                return amount
            }
        }
        return 0.0
    }

    /**
     * Find an amount in nearby rows using spatial proximity.
     */
    private fun findAmountNearRow(
        keywordRow: List<TextBlock>,
        allRows: List<List<TextBlock>>,
        patterns: ReceiptPatterns
    ): Double {
        val keywordRowIndex = allRows.indexOf(keywordRow)
        if (keywordRowIndex == -1) return 0.0

        // Get X-coordinate of keyword (to find amounts aligned with it)
        val keywordBlock = keywordRow.find { patterns.isTotalKeyword(it.text) }
        val keywordRight = keywordBlock?.boundingBox?.right ?: 0

        // Look in next few rows for amounts spatially aligned with keyword
        val candidates = mutableListOf<Pair<Int, Double>>()
        val negatives = mutableListOf<Pair<Int, Double>>()

        for (i in (keywordRowIndex + 1) until minOf(keywordRowIndex + 10, allRows.size)) {
            val row = allRows[i]

            // Look for amounts in this row
            for (block in row) {
                val blockLeft = block.boundingBox?.left ?: 0

                // Check if block is in similar horizontal position (column alignment)
                // or on the right side of the receipt (typical for amounts)
                if (blockLeft >= keywordRight - 100) { // Allow 100px tolerance
                    val text = block.text.trim()

                    // Check for negative (payment)
                    if (text.startsWith("-")) {
                        val negAmount = text.replace("-", "")
                            .replace(Regex("[€$£\\s]"), "")
                            .replace(",", ".")
                            .toDoubleOrNull() ?: 0.0
                        if (negAmount > 10.0) {
                            negatives.add(i to negAmount)
                        }
                    }

                    // Check for positive amount
                    val amount = extractStandaloneAmount(text, patterns)
                    if (amount > 0) {
                        candidates.add(i to amount)
                    }
                }
            }
        }

        // Use payment matching algorithm
        for ((posIndex, posAmount) in candidates.reversed()) {
            val hasMatchingNegative = negatives.any {
                it.first > posIndex && abs(it.second - posAmount) < 0.01
            }
            if (hasMatchingNegative) {
                return posAmount
            }
        }

        // Fallback: take the first significant amount found
        return candidates.firstOrNull()?.second ?: 0.0
    }

    /**
     * Extract standalone amount from text.
     */
    private fun extractStandaloneAmount(line: String, patterns: ReceiptPatterns): Double {
        val cleanLine = line.trim()

        // Match amounts with optional currency symbols
        val amountPattern = Regex("[€$£]?\\s*-?\\d+[,.]\\d{2}\\s*[€$£]?")
        val match = amountPattern.find(cleanLine) ?: return 0.0

        val numericPart = match.value.replace(Regex("[€$£\\s-]"), "")
        return numericPart.toMoney(
            decimalSeparator = getDecimalSeparator(patterns),
            thousandsSeparator = getThousandsSeparator(patterns)
        ).takeIf { it > 0 } ?: 0.0
    }

    /**
     * Fallback: Extract total from lines using text-only approach.
     */
    private fun extractTotalFromLines(lines: List<String>, patterns: ReceiptPatterns): Double {
        // Try to match total pattern first
        for (line in lines) {
            patterns.totalPattern.find(line)?.let { match ->
                val amount = extractAmountFromMatch(match, patterns)
                if (amount > 0) return amount
            }
        }
        return 0.0
    }

    private fun extractAmountFromMatch(match: MatchResult, patterns: ReceiptPatterns): Double {
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
     * Extract date from text lines.
     */
    private fun extractDate(lines: List<String>, patterns: ReceiptPatterns): Instant {
        for (line in lines) {
            val matchResult = patterns.datePattern.find(line)
            if (matchResult != null) {
                val dateStr = matchResult.groupValues[1]
                for (formatPattern in patterns.dateFormats) {
                    try {
                        val formatter = DateTimeFormatter.ofPattern(
                            formatPattern,
                            Locale.forLanguageTag(patterns.localeCode)
                        )
                        val localDate = LocalDate.parse(dateStr, formatter)
                        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    } catch (e: Exception) {
                        // Try next pattern
                    }
                }
            }
        }
        return Instant.now()
    }

    private fun getDecimalSeparator(patterns: ReceiptPatterns): Char {
        return when (patterns) {
            is GermanReceiptPatterns ->
                GermanReceiptPatterns.DECIMAL_SEPARATOR

            is EnglishReceiptPatterns ->
                EnglishReceiptPatterns.DECIMAL_SEPARATOR

            else -> '.'
        }
    }

    private fun getThousandsSeparator(patterns: ReceiptPatterns): Char {
        return when (patterns) {
            is GermanReceiptPatterns ->
                GermanReceiptPatterns.THOUSANDS_SEPARATOR

            is EnglishReceiptPatterns ->
                EnglishReceiptPatterns.THOUSANDS_SEPARATOR

            else -> ','
        }
    }
}
