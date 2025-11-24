package com.ivy.receipts.parser

import com.ivy.data.repository.CategoryRepository
import com.ivy.receipts.category.CategoryDetector
import com.ivy.receipts.category.CategoryInfo
import com.ivy.receipts.category.CompositeCategoryDetector
import com.ivy.receipts.ocr.OcrReceipt
import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.parser.locales.EnglishReceiptPatterns
import com.ivy.receipts.parser.locales.GermanReceiptPatterns
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

/**
 * Spatial-aware receipt parser that uses bounding box coordinates
 * to understand receipt layout and associate labels with amounts.
 *
 * This solves the problem of text-only parsing where "SUMME EUR" and "27,66"
 * appear on different lines but are spatially aligned or nearby.
 */

class SpatialReceiptParser @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val patterns: ReceiptPatterns,
    private val categoryDetector: CategoryDetector
) : ReceiptParseable {

    private val amountExtractor = AmountExtractor()
    private val rowGrouper = RowGrouper()
    private val spatialTotalFinder = SpatialTotalFinder()
    private val fallbackExtractor = FallbackExtractor()
    private val dateExtractor = DateExtractor()
    private val merchantExtractor = MerchantExtractor()

    override suspend fun parse(blocks: List<TextBlock>): OcrReceipt {
        val sortedBlocks = blocks
            .filter { it.boundingBox != null }
            .sortedBy { it.boundingBox!!.top }

        val rows = rowGrouper.group(sortedBlocks)
        val allLines = blocks.flatMap { it.lines.map { l -> l.text } }

        val total =
            spatialTotalFinder.find(rows, patterns) { text ->
                amountExtractor.extractStandaloneAmount(text, patterns)
            } ?: fallbackExtractor.extractTotal(allLines, patterns, amountExtractor)

        val date = dateExtractor.extract(allLines, patterns)

        // Detect category from receipt
        val categoryId = detectCategory(blocks)

        // Extract merchant name
        val merchantName = merchantExtractor.extractMerchantName(blocks)

        return OcrReceipt(
            total = BigDecimal.valueOf(total),
            currency = patterns.defaultCurrency,
            date = date,
            categoryId = categoryId,
            merchantName = merchantName
        )
    }

    /**
     * Groups OCR blocks into horizontal rows.
     */
    class RowGrouper {
        fun group(blocks: List<TextBlock>): List<List<TextBlock>> {
            if (blocks.isEmpty()) return emptyList()

            val rows = mutableListOf<MutableList<TextBlock>>()
            var current = mutableListOf(blocks.first())

            for (i in 1 until blocks.size) {
                val prevY = blocks[i - 1].boundingBox!!.centerY()
                val curr = blocks[i]
                val currY = curr.boundingBox!!.centerY()

                // Increased threshold from 20 to 100 to group blocks that are on the same visual row
                // even if they have some vertical offset (like "Betrag" and "51,54 ER")
                if (abs(currY - prevY) < 100) {
                    current.add(curr)
                } else {
                    rows.add(current)
                    current = mutableListOf(curr)
                }
            }

            rows.add(current)
            return rows.map { r -> r.sortedBy { it.boundingBox!!.left } }
        }
    }

    /**
     * Finds totals using spatial information.
     */
    inner class SpatialTotalFinder {

        fun find(
            rows: List<List<TextBlock>>,
            patterns: ReceiptPatterns,
            extractAmount: (String) -> Double
        ): Double? {

            println("=== SPATIAL TOTAL FINDER DEBUG ===")
            println("Total rows: ${rows.size}")

            val totalRow = rows.find { row ->
                row.any { block -> patterns.isTotalKeyword(block.text) }
            }

            if (totalRow == null) {
                println("No row with total keyword found!")
                return null
            }

            println("Found total row with ${totalRow.size} blocks:")
            totalRow.forEach { block ->
                println("  - '${block.text}' | isTotalKeyword=${patterns.isTotalKeyword(block.text)}")
            }

            // 1. Find in same row
            val rowAmount = findAmountInRow(totalRow, extractAmount)
            println("Amount in same row: $rowAmount")
            if (rowAmount > 0) return rowAmount

            // 2. Look in rows below
            val nearAmount = findAmountNearRow(rows, totalRow, patterns, extractAmount)
            println("Amount near row: $nearAmount")
            return nearAmount.takeIf { it > 0 }
        }

        private fun findAmountInRow(
            row: List<TextBlock>,
            extractAmount: (String) -> Double
        ): Double {
            println("  Checking blocks in row (reversed):")
            for (block in row.asReversed()) {
                val amount = extractAmount(block.text)
                println("    '${block.text}' -> amount=$amount")
                if (amount > 0) return amount
            }
            return 0.0
        }

        private fun findAmountNearRow(
            rows: List<List<TextBlock>>,
            keywordRow: List<TextBlock>,
            patterns: ReceiptPatterns,
            extractAmount: (String) -> Double
        ): Double {

            val keywordIndex = rows.indexOf(keywordRow)
            val keywordBlock = keywordRow.firstOrNull { patterns.isTotalKeyword(it.text) }
                ?: return 0.0

            val keywordRight = keywordBlock.boundingBox!!.right

            val candidates = mutableListOf<Pair<Int, Double>>()
            val negatives = mutableListOf<Pair<Int, Double>>()

            for (i in keywordIndex + 1 until minOf(keywordIndex + 10, rows.size)) {
                for (block in rows[i]) {
                    val left = block.boundingBox?.left ?: continue
                    if (left < keywordRight - 100) continue

                    val text = block.text.trim()

                    // Negative value (payment)
                    if (text.startsWith("-")) {
                        val neg = amountExtractor.extractNegativeAmount(text)
                        if (neg > 10) negatives.add(i to neg)
                    }

                    // Positive candidate
                    val amount = extractAmount(text)
                    if (amount > 0) candidates.add(i to amount)
                }
            }

            // Match positive with later negative (payment)
            for ((posIndex, posAmount) in candidates.asReversed()) {
                if (negatives.any { (negIdx, negAmount) ->
                        negIdx > posIndex && abs(negAmount - posAmount) < 0.01
                    }) {
                    return posAmount
                }
            }

            return candidates.firstOrNull()?.second ?: 0.0
        }
    }

    /**
     * Extracts totals via text-based fallback logic.
     */
    class FallbackExtractor {
        fun extractTotal(
            lines: List<String>,
            patterns: ReceiptPatterns,
            amountExtractor: AmountExtractor
        ): Double {
            for (line in lines) {
                val match = patterns.totalPattern.find(line)
                if (match != null) {
                    val amount = extractAmountFromMatch(match, patterns, amountExtractor)
                    if (amount > 0) return amount
                }
            }
            return 0.0
        }

        private fun extractAmountFromMatch(
            match: MatchResult,
            patterns: ReceiptPatterns,
            amountExtractor: AmountExtractor
        ): Double {
            for (i in 1 until match.groupValues.size) {
                val v = match.groupValues[i]
                if (v.matches(Regex("\\d+[,.]\\d+"))) {
                    return amountExtractor.parseMoney(v, patterns)
                }
            }
            return 0.0
        }
    }

    /**
     * Extracts dates from lines.
     */
    class DateExtractor {
        fun extract(lines: List<String>, patterns: ReceiptPatterns): Instant {
            for (line in lines) {
                val match = patterns.datePattern.find(line) ?: continue
                val raw = match.groupValues[1]

                for (fmt in patterns.dateFormats) {
                    try {
                        val formatter = DateTimeFormatter.ofPattern(
                            fmt, Locale.forLanguageTag(patterns.localeCode)
                        )
                        return LocalDate.parse(raw, formatter)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                    } catch (_: Exception) {
                    }
                }
            }
            return Instant.now()
        }
    }

    /**
     * Extracts amounts, handles decimals & national formatting.
     */
    class AmountExtractor {

        fun extractStandaloneAmount(text: String, patterns: ReceiptPatterns): Double {
            val regex = Regex("[€$£]?\\s*-?\\d+[,.]\\d{2}\\s*[€$£]?")
            val match = regex.find(text.trim()) ?: return 0.0

            val raw = match.value.replace(Regex("[€$£\\s-]"), "")
            return parseMoney(raw, patterns)
        }

        fun extractNegativeAmount(text: String): Double =
            text.replace("-", "")
                .replace(Regex("[€$£\\s]"), "")
                .replace(",", ".")
                .toDoubleOrNull() ?: 0.0

        fun parseMoney(raw: String, patterns: ReceiptPatterns): Double =
            raw.toMoney(
                decimalSeparator = patterns.decimalSeparator(),
                thousandsSeparator = patterns.thousandsSeparator()
            )
    }

    /**
     * Detect category from receipt using available categories.
     */
    private suspend fun detectCategory(blocks: List<TextBlock>) = try {
        val availableCategories = categoryRepository.findAll().map {
            CategoryInfo(id = it.id, name = it.name.value)
        }
        categoryDetector.detectCategory(blocks, availableCategories)
    } catch (e: Exception) {
        println("Category detection failed: ${e.message}")
        null
    }
}

// ------------------- ReceiptPatterns extensions -------------------

fun ReceiptPatterns.decimalSeparator(): Char = when (this) {
    is GermanReceiptPatterns -> GermanReceiptPatterns.DECIMAL_SEPARATOR
    is EnglishReceiptPatterns -> EnglishReceiptPatterns.DECIMAL_SEPARATOR
    else -> '.'
}

fun ReceiptPatterns.thousandsSeparator(): Char = when (this) {
    is GermanReceiptPatterns -> GermanReceiptPatterns.THOUSANDS_SEPARATOR
    is EnglishReceiptPatterns -> EnglishReceiptPatterns.THOUSANDS_SEPARATOR
    else -> ','
}