package com.ivy.receipts.parser.strategies

import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.parser.ReceiptPatterns
import com.ivy.receipts.parser.TotalFinderStrategy
import kotlin.math.abs

/**
 * Strategy that finds totals by looking for keyword indicators like "Total", "Summe", "Betrag", etc.
 * Uses spatial analysis to find amounts near these keywords.
 *
 * This is the most reliable strategy when keywords are properly recognized by OCR.
 */
class KeywordBasedStrategy : TotalFinderStrategy {
    override val name = "KeywordBasedStrategy"

    override fun findTotal(
        blocks: List<TextBlock>,
        rows: List<List<TextBlock>>,
        patterns: ReceiptPatterns,
        extractAmount: (String) -> Double
    ): Double? {
        println("  [KeywordBasedStrategy] Looking for total keywords...")

        // Find row containing total keyword
        val totalRow = rows.find { row ->
            row.any { block -> patterns.isTotalKeyword(block.text) }
        }

        if (totalRow == null) {
            println("  [KeywordBasedStrategy] No row with total keyword found")
            return null
        }

        val keywordBlock = totalRow.firstOrNull { patterns.isTotalKeyword(it.text) }
        if (keywordBlock == null) {
            println("  [KeywordBasedStrategy] No keyword block found in row")
            return null
        }

        println("  [KeywordBasedStrategy] Found keyword: '${keywordBlock.text}'")

        // Detect orientation
        val isLandscape = detectLandscape(rows)
        println("  [KeywordBasedStrategy] Orientation: ${if (isLandscape) "LANDSCAPE" else "PORTRAIT"}")

        // 1. Try to find amount in same row
        val rowAmount = findAmountInRow(totalRow, extractAmount)
        if (rowAmount > 0) {
            println("  [KeywordBasedStrategy] Found amount in same row: $rowAmount")
            return rowAmount
        }

        // 2. Search above/below based on orientation
        val keywordIndex = rows.indexOf(totalRow)
        if (isLandscape) {
            // For landscape: search left (above in row indices)
            return findAmountAboveRow(rows, keywordIndex, keywordBlock, patterns, extractAmount, isLandscape)
        } else {
            // For portrait: search below (after keyword)
            return findAmountNearRow(rows, keywordIndex, patterns, extractAmount)
        }
    }

    private fun detectLandscape(rows: List<List<TextBlock>>): Boolean {
        val allBounds = rows.flatten().mapNotNull { it.boundingBox }
        if (allBounds.isEmpty()) return false

        val imageWidth = allBounds.maxOf { it.right } - allBounds.minOf { it.left }
        val imageHeight = allBounds.maxOf { it.bottom } - allBounds.minOf { it.top }
        return imageWidth.toDouble() / imageHeight > 1.2
    }

    private fun findAmountInRow(
        row: List<TextBlock>,
        extractAmount: (String) -> Double
    ): Double {
        // Look for amounts in the same row, starting from right
        for (block in row.asReversed()) {
            val amount = extractAmount(block.text)
            if (amount > 0) return amount
        }

        // Check if keyword and amount in same block
        for (block in row) {
            val parts = block.text.split(Regex("\\s+"))
            for (part in parts) {
                val amount = extractAmount(part)
                if (amount > 0) return amount
            }
        }

        return 0.0
    }

    private fun findAmountAboveRow(
        rows: List<List<TextBlock>>,
        keywordIndex: Int,
        keywordBlock: TextBlock,
        patterns: ReceiptPatterns,
        extractAmount: (String) -> Double,
        isLandscape: Boolean
    ): Double? {
        if (keywordIndex <= 0) return null

        val keywordY = keywordBlock.boundingBox?.centerY()
        println("  [KeywordBasedStrategy] Keyword at Y:$keywordY")

        data class AmountCandidate(val rowIndex: Int, val amount: Double, val xRight: Int, val yDistance: Int)
        val candidates = mutableListOf<AmountCandidate>()

        val startRow = maxOf(0, keywordIndex - 10)

        for (i in startRow until keywordIndex) {
            val row = rows[i]

            // Skip interim sums
            val rowText = row.joinToString(" ") { it.text }
            if (rowText.matches(Regex("(?i).*(zw-summe|zwischensumme|subtotal).*"))) continue

            for (block in row) {
                val bbox = block.boundingBox ?: continue
                val blockY = bbox.centerY()
                val yDistance = if (keywordY != null) abs(blockY - keywordY) else Int.MAX_VALUE

                val amount = extractAmount(block.text)
                if (amount > 0) {
                    candidates.add(AmountCandidate(i, amount, bbox.right, yDistance))
                }
            }
        }

        if (candidates.isEmpty()) {
            println("  [KeywordBasedStrategy] No candidates found above keyword")
            return null
        }

        val selected = if (isLandscape) {
            // For landscape: prioritize rightmost amounts
            val rightmostX = candidates.maxOf { it.xRight }
            val rightmostCandidates = candidates.filter { it.xRight >= rightmostX - 300 }
            rightmostCandidates.maxByOrNull { it.rowIndex }
        } else {
            // For portrait: prioritize horizontal alignment (smallest Y-distance)
            candidates.minByOrNull { it.yDistance }
        }

        if (selected != null) {
            println("  [KeywordBasedStrategy] Selected amount: ${selected.amount} (yDistance=${selected.yDistance}px, xRight=${selected.xRight})")
            return selected.amount
        }

        return null
    }

    private fun findAmountNearRow(
        rows: List<List<TextBlock>>,
        keywordIndex: Int,
        patterns: ReceiptPatterns,
        extractAmount: (String) -> Double
    ): Double? {
        data class AmountCandidate(val rowIndex: Int, val amount: Double, val xRight: Int)
        val candidates = mutableListOf<AmountCandidate>()

        val searchEnd = minOf(keywordIndex + 20, rows.size)

        for (i in keywordIndex + 1 until searchEnd) {
            for (block in rows[i]) {
                val bbox = block.boundingBox ?: continue
                val amount = extractAmount(block.text)
                if (amount > 0) {
                    candidates.add(AmountCandidate(i, amount, bbox.right))
                }
            }
        }

        if (candidates.isEmpty()) return null

        // Return rightmost amount closest to keyword row
        val rightmostX = candidates.maxOf { it.xRight }
        val rightmostAmounts = candidates.filter { it.xRight >= rightmostX - 300 }

        return rightmostAmounts.minByOrNull { it.rowIndex }?.amount
    }
}
