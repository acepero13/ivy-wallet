package com.ivy.receipts.parser

import android.graphics.Rect
import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.ocr.TextLine
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Integration tests for receipt parsing that simulate real-world OCR output.
 * These tests use realistic receipt text as it would be extracted by ML Kit.
 */
class ReceiptParserIntegrationTest {

    @Test
    fun `integration test - German supermarket receipt from REWE`() {
        // Simulate ML Kit OCR output from a real REWE receipt
        val ocrBlocks = listOf(
            createBlock("REWE"),
            createBlock("Ihr Kaufpark"),
            createBlock("Hauptstraße 123"),
            createBlock("12345 Berlin"),
            createBlock(""),
            createBlock("Datum: 22.11.2025    Uhrzeit: 14:35"),
            createBlock("Kasse: 003    Bon-Nr: 1234"),
            createBlock(""),
            createBlock("Bio-Vollmilch 3,5%              1,29"),
            createBlock("Freilandeier 10er               2,99"),
            createBlock("Vollkornbrot 500g               1,49"),
            createBlock("Gouda jung 45% 200g             1,79"),
            createBlock("Tafeläpfel Elstar 1kg           2,49"),
            createBlock(""),
            createBlock("SUMME EUR                      10,05"),
            createBlock("Geg. BAR EUR                   20,00"),
            createBlock("Rückgeld EUR                    9,95"),
            createBlock(""),
            createBlock("Enthaltene MwSt.  7,00%         0,42"),
            createBlock("Enthaltene MwSt. 19,00%         1,48"),
            createBlock(""),
            createBlock("Vielen Dank für Ihren Einkauf!"),
            createBlock("www.rewe.de")
        )

        val parser = RegexMlkitParser.forLocale("de_DE")
        val result = parser.parse(ocrBlocks)

        // Assertions
        result.total shouldBe 10.05
        result.currency shouldBe "EUR"
        result.date.toLocalDate() shouldBe LocalDate.of(2025, 11, 22)
    }

    @Test
    fun `integration test - German discount store receipt from ALDI`() {
        val ocrBlocks = listOf(
            createBlock("ALDI SÜD"),
            createBlock("Filiale 4711"),
            createBlock("Musterweg 42"),
            createBlock("80331 München"),
            createBlock("Tel: 089/12345678"),
            createBlock(""),
            createBlock("22.11.2025           14:35:22"),
            createBlock(""),
            createBlock("H-Milch 1,5% 1L                 0,99"),
            createBlock("Eier Freiland 6er               1,29"),
            createBlock("Mehl Type 405 1kg               0,49"),
            createBlock("Butter 250g                     1,99"),
            createBlock("Joghurt natur 500g              0,79"),
            createBlock("Bananen 1kg                     1,29"),
            createBlock(""),
            createBlock("SUMME                           6,84"),
            createBlock("Bar EUR                        10,00"),
            createBlock("Rückgeld                        3,16"),
            createBlock(""),
            createBlock("Netto      7% MwSt               0,27"),
            createBlock("Netto     19% MwSt               4,64"),
            createBlock(""),
            createBlock("Danke für Ihren Einkauf")
        )

        val parser = RegexMlkitParser.withAutoDetection()
        val result = parser.parse(ocrBlocks)

        result.total shouldBe 6.84
        result.currency shouldBe "EUR"
        result.date.toLocalDate() shouldBe LocalDate.of(2025, 11, 22)
    }

    @Test
    fun `integration test - US retail receipt from Walmart`() {
        val ocrBlocks = listOf(
            createBlock("WALMART SUPERCENTER"),
            createBlock("Store #5432"),
            createBlock("123 Main Street"),
            createBlock("Anytown, NY 12345"),
            createBlock("(555) 123-4567"),
            createBlock(""),
            createBlock("11/22/2025         2:35 PM"),
            createBlock(""),
            createBlock("GROCERY"),
            createBlock("Whole Milk Gallon              $3.99"),
            createBlock("Large Eggs Dozen               $2.49"),
            createBlock("White Bread 24oz               $1.99"),
            createBlock("Cheddar Cheese 8oz             $3.99"),
            createBlock("Apples Red Delicious 3lb       $4.99"),
            createBlock(""),
            createBlock("SUBTOTAL                      $17.45"),
            createBlock("TAX 1 @ 0.0%                   $0.00"),
            createBlock("TOTAL                         $17.45"),
            createBlock(""),
            createBlock("CASH TEND                     $20.00"),
            createBlock("CHANGE                         $2.55"),
            createBlock(""),
            createBlock("Thank you for shopping at Walmart!"),
            createBlock("Save money. Live better.")
        )

        val parser = RegexMlkitParser.withAutoDetection()
        val result = parser.parse(ocrBlocks)

        result.total shouldBe 17.45
        result.currency shouldBe "USD"
        result.date.toLocalDate() shouldBe LocalDate.of(2025, 11, 22)
    }

    @Test
    fun `integration test - US coffee shop receipt from Starbucks`() {
        val ocrBlocks = listOf(
            createBlock("STARBUCKS COFFEE"),
            createBlock("Store #12345"),
            createBlock("555 Market St"),
            createBlock("San Francisco, CA 94105"),
            createBlock(""),
            createBlock("11/22/2025 2:35 PM"),
            createBlock("Order #142"),
            createBlock(""),
            createBlock("Grande Latte                   $4.95"),
            createBlock("Blueberry Muffin               $3.45"),
            createBlock("Bottled Water                  $2.25"),
            createBlock(""),
            createBlock("Subtotal                      $10.65"),
            createBlock("Tax                            $0.91"),
            createBlock("Total                         $11.56"),
            createBlock(""),
            createBlock("Credit Card                   $11.56"),
            createBlock(""),
            createBlock("Thank you!"),
            createBlock("starbucks.com/rewards")
        )

        val parser = RegexMlkitParser.forLocale("en_US")
        val result = parser.parse(ocrBlocks)

        result.total shouldBe 11.56
        result.currency shouldBe "USD"
    }

    @Test
    fun `integration test - receipt with OCR errors and noise`() {
        // Simulate imperfect OCR with typical recognition errors
        val ocrBlocks = listOf(
            createBlock("REW E"),  // OCR split the word
            createBlock("Hauptstr. 1"),
            createBlock(""),
            createBlock("Datum: 15.O3.2024"),  // OCR confused 0 with O
            createBlock(""),
            createBlock("Mi1ch                          2,49"),  // OCR confused l with 1
            createBlock("Br0t                           1,99"),  // OCR confused o with 0
            createBlock(""),
            createBlock("SUMME EUR                      4,48"),
            createBlock(""),
            createBlock("|||||||||||||||||||"),  // Barcode noise
            createBlock("Vielen Dank!")
        )

        val parser = RegexMlkitParser.forLocale("de_DE")
        val result = parser.parse(ocrBlocks)

        // Should still extract the total correctly despite OCR errors
        result.total shouldBe 4.48
        result.currency shouldBe "EUR"
    }

    @Test
    fun `integration test - minimal receipt with only total`() {
        val ocrBlocks = listOf(
            createBlock("Mini Mart"),
            createBlock("15.11.2025"),
            createBlock(""),
            createBlock("Total EUR                      5,99"),
            createBlock("")
        )

        val parser = RegexMlkitParser.withAutoDetection()
        val result = parser.parse(ocrBlocks)

        result.total shouldBe 5.99
        result.date.toLocalDate() shouldBe LocalDate.of(2025, 11, 15)
    }

    @Test
    fun `integration test - receipt without total line calculates from items`() {
        val ocrBlocks = listOf(
            createBlock("Corner Store"),
            createBlock("22.11.2025"),
            createBlock(""),
            createBlock("Item A                         3,50"),
            createBlock("Item B                         2,25"),
            createBlock("Item C                         1,75"),
            createBlock(""),
            createBlock("Thank you!")
        )

        val parser = RegexMlkitParser.forLocale("de_DE")
        val result = parser.parse(ocrBlocks)

        // Should sum up all items
        result.total shouldBe 7.50
    }

    @Test
    fun `integration test - German pharmacy receipt`() {
        val ocrBlocks = listOf(
            createBlock("Apotheke am Markt"),
            createBlock("Marktplatz 5"),
            createBlock("10115 Berlin"),
            createBlock(""),
            createBlock("22.11.2025            15:42"),
            createBlock(""),
            createBlock("Aspirin 20 Tbl.               12,99"),
            createBlock("Hustensaft 200ml               8,50"),
            createBlock("Vitamin C 100 Tbl.             6,99"),
            createBlock(""),
            createBlock("Bruttoumsatz                  28,48"),
            createBlock(""),
            createBlock("Enthält MwSt. 19%              4,54"),
            createBlock(""),
            createBlock("Gute Besserung!")
        )

        val parser = RegexMlkitParser.withAutoDetection()
        val result = parser.parse(ocrBlocks)

        result.total shouldBe 28.48
        result.currency shouldBe "EUR"
    }

    @Test
    fun `integration test - multiple currencies in text uses pattern default`() {
        val ocrBlocks = listOf(
            createBlock("International Shop"),
            createBlock("We accept USD, EUR, GBP"),
            createBlock(""),
            createBlock("Item                           5,99"),
            createBlock(""),
            createBlock("Summe                          5,99"),
            createBlock("")
        )

        val parser = RegexMlkitParser.forLocale("de_DE")
        val result = parser.parse(ocrBlocks)

        // Should use the pattern's default currency (EUR for German)
        result.currency shouldBe "EUR"
        result.total shouldBe 5.99
    }

    // ========== Helper Methods ==========

    /**
     * Create a TextBlock from a single line of text.
     * Simulates ML Kit OCR output structure.
     */
    private fun createBlock(text: String, confidence: Float = 0.95f): TextBlock {
        return TextBlock(
            text = text,
            boundingBox = Rect(0, 0, 1000, 50),
            lines = listOf(
                TextLine(
                    text = text,
                    confidence = confidence
                )
            )
        )
    }

    /**
     * Extension to convert Instant to LocalDate for easier comparison.
     */
    private fun java.time.Instant.toLocalDate(): LocalDate {
        return this.atZone(ZoneId.systemDefault()).toLocalDate()
    }
}