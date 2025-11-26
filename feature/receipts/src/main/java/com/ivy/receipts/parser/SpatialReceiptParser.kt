package com.ivy.receipts.parser

import com.ivy.data.repository.CategoryRepository
import com.ivy.receipts.category.CategoryDetector
import com.ivy.receipts.category.CategoryInfo
import com.ivy.receipts.ocr.OcrReceipt
import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.parser.locales.EnglishReceiptPatterns
import com.ivy.receipts.parser.locales.GermanReceiptPatterns
import com.ivy.receipts.parser.orientation.LandscapeStrategy
import com.ivy.receipts.parser.orientation.OrientationStrategy
import com.ivy.receipts.parser.orientation.PortraitStrategy
import com.ivy.receipts.parser.total.AmountExtractor
import com.ivy.receipts.parser.total.CompositeTotalFinder
import com.ivy.receipts.parser.total.FallbackExtractor
import com.ivy.receipts.parser.total.strategies.KeywordBasedStrategy
import com.ivy.receipts.parser.total.strategies.LargestAmountStrategy
import com.ivy.receipts.parser.total.strategies.RepetitionStrategy
import timber.log.Timber
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

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
        return try {
            Timber.tag(TAG).i("=== Starting receipt parsing ===")
            Timber.tag(TAG).i("Total blocks: ${blocks.size}")

            val filteredBlocks = blocks.filter { it.boundingBox != null }
            Timber.tag(TAG).d("Blocks with bounding boxes: ${filteredBlocks.size}")

            if (filteredBlocks.isEmpty()) {
                Timber.tag(TAG).w("No blocks with bounding boxes found")
                return createEmptyReceipt()
            }

            // Detect orientation and select strategy
            val strategy = selectOrientationStrategy(filteredBlocks)
            Timber.tag(TAG).i("Detected orientation: ${strategy.javaClass.simpleName}")

            val rows = rowGrouper.group(filteredBlocks, strategy)
            Timber.tag(TAG).d("Grouped into ${rows.size} rows")

            val allLines = blocks.flatMap { it.lines.map { l -> l.text } }

            // Try composite total finder with multiple strategies
            val total = compositeTotalFinder.findTotal(
                blocks = filteredBlocks,
                rows = rows,
                patterns = patterns
            ) { text ->
                amountExtractor.extractStandaloneAmount(text, patterns)
            } ?: fallbackExtractor.extractTotal(allLines, patterns, amountExtractor)

            Timber.tag(TAG).i("Final total: $total")

            val date = dateExtractor.extract(allLines, patterns)
            Timber.tag(TAG).d("Extracted date: $date")

            // Detect category from receipt
            val categoryId = detectCategory(blocks)
            if (categoryId != null) {
                Timber.tag(TAG).d("Detected category ID: $categoryId")
            }

            // Extract merchant name
            val merchantName = merchantExtractor.extractMerchantName(blocks)
            if (merchantName != null) {
                Timber.tag(TAG).d("Extracted merchant: $merchantName")
            }

            OcrReceipt(
                total = BigDecimal.valueOf(total),
                currency = patterns.defaultCurrency,
                date = date,
                categoryId = categoryId,
                merchantName = merchantName
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error parsing receipt")
            createEmptyReceipt()
        }
    }

    /**
     * Select orientation strategy based on image dimensions.
     */
    private fun selectOrientationStrategy(blocks: List<TextBlock>): OrientationStrategy {
        if (blocks.isEmpty()) {
            Timber.tag(TAG).w("No blocks provided, defaulting to portrait")
            return PortraitStrategy()
        }

        val allBounds = blocks.mapNotNull { it.boundingBox }
        if (allBounds.isEmpty()) {
            Timber.tag(TAG).w("No bounding boxes found, defaulting to portrait")
            return PortraitStrategy()
        }

        val imageWidth =
            allBounds.maxOfOrNull { it.right }?.minus(allBounds.minOfOrNull { it.left } ?: 0) ?: 0
        val imageHeight =
            allBounds.maxOfOrNull { it.bottom }?.minus(allBounds.minOfOrNull { it.top } ?: 0) ?: 0

        if (imageHeight == 0) {
            Timber.tag(TAG).w("Invalid image dimensions, defaulting to portrait")
            return PortraitStrategy()
        }

        val imageRatio = imageWidth.toDouble() / imageHeight
        Timber.tag(TAG).d("Image dimensions: ${imageWidth}x${imageHeight}, ratio=$imageRatio")

        // Image is landscape if width > height (using threshold from constants)
        return if (imageRatio > ParserConstants.LANDSCAPE_RATIO_THRESHOLD) {
            LandscapeStrategy()
        } else {
            PortraitStrategy()
        }
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
        Timber.tag(TAG).e(e, "Category detection failed")
        null
    }

    /**
     * Creates an empty receipt with current timestamp.
     */
    private fun createEmptyReceipt() = OcrReceipt(
        total = BigDecimal.ZERO,
        currency = patterns.defaultCurrency,
        date = Instant.now(),
        categoryId = null,
        merchantName = null
    )

    companion object {
        private const val TAG = "SpatialReceiptParser"
    }
}

/**
 * Groups OCR blocks into rows using the appropriate orientation strategy.
 */
class RowGrouper {
    fun group(blocks: List<TextBlock>, strategy: OrientationStrategy): List<List<TextBlock>> {
        if (blocks.isEmpty()) {
            Timber.tag(TAG).d("No blocks to group")
            return emptyList()
        }

        return try {
            val sortedBlocks = strategy.sortBlocksForRows(blocks)
            val rows = strategy.groupIntoRows(sortedBlocks)

            Timber.tag(TAG).d("Grouped ${blocks.size} blocks into ${rows.size} rows")

            // Sort blocks within each row
            rows.map { row -> strategy.sortBlocksWithinRow(row) }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error grouping blocks into rows")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "RowGrouper"
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
