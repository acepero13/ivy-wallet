package com.ivy.receipts.parser.strategies

import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.parser.ReceiptPatterns
import com.ivy.receipts.parser.TotalFinderStrategy

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

        // Extract all amounts from all blocks
        val amounts = mutableListOf<Double>()
        for (block in blocks) {
            val amount = extractAmount(block.text)
            if (amount >= 10.0) { // Filter small amounts
                amounts.add(amount)
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
                println("    $amount appears $count time(s)")
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
