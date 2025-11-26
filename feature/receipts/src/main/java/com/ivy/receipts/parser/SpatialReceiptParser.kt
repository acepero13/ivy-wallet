package com.ivy.receipts.parser

import com.ivy.data.repository.CategoryRepository
import com.ivy.receipts.category.CategoryDetector
import com.ivy.receipts.category.CategoryInfo
import com.ivy.receipts.category.CompositeCategoryDetector
import com.ivy.receipts.ocr.OcrReceipt
import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.parser.locales.EnglishReceiptPatterns
import com.ivy.receipts.parser.locales.GermanReceiptPatterns
import com.ivy.receipts.parser.strategies.KeywordBasedStrategy
import com.ivy.receipts.parser.strategies.RepetitionStrategy
import com.ivy.receipts.parser.strategies.LargestAmountStrategy
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

/**
 * Strategy interface for handling different receipt orientations.
 */
interface OrientationStrategy {
    fun sortBlocksForRows(blocks: List<TextBlock>): List<TextBlock>
    fun groupIntoRows(sortedBlocks: List<TextBlock>): List<List<TextBlock>>
    fun sortBlocksWithinRow(row: List<TextBlock>): List<TextBlock>
}

/**
 * Strategy for portrait (vertical) receipts.
 */
class PortraitStrategy : OrientationStrategy {
    override fun sortBlocksForRows(blocks: List<TextBlock>): List<TextBlock> {
        return blocks.sortedBy { it.boundingBox!!.top }
    }

    override fun groupIntoRows(sortedBlocks: List<TextBlock>): List<List<TextBlock>> {
        if (sortedBlocks.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<TextBlock>>()
        var current = mutableListOf(sortedBlocks.first())

        for (i in 1 until sortedBlocks.size) {
            val prevY = sortedBlocks[i - 1].boundingBox!!.centerY()
            val curr = sortedBlocks[i]
            val currY = curr.boundingBox!!.centerY()

            // Increased threshold to group blocks on the same visual row
            if (abs(currY - prevY) < 100) {
                current.add(curr)
            } else {
                rows.add(current)
                current = mutableListOf(curr)
            }
        }

        rows.add(current)
        return rows
    }

    override fun sortBlocksWithinRow(row: List<TextBlock>): List<TextBlock> {
        // Sort left to right
        return row.sortedBy { it.boundingBox!!.left }
    }
}

/**
 * Strategy for landscape (horizontal) receipts.
 */
class LandscapeStrategy : OrientationStrategy {
    override fun sortBlocksForRows(blocks: List<TextBlock>): List<TextBlock> {
        // For landscape, columns become rows, so sort by X
        return blocks.sortedBy { it.boundingBox!!.left }
    }

    override fun groupIntoRows(sortedBlocks: List<TextBlock>): List<List<TextBlock>> {
        if (sortedBlocks.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<TextBlock>>()
        var current = mutableListOf(sortedBlocks.first())

        for (i in 1 until sortedBlocks.size) {
            val prevX = sortedBlocks[i - 1].boundingBox!!.centerX()
            val curr = sortedBlocks[i]
            val currX = curr.boundingBox!!.centerX()

            if (abs(currX - prevX) < 100) {
                current.add(curr)
            } else {
                rows.add(current)
                current = mutableListOf(curr)
            }
        }

        rows.add(current)
        return rows
    }

    override fun sortBlocksWithinRow(row: List<TextBlock>): List<TextBlock> {
        // For landscape, sort top to bottom within each column
        return row.sortedBy { it.boundingBox!!.top }
    }
}

class SpatialReceiptParser @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val patterns: ReceiptPatterns,
    private val categoryDetector: CategoryDetector
) : ReceiptParseable {

    private val amountExtractor = AmountExtractor()
    private val rowGrouper = RowGrouper()
    private val dateExtractor = DateExtractor()
    private val merchantExtractor = MerchantExtractor()

    // Initialize composite total finder with strategies in priority order
    private val compositeTotalFinder = CompositeTotalFinder(
        strategies = listOf(
            RepetitionStrategy(),      // Try repetition first - most reliable when applicable
            KeywordBasedStrategy(),    // Try keyword-based next - traditional approach
            LargestAmountStrategy()    // Fallback to largest amount in bottom portion
        )
    )

    private val fallbackExtractor = FallbackExtractor()

    override suspend fun parse(blocks: List<TextBlock>): OcrReceipt {
        val filteredBlocks = blocks.filter { it.boundingBox != null }

        // Detect orientation and select strategy
        val strategy = selectOrientationStrategy(filteredBlocks)
        println("=== IMAGE ORIENTATION ===")
        println("Detected orientation: ${strategy.javaClass.simpleName}")

        val rows = rowGrouper.group(filteredBlocks, strategy)
        val allLines = blocks.flatMap { it.lines.map { l -> l.text } }

        // Try composite total finder with multiple strategies
        val total = compositeTotalFinder.findTotal(
            blocks = filteredBlocks,
            rows = rows,
            patterns = patterns
        ) { text ->
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
     * Select orientation strategy based on image dimensions.
     */
    private fun selectOrientationStrategy(blocks: List<TextBlock>): OrientationStrategy {
        if (blocks.isEmpty()) return PortraitStrategy()

        val allBounds = blocks.mapNotNull { it.boundingBox }
        if (allBounds.isEmpty()) return PortraitStrategy()

        val imageWidth = allBounds.maxOf { it.right } - allBounds.minOf { it.left }
        val imageHeight = allBounds.maxOf { it.bottom } - allBounds.minOf { it.top }
        val imageRatio = imageWidth.toDouble() / imageHeight

        println("  Image dimensions: ${imageWidth}x${imageHeight}, ratio=$imageRatio")

        // Image is landscape if width > height
        return if (imageRatio > 1.2) {
            LandscapeStrategy()
        } else {
            PortraitStrategy()
        }
    }

    /**
     * Groups OCR blocks into rows using the appropriate orientation strategy.
     */
    class RowGrouper {
        fun group(blocks: List<TextBlock>, strategy: OrientationStrategy): List<List<TextBlock>> {
            if (blocks.isEmpty()) return emptyList()

            val sortedBlocks = strategy.sortBlocksForRows(blocks)
            val rows = strategy.groupIntoRows(sortedBlocks)

            // Sort blocks within each row
            return rows.map { row -> strategy.sortBlocksWithinRow(row) }
        }
    }

    /**
     * Finds totals using spatial information.
     */
    inner class SpatialTotalFinder {

        fun find(
            rows: List<List<TextBlock>>,
            patterns: ReceiptPatterns,
            strategy: OrientationStrategy,
            extractAmount: (String) -> Double
        ): Double? {

            println("=== SPATIAL TOTAL FINDER DEBUG ===")
            println("Total rows: ${rows.size}")
            println("Strategy: ${strategy.javaClass.simpleName}")

            val totalRow = rows.find { row ->
                row.any { block -> patterns.isTotalKeyword(block.text) }
            }

            if (totalRow == null) {
                println("No row with total keyword found!")
                println("Attempting fallback: searching for largest amount in bottom portion of receipt")
                return findLargestAmountInBottomPortion(rows, extractAmount)
            }

            println("Found total row with ${totalRow.size} blocks:")
            totalRow.forEach { block ->
                println("  - '${block.text}' | isTotalKeyword=${patterns.isTotalKeyword(block.text)}")
            }

            // 1. Find in same row
            val rowAmount = findAmountInRow(totalRow, extractAmount)
            println("Amount in same row: $rowAmount")
            if (rowAmount > 0) return rowAmount

            // 2. Orientation-aware search:
            // - Portrait: Look below first (higher row indices) - totals usually after keyword
            // - Landscape: Look above first (lower row indices) - "above" = LEFT on rotated receipt
            val isLandscape = strategy is LandscapeStrategy

            if (isLandscape) {
                // For landscape: search left (above in row indices) first
                println("Landscape mode: searching left/above first")
                val aboveAmount = findAmountAboveRow(rows, totalRow, patterns, extractAmount)
                println("Amount left/above row: $aboveAmount")
                if (aboveAmount > 0) return aboveAmount

                val nearAmount = findAmountNearRow(rows, totalRow, patterns, extractAmount)
                println("Amount right/below row: $nearAmount")
                return nearAmount.takeIf { it > 0 }
            } else {
                // For portrait: search below (after keyword) first
                println("Portrait mode: searching below first")
                val nearAmount = findAmountNearRow(rows, totalRow, patterns, extractAmount)
                println("Amount near row: $nearAmount")
                if (nearAmount > 0) return nearAmount

                val aboveAmount = findAmountAboveRow(rows, totalRow, patterns, extractAmount)
                println("Amount above row: $aboveAmount")
                return aboveAmount.takeIf { it > 0 }
            }
        }

        private fun findAmountInRow(
            row: List<TextBlock>,
            extractAmount: (String) -> Double
        ): Double {
            println("  Checking blocks in row (reversed):")
            // First pass: look for amounts in the same row, starting from right
            for (block in row.asReversed()) {
                val amount = extractAmount(block.text)
                println("    '${block.text}' -> amount=$amount")
                if (amount > 0) return amount
            }

            // Second pass: check if keyword and amount might be in the same block
            // e.g., "Betrag 51,54" or "Total: 27.66" or "SunLo2151,54"
            for (block in row) {
                val text = block.text
                println("    Trying to extract from: '$text'")

                // Try extracting amount directly from the full text first
                val directAmount = extractAmount(text)
                if (directAmount > 0) {
                    println("    Found direct amount in block: $directAmount")
                    return directAmount
                }

                // If that fails, try cleaning up the text
                // Remove all known keyword variations
                val cleaned = text.replace(Regex("(?i)(summe|sum|sunlo|gesamt|total|betrag|brutto|zahlung|payment|zu zahlen|endsumme)"), "")
                    .replace(Regex("[A-Za-z]+"), "") // Remove remaining letters
                    .trim()

                if (cleaned.isNotEmpty()) {
                    val amount = extractAmount(cleaned)
                    if (amount > 0) {
                        println("    Found amount in keyword block: '$text' -> cleaned='$cleaned' -> amount=$amount")
                        return amount
                    }
                }
            }
            return 0.0
        }

        private fun findAmountAboveRow(
            rows: List<List<TextBlock>>,
            keywordRow: List<TextBlock>,
            patterns: ReceiptPatterns,
            extractAmount: (String) -> Double
        ): Double {
            val keywordIndex = rows.indexOf(keywordRow)
            println("    findAmountAboveRow: keywordIndex=$keywordIndex, total rows=${rows.size}")

            if (keywordIndex <= 0) {
                println("    No rows above keyword (keywordIndex=$keywordIndex)")
                return 0.0  // No rows above
            }

            val keywordBlock = keywordRow.firstOrNull { patterns.isTotalKeyword(it.text) }
            if (keywordBlock == null) {
                println("    No keyword block found in row")
                return 0.0
            }

            val keywordY = keywordBlock.boundingBox?.centerY()
            println("    Keyword block '${keywordBlock.text}' at Y:$keywordY")

            // Collect all amounts found in rows above
            data class AmountCandidate(val rowIndex: Int, val amount: Double, val xRight: Int, val yDistance: Int)
            val candidates = mutableListOf<AmountCandidate>()

            val startRow = maxOf(0, keywordIndex - 10)
            println("    Searching rows $startRow until $keywordIndex")

            // Look in rows above the keyword row (up to 10 rows up)
            for (i in startRow until keywordIndex) {
                val row = rows[i]

                // Skip rows containing "Zw-Summe" (interim sum) or similar
                val rowText = row.joinToString(" ") { it.text }
                if (rowText.matches(Regex("(?i).*(zw-summe|zwischensumme|subtotal).*"))) {
                    println("    Skipping row $i: contains interim sum indicator")
                    continue
                }

                for (block in row) {
                    val bbox = block.boundingBox ?: continue
                    val left = bbox.left
                    val right = bbox.right
                    val blockY = bbox.centerY()
                    val text = block.text.trim()

                    // Calculate Y-distance from keyword
                    val yDistance = if (keywordY != null) kotlin.math.abs(blockY - keywordY) else Int.MAX_VALUE

                    val amount = extractAmount(text)
                    if (amount > 0) {
                        println("    Found candidate in row $i: '$text' -> $amount at X:$left-$right, Y:$blockY (yDistance=$yDistance)")
                        candidates.add(AmountCandidate(i, amount, right, yDistance))
                    }
                }
            }

            if (candidates.isEmpty()) {
                println("    No candidates found above keyword")
                return 0.0
            }

            // Strategy: Different prioritization for landscape vs portrait
            // - Landscape: Prioritize by rightmost X (amounts are to the left/above in row indices)
            // - Portrait: Prioritize by smallest Y-distance (horizontal alignment)

            val isLandscape = keywordBlock.boundingBox?.let { bbox ->
                val allBounds = rows.flatten().mapNotNull { it.boundingBox }
                val imageWidth = allBounds.maxOf { it.right } - allBounds.minOf { it.left }
                val imageHeight = allBounds.maxOf { it.bottom } - allBounds.minOf { it.top }
                imageWidth.toDouble() / imageHeight > 1.2
            } ?: false

            val selected = if (isLandscape) {
                // For landscape: prioritize rightmost amounts (they appear on left side of rotated receipt)
                val rightmostX = candidates.maxOf { it.xRight }
                val rightmostCandidates = candidates.filter { it.xRight >= rightmostX - 300 }
                // Among rightmost, pick the one closest to keyword row
                rightmostCandidates.maxByOrNull { it.rowIndex }!!
            } else {
                // For portrait: prioritize horizontal alignment (smallest Y-distance)
                candidates.minByOrNull { it.yDistance }!!
            }

            println("    Selecting amount (${if (isLandscape) "landscape: rightmost" else "portrait: Y-aligned"}): ${selected.amount} (yDistance=${selected.yDistance}px, xRight=${selected.xRight})")
            return selected.amount
        }

        private fun findAmountNearRow(
            rows: List<List<TextBlock>>,
            keywordRow: List<TextBlock>,
            patterns: ReceiptPatterns,
            extractAmount: (String) -> Double
        ): Double {

            val keywordIndex = rows.indexOf(keywordRow)
            println("    findAmountNearRow: keywordIndex=$keywordIndex, total rows=${rows.size}")

            val keywordBlock = keywordRow.firstOrNull { patterns.isTotalKeyword(it.text) }
            if (keywordBlock == null) {
                println("    No keyword block found in row")
                return 0.0
            }

            println("    Keyword block '${keywordBlock.text}'")

            // Collect all amounts found in the next several rows
            // Store as (rowIndex, amount, xPosition) to find the rightmost amounts
            data class AmountCandidate(val rowIndex: Int, val amount: Double, val xRight: Int)

            val candidates = mutableListOf<AmountCandidate>()
            val negatives = mutableListOf<Pair<Int, Double>>()

            // Look in rows below the keyword row (up to 20 rows down to handle landscape receipts)
            val searchEnd = minOf(keywordIndex + 20, rows.size)
            println("    Searching rows ${keywordIndex + 1} until $searchEnd")

            for (i in keywordIndex + 1 until searchEnd) {
                for (block in rows[i]) {
                    val left = block.boundingBox?.left ?: continue
                    val right = block.boundingBox?.right ?: continue
                    val text = block.text.trim()

                    // Negative value (payment)
                    if (text.startsWith("-")) {
                        val neg = amountExtractor.extractNegativeAmount(text)
                        if (neg > 10) {
                            println("    Found negative amount in row $i: '$text' -> $neg")
                            negatives.add(i to neg)
                        }
                        continue
                    }

                    // Positive candidate - NO ALIGNMENT CHECK, collect ALL amounts
                    val amount = extractAmount(text)
                    if (amount > 0) {
                        println("    Found candidate in row $i: '$text' -> $amount at X:$left-$right")
                        candidates.add(AmountCandidate(i, amount, right))
                    }
                }
            }

            if (candidates.isEmpty()) {
                println("    No candidates found")
                return 0.0
            }

            // Strategy 1: Match positive with later negative (payment confirmation)
            for (candidate in candidates.asReversed()) {
                if (negatives.any { (negIdx, negAmount) ->
                        negIdx > candidate.rowIndex && abs(negAmount - candidate.amount) < 0.01
                    }) {
                    println("    Matched positive ${candidate.amount} with negative (payment confirmation)")
                    return candidate.amount
                }
            }

            // Strategy 2: Find the rightmost amount column (where totals typically appear)
            // Group candidates by their X position (with tolerance)
            val rightmostX = candidates.maxOf { it.xRight }
            println("    Rightmost X position: $rightmostX")

            // Find all amounts within 300px of the rightmost edge (tolerance for OCR variations)
            val rightmostAmounts = candidates.filter { it.xRight >= rightmostX - 300 }

            if (rightmostAmounts.isNotEmpty()) {
                // Among the rightmost amounts, prefer the one closest to the keyword row
                val closest = rightmostAmounts.minByOrNull { it.rowIndex }!!
                println("    Selecting rightmost amount: ${closest.amount} from row ${closest.rowIndex}")
                return closest.amount
            }

            // Strategy 3: Fallback - return the first candidate (closest to keyword row)
            val fallback = candidates.first()
            println("    Fallback: returning first candidate ${fallback.amount} from row ${fallback.rowIndex}")
            return fallback.amount
        }

        /**
         * Fallback strategy when no total keyword is found.
         * Searches for the largest amount in the bottom 30% of the receipt (where totals typically appear).
         * Filters out small amounts (< 5.0) to avoid item prices.
         */
        private fun findLargestAmountInBottomPortion(
            rows: List<List<TextBlock>>,
            extractAmount: (String) -> Double
        ): Double? {
            if (rows.isEmpty()) return null

            // Focus on bottom 30% of rows (where totals typically are)
            val bottomStartIndex = (rows.size * 0.7).toInt()
            val bottomRows = rows.subList(bottomStartIndex, rows.size)

            println("  Searching bottom ${bottomRows.size} rows (from index $bottomStartIndex)")

            data class AmountCandidate(val amount: Double, val rowIndex: Int, val text: String)
            val candidates = mutableListOf<AmountCandidate>()

            bottomRows.forEachIndexed { relativeIndex, row ->
                val absoluteIndex = bottomStartIndex + relativeIndex
                for (block in row) {
                    val amount = extractAmount(block.text)
                    // Filter out small amounts (likely item prices, not totals)
                    if (amount >= 5.0) {
                        println("    Found candidate in row $absoluteIndex: '${block.text}' -> $amount")
                        candidates.add(AmountCandidate(amount, absoluteIndex, block.text))
                    }
                }
            }

            if (candidates.isEmpty()) {
                println("  No candidates found in bottom portion")
                return null
            }

            // Return the largest amount (most likely to be the total)
            val largest = candidates.maxByOrNull { it.amount }!!
            println("  Selected largest amount: ${largest.amount} from row ${largest.rowIndex} ('${largest.text}')")
            return largest.amount
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
            // Don't extract negative amounts (these are usually change/return amounts)
            val trimmed = text.trim()
            if (trimmed.startsWith("-")) {
                return 0.0
            }

            // Filter out dates that look like amounts
            // Reject patterns like: "25.11.2025", "25.11. 20", "25.11.", ": 25.11", "25.11.20"
            // Key characteristic: 2 dots with 2 digits on each side (day.month pattern)
            if (trimmed.contains(Regex("\\d{1,2}\\.\\d{1,2}\\."))) {
                return 0.0
            }

            // Filter out tax percentages
            // Reject patterns like: "A:19,00%", "B 19,O% Net to", "19.0%", or any text containing %
            // Also reject patterns like "A:19,00" (tax rate prefix format)
            if (trimmed.contains("%") || trimmed.matches(Regex(".*[A-Z]:\\s*\\d+[,.].*"))) {
                return 0.0
            }

            // More flexible regex that handles:
            // - "51,54 ER" (amount with suffix)
            // - "84,25 EUR" (amount with currency)
            // - "51.54" (amount with different decimal separator)
            // - "1.234,56" (German format with thousand separator)
            // - "84,25" (simple format)
            val regex = Regex("[€$£]?\\s*\\d{1,3}(?:[.,]\\d{3})*[,.]\\d{2}(?:\\s*[A-Z]{1,4})?\\s*[€$£]?")
            val match = regex.find(trimmed) ?: return 0.0

            // Extract just the numeric part, removing currency symbols and extra text
            val raw = match.value
                .replace(Regex("[€$£\\s]"), "")
                .replace(Regex("[A-Z]+$"), "") // Remove trailing letters like "ER" or "EUR"
                .trim()

            // Double-check: if it contains date patterns, reject it
            // Reject if it has 2+ dots (e.g., "25.11.20" would become "2511.20" after extraction)
            if (raw.count { it == '.' } >= 2) {
                return 0.0
            }

            // Reject if it looks like a year (4 consecutive digits)
            if (raw.contains(Regex("\\d{4}"))) {
                return 0.0
            }

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