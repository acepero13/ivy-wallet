package com.ivy.receipts.parser.orientation

import com.ivy.receipts.ocr.TextBlock

/**
 * Strategy interface for handling different receipt orientations.
 * Provides methods for sorting and grouping text blocks based on orientation.
 */
interface OrientationStrategy {
    /**
     * Sort text blocks for initial row processing based on orientation.
     * Portrait: sorts by Y coordinate (top to bottom)
     * Landscape: sorts by X coordinate (left to right)
     */
    fun sortBlocksForRows(blocks: List<TextBlock>): List<TextBlock>

    /**
     * Groups sorted text blocks into logical rows based on their proximity.
     * Uses ROW_GROUPING_THRESHOLD_PX to determine if blocks belong to the same row.
     */
    fun groupIntoRows(sortedBlocks: List<TextBlock>): List<List<TextBlock>>

    /**
     * Sorts text blocks within a single row in reading order.
     * Portrait: left to right
     * Landscape: top to bottom
     */
    fun sortBlocksWithinRow(row: List<TextBlock>): List<TextBlock>
}
