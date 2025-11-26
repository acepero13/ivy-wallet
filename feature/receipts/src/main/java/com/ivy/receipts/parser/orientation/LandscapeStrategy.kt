package com.ivy.receipts.parser.orientation

import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.parser.ParserConstants
import kotlin.math.abs

/**
 * Strategy for landscape (horizontal) receipts.
 * Text flows left-to-right in columns, which become "rows" for processing.
 */
class LandscapeStrategy : OrientationStrategy {
    override fun sortBlocksForRows(blocks: List<TextBlock>): List<TextBlock> {
        // For landscape, columns become rows, so sort by X coordinate
        return blocks.sortedBy { it.boundingBox?.left ?: 0 }
    }

    override fun groupIntoRows(sortedBlocks: List<TextBlock>): List<List<TextBlock>> {
        if (sortedBlocks.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<TextBlock>>()
        var current = mutableListOf(sortedBlocks.first())

        for (i in 1 until sortedBlocks.size) {
            val prevBlock = sortedBlocks[i - 1]
            val currBlock = sortedBlocks[i]

            val prevX = prevBlock.boundingBox?.centerX() ?: continue
            val currX = currBlock.boundingBox?.centerX() ?: continue

            // Group blocks in the same column using threshold
            if (abs(currX - prevX) < ParserConstants.ROW_GROUPING_THRESHOLD_PX) {
                current.add(currBlock)
            } else {
                rows.add(current)
                current = mutableListOf(currBlock)
            }
        }

        rows.add(current)
        return rows
    }

    override fun sortBlocksWithinRow(row: List<TextBlock>): List<TextBlock> {
        // For landscape, sort top to bottom within a column
        return row.sortedBy { it.boundingBox?.top ?: 0 }
    }
}
