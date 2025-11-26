package com.ivy.receipts.parser

/**
 * Constants used throughout the receipt parsing logic.
 * Centralizing these values makes them easier to tune and maintain.
 */
object ParserConstants {

    // Row grouping thresholds
    /**
     * Maximum vertical/horizontal pixel distance between text blocks to consider them on the same row.
     * Used for grouping OCR text blocks into logical rows.
     */
    const val ROW_GROUPING_THRESHOLD_PX = 100

    // Orientation detection
    /**
     * Aspect ratio threshold to detect landscape orientation.
     * If width/height ratio > this value, receipt is considered landscape.
     */
    const val LANDSCAPE_RATIO_THRESHOLD = 1.2

    // Amount search parameters
    /**
     * Maximum number of rows to search below a keyword when looking for totals.
     * Used in landscape receipts where totals might be further away.
     */
    const val MAX_ROWS_TO_SEARCH_FOR_TOTAL = 20

    /**
     * Maximum number of rows to search above a keyword when looking for totals.
     */
    const val MAX_ROWS_TO_SEARCH_ABOVE_KEYWORD = 10

    /**
     * Horizontal tolerance in pixels for detecting rightmost amounts.
     * Amounts within this distance from the rightmost edge are considered aligned.
     */
    const val RIGHTMOST_TOLERANCE_PX = 300

    // Amount filtering
    /**
     * Minimum amount value to consider as a valid total candidate.
     * Helps filter out small item prices from total detection.
     */
    const val MIN_TOTAL_CANDIDATE_AMOUNT = 5.0

    /**
     * Minimum amount threshold for general filtering.
     * Used to filter out very small values that are unlikely to be totals.
     */
    const val MIN_AMOUNT_THRESHOLD = 10.0

    /**
     * Tolerance for comparing two amounts as equal (in currency units).
     * Used for matching positive amounts with negative confirmations.
     */
    const val AMOUNT_COMPARISON_TOLERANCE = 0.01

    // Spatial search parameters
    /**
     * Ratio of receipt height to define the "bottom portion" where totals typically appear.
     * For example, 0.7 means the bottom 30% of the receipt (from 70% down to 100%).
     */
    const val BOTTOM_PORTION_RATIO = 0.7

    // Date pattern validation
    /**
     * Minimum number of consecutive digits required to reject as a date pattern.
     * Used to filter out year-like patterns (e.g., "2025") from amounts.
     */
    const val MIN_DIGITS_FOR_YEAR_REJECTION = 4

    /**
     * Minimum number of dots to reject as a date pattern.
     * Used to filter out date-like patterns (e.g., "25.11.2025") from amounts.
     */
    const val MIN_DOTS_FOR_DATE_REJECTION = 2
}
