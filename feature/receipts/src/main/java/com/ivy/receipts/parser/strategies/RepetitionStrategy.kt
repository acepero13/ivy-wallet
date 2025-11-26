package com.ivy.receipts.parser.strategies

import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.parser.ReceiptPatterns
import com.ivy.receipts.parser.TotalFinderStrategy
import com.ivy.receipts.parser.normalizeReceiptAmount

/**
 * Strategy that finds totals by looking for repeated amounts.
 *
 * The logic: If the same amount appears multiple times on a receipt (e.g., 20.99),
 * and it's one of the larger amounts, it's likely the total (appearing as both
 * line item and final total). This helps distinguish totals from payment amounts
 * (e.g., "paid with 50" won't appear multiple times).
 *
 * Priority:
 * - Amounts > 10.0 (filters out small items)
 * - Repeated 2+ times
 * - Largest among repeated amounts
 */
class RepetitionStrategy : TotalFinderStrategy {
    override val name = "RepetitionStrategy"

    override fun findTotal(
        blocks: List<TextBlock>,
        rows: List<List<TextBlock>>,
        patterns: ReceiptPatterns,
        extractAmount: (String) -> Double
    ): Double? {
        println("  [RepetitionStrategy] Looking for repeated amounts...")

        // Extract all amounts from all blocks, with normalization for OCR errors
        val amounts = mutableListOf<Double>()
        val amountTexts = mutableMapOf<Double, MutableList<String>>() // Track original text for debugging

        for (block in blocks) {
            // Use direct normalization to handle OCR comma/period confusion
            // This is more reliable than extractAmount when OCR mixes up separators
            // For example: "51,54" and "51.54" both normalize to 51.54
            val normalizedAmount = block.text.normalizeReceiptAmount()

            if (normalizedAmount >= 10.0) {
                amounts.add(normalizedAmount)
                amountTexts.getOrPut(normalizedAmount) { mutableListOf() }.add(block.text)
                println("  [RepetitionStrategy] Block \"${block.text}\" -> normalized to $normalizedAmount")
            }
        }

        if (amounts.isEmpty()) {
            println("  [RepetitionStrategy] No amounts found >= 10.0")
            return null
        }

        // Count occurrences of each amount
        val amountCounts = amounts.groupingBy { it }.eachCount()

        println("  [RepetitionStrategy] Amount frequencies:")
        amountCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .forEach { (amount, count) ->
                val texts = amountTexts[amount]?.take(3)?.joinToString(", ") { "\"$it\"" } ?: ""
                println("    $amount appears $count time(s) [from: $texts]")
            }

        // Find amounts that appear 2+ times
        val repeatedAmounts = amountCounts.filter { it.value >= 2 }

        if (repeatedAmounts.isEmpty()) {
            println("  [RepetitionStrategy] No repeated amounts found")
            return null
        }

        // Among repeated amounts, return the largest one
        val total = repeatedAmounts.keys.maxOrNull()
        println("  [RepetitionStrategy] Largest repeated amount: $total")

        return total
    }
}
