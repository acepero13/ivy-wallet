package com.ivy.receipts.parser.orientation

import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.parser.ParserConstants
import kotlin.math.abs

/**
 * Strategy for portrait (vertical) receipts.
 * Text flows top-to-bottom, left-to-right.
 */
class PortraitStrategy : OrientationStrategy {
    override fun sortBlocksForRows(blocks: List<TextBlock>): List<TextBlock> {
        return blocks.sortedBy { it.boundingBox?.top ?: 0 }
    }

    override fun groupIntoRows(sortedBlocks: List<TextBlock>): List<List<TextBlock>> {
        if (sortedBlocks.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<TextBlock>>()
        var current = mutableListOf(sortedBlocks.first())

        for (i in 1 until sortedBlocks.size) {
            val prevBlock = sortedBlocks[i - 1]
            val currBlock = sortedBlocks[i]

            val prevY = prevBlock.boundingBox?.centerY() ?: continue
            val currY = currBlock.boundingBox?.centerY() ?: continue

            // Group blocks on the same visual row using threshold
            if (abs(currY - prevY) < ParserConstants.ROW_GROUPING_THRESHOLD_PX) {
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
        // Sort left to right
        return row.sortedBy { it.boundingBox?.left ?: 0 }
    }
}
