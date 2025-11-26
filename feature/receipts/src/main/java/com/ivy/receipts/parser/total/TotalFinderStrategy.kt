package com.ivy.receipts.parser.total

import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.parser.ReceiptPatterns

/**
 * Strategy for finding the total amount on a receipt.
 * Different strategies can be tried in sequence until one succeeds.
 */
interface TotalFinderStrategy {
    /**
     * Attempt to find the total amount.
     * @return The total amount if found, or null if this strategy cannot determine the total
     */
    fun findTotal(
        blocks: List<TextBlock>,
        rows: List<List<TextBlock>>,
        patterns: ReceiptPatterns,
        extractAmount: (String) -> Double
    ): Double?

    /**
     * Human-readable name for logging/debugging
     */
    val name: String
}

/**
 * Result from a total finder strategy with confidence score
 */
data class TotalCandidate(
    val amount: Double,
    val confidence: Double, // 0.0 to 1.0
    val strategy: String,
    val reason: String
)

/**
 * Composite strategy that tries multiple strategies in order until one succeeds.
 */
class CompositeTotalFinder(
    private val strategies: List<TotalFinderStrategy>
) {
    fun findTotal(
        blocks: List<TextBlock>,
        rows: List<List<TextBlock>>,
        patterns: ReceiptPatterns,
        extractAmount: (String) -> Double
    ): Double? {
        println("=== COMPOSITE TOTAL FINDER ===")
        println("Trying ${strategies.size} strategies in order")

        for (strategy in strategies) {
            println("\n--- Trying strategy: ${strategy.name} ---")
            val result = strategy.findTotal(blocks, rows, patterns, extractAmount)
            if (result != null && result > 0) {
                println("✓ Strategy '${strategy.name}' succeeded with total: $result")
                return result
            }
            println("✗ Strategy '${strategy.name}' failed or returned 0")
        }

        println("\n✗ All strategies failed")
        return null
    }

    /**
     * Try all strategies and return candidates sorted by confidence
     */
    fun findAllCandidates(
        blocks: List<TextBlock>,
        rows: List<List<TextBlock>>,
        patterns: ReceiptPatterns,
        extractAmount: (String) -> Double
    ): List<TotalCandidate> {
        val candidates = mutableListOf<TotalCandidate>()

        for (strategy in strategies) {
            val result = strategy.findTotal(blocks, rows, patterns, extractAmount)
            if (result != null && result > 0) {
                // TODO: strategies could return confidence scores
                candidates.add(TotalCandidate(result, 0.5, strategy.name, "Found by ${strategy.name}"))
            }
        }

        return candidates.sortedByDescending { it.confidence }
    }
}
