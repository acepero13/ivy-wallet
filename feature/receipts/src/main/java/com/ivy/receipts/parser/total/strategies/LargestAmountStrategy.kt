package com.ivy.receipts.parser.total.strategies

import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.parser.ReceiptPatterns
import com.ivy.receipts.parser.total.TotalFinderStrategy

/**
 * Fallback strategy that finds totals by looking for the largest amount in the bottom portion.
 *
 * This strategy is used when:
 * - No keywords are found (due to OCR issues like letter spacing)
 * - Other strategies fail
 *
 * Assumptions:
 * - Totals appear in bottom 30% of receipt
 * - Totals are >= 5.0 (filters out small items)
 * - The largest amount in that area is likely the total
 */
class LargestAmountStrategy : TotalFinderStrategy {
    override val name = "LargestAmountStrategy"

    override fun findTotal(
        blocks: List<TextBlock>,
        rows: List<List<TextBlock>>,
        patterns: ReceiptPatterns,
        extractAmount: (String) -> Double
    ): Double? {
        println("  [LargestAmountStrategy] Looking for largest amount in bottom portion...")

        if (rows.isEmpty()) {
            println("  [LargestAmountStrategy] No rows to search")
            return null
        }

        // Focus on bottom 30% of rows (where totals typically are)
        val bottomStartIndex = (rows.size * 0.7).toInt()
        val bottomRows = rows.subList(bottomStartIndex, rows.size)

        println("  [LargestAmountStrategy] Searching bottom ${bottomRows.size} rows (from index $bottomStartIndex)")

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
            println("  [LargestAmountStrategy] No candidates found in bottom portion")
            return null
        }

        // Return the largest amount (most likely to be the total)
        val largest = candidates.maxByOrNull { it.amount }!!
        println("  [LargestAmountStrategy] Selected largest amount: ${largest.amount} from row ${largest.rowIndex} ('${largest.text}')")
        return largest.amount
    }
}
