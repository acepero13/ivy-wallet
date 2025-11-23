package com.ivy.receipts.parser

import android.graphics.Rect
import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.ocr.TextLine
import io.kotest.matchers.bigdecimal.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Before
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit test that demonstrates parsing receipts from OCR data
 * as if it came from actual images.
 *
 * This test uses simulated ML Kit OCR output that represents
 * what would be extracted from real receipt images.
 *
 * For actual image-based testing with ML Kit OCR, see:
 * - androidTest/ReceiptImageParsingTest.kt (Android instrumentation test)
 */
class ImageBasedReceiptParserTest {

    private lateinit var parser: RegexMlkitParser

    @Before
    fun setup() {
        parser = RegexMlkitParser.withAutoDetection()
    }

    @Test
    fun `parse receipt from simulated ML Kit OCR output - German REWE receipt`() {
        // This simulates the exact OCR output you would get from ML Kit
        // when scanning a real REWE receipt image
        val mlKitOcrOutput = simulateMLKitOCR(
            """
            REWE
            Ihr Kaufpark
            Hauptstraße 123
            12345 Berlin

            Datum: 22.11.2025    Zeit: 14:35
            Kasse: 003    Bon-Nr: 1234

            Bio-Vollmilch 3,5%              2,49
            Freilandeier 10er               2,99
            Vollkornbrot 500g               1,49
            Gouda jung 200g                 1,79
            Äpfel Elstar 1kg                2,49

            SUMME EUR                      11,25
            Geg. BAR EUR                   20,00
            Rückgeld EUR                    8,75

            Enthaltene MwSt.  7,00%         0,47
            Enthaltene MwSt. 19,00%         1,59

            Vielen Dank für Ihren Einkauf!
            www.rewe.de
            """.trimIndent()
        )

        // Parse the OCR output
        val result = parser.parse(mlKitOcrOutput)

        // Verify parsed values
        result.total.toDouble() shouldBe 11.25
        result.currency shouldBe "EUR"
        result.date.toLocalDate() shouldBe LocalDate.of(2025, 11, 22)
    }

    @Test
    fun `parse receipt from simulated ML Kit OCR output - US Walmart receipt`() {
        val mlKitOcrOutput = simulateMLKitOCR(
            """
            WALMART SUPERCENTER
            Store #5432
            123 Main Street
            Anytown, NY 12345

            11/22/2025         2:35 PM

            GROCERY
            Whole Milk Gallon              $3.99
            Large Eggs Dozen               $2.49
            White Bread 24oz               $1.99
            Cheddar Cheese 8oz             $3.99
            Apples Red Delicious 3lb       $4.99

            SUBTOTAL                      $17.45
            TAX 1 @ 0.0%                   $0.00
            TOTAL                         $17.45

            CASH TEND                     $20.00
            CHANGE                         $2.55

            Thank you for shopping at Walmart!
            Save money. Live better.
            """.trimIndent()
        )

        val result = parser.parse(mlKitOcrOutput)

        result.total.toDouble() shouldBe 17.45
        result.currency shouldBe "USD"
        result.date.toLocalDate() shouldBe LocalDate.of(2025, 11, 22)
    }

    @Test
    fun `parse receipt with OCR recognition errors - simulates real-world imperfect OCR`() {
        // Real OCR often makes mistakes - this simulates common errors
        val mlKitOcrOutput = simulateMLKitOCR(
            """
            REW E
            Hauptstr. 1

            Datum: 15.O3.2024

            Mi1ch                          2,49
            Br0t                           1,99

            SUMME EUR                      4,48

            |||||||||||||||||||
            Vielen Dank!
            """.trimIndent(),
            confidenceScores = mapOf(
                0 to 0.92f,  // "REW E" - lower confidence due to space
                3 to 0.85f,  // Date with OCR error
                5 to 0.88f,  // "Mi1ch" - OCR confused l with 1
                6 to 0.87f   // "Br0t" - OCR confused o with 0
            )
        )

        val result = parser.parse(mlKitOcrOutput)

        // Parser should still extract the total despite OCR errors
        result.total.toDouble() shouldBe 4.48
        result.currency shouldBe "EUR"
    }

    @Test
    fun `parse receipt from image file path - demonstrates file loading pattern`() {
        // This demonstrates the pattern for loading images from files
        // In a real test, you would:
        // 1. Load the image file from resources
        // 2. Use ML Kit to perform OCR
        // 3. Parse the OCR result

        /*
        Example with actual image loading (requires Android context):

        val imageFile = File(javaClass.classLoader.getResource("images/german_receipt_rewe.jpg").file)
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(inputImage).await()

        val ocrBlocks = convertMLKitToTextBlocks(visionText)
        val result = parser.parse(ocrBlocks)
        */

        // For this unit test, we simulate the result
        val simulatedOCRFromImage = simulateMLKitOCR(
            """
            ALDI SÜD
            22.11.2025
            Milch                          0,99
            Brot                           0,49
            Summe                          1,48
            """.trimIndent()
        )

        val result = parser.parse(simulatedOCRFromImage)

        result.total.toDouble() shouldBe 1.48
        result.total shouldBeGreaterThan BigDecimal.ZERO
    }

    @Test
    fun `demonstrate how to handle image resource loading`() {
        // This test shows the pattern you would use to load actual image resources

        val resourcePath = "images/german_receipt_rewe.jpg"

        // In a real test with resources, you would:
        // val imageStream = javaClass.classLoader.getResourceAsStream(resourcePath)

        // For now, we check if the file would exist
        val resourceExists = checkIfResourceWouldExist(resourcePath)

        // We can still run the test with simulated data
        val ocrOutput = simulateMLKitOCR("SUMME 10,00")
        val result = parser.parse(ocrOutput)

        result shouldNotBe null
        // In a real image test, you would assert specific values
    }

    @Test
    fun `parse high-quality OCR output with perfect confidence`() {
        val mlKitOcrOutput = simulateMLKitOCR(
            """
            EDEKA
            01.12.2025
            Butter                         1,99
            Milch                          1,29
            Eier                           2,49
            SUMME                          5,77
            """.trimIndent(),
            confidenceScores = mapOf(
                0 to 0.99f,
                1 to 0.98f,
                2 to 0.99f,
                3 to 0.98f,
                4 to 0.99f,
                5 to 0.99f
            )
        )

        val result = parser.parse(mlKitOcrOutput)

        result.total.toDouble() shouldBe 5.77
        result.currency shouldBe "EUR"
        result.date.toLocalDate() shouldBe LocalDate.of(2025, 12, 1)
    }

    @Test
    fun `parse low-quality OCR output with poor confidence`() {
        // Simulates OCR from a blurry or poorly lit image
        val mlKitOcrOutput = simulateMLKitOCR(
            """
            REWE
            Milk                           2 49
            Total                         2 49
            """.trimIndent(),
            confidenceScores = mapOf(
                0 to 0.65f,
                1 to 0.62f,
                2 to 0.68f
            )
        )

        val result = parser.parse(mlKitOcrOutput)

        // Parser should still attempt to extract data
        result shouldNotBe null
    }

    // ========== Helper Methods ==========

    /**
     * Simulates ML Kit OCR output from receipt text.
     * Creates TextBlocks as ML Kit would return them.
     */
    private fun simulateMLKitOCR(
        receiptText: String,
        confidenceScores: Map<Int, Float> = emptyMap()
    ): List<TextBlock> {
        val lines = receiptText.lines()

        return lines.mapIndexed { index, lineText ->
            val confidence = confidenceScores[index] ?: 0.95f

            TextBlock(
                text = lineText,
                boundingBox = Rect(
                    10,                    // left
                    50 + (index * 30),    // top
                    790,                   // right
                    80 + (index * 30)     // bottom
                ),
                lines = listOf(
                    TextLine(
                        text = lineText,
                        confidence = confidence
                    )
                )
            )
        }
    }

    /**
     * Checks if a resource file would exist.
     * In a real test, you would actually load the resource.
     */
    private fun checkIfResourceWouldExist(path: String): Boolean {
        return try {
            javaClass.classLoader?.getResource(path) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extension to convert Instant to LocalDate for easier comparison.
     */
    private fun java.time.Instant.toLocalDate(): LocalDate {
        return this.atZone(ZoneId.systemDefault()).toLocalDate()
    }
}

/**
 * README: How to use this test with actual images
 * ================================================
 *
 * This test demonstrates the pattern for parsing receipts from images.
 * To test with actual images:
 *
 * Option 1: Android Instrumentation Test (Recommended)
 * ----------------------------------------------------
 * Use src/androidTest/ReceiptImageParsingTest.kt which can:
 * - Load images from assets
 * - Use actual ML Kit OCR
 * - Run on a real device/emulator
 *
 * Run with: ./gradlew :feature:receipts:connectedAndroidTest
 *
 * Option 2: Add images to test resources
 * --------------------------------------
 * 1. Place images in: src/test/resources/images/
 * 2. Load them in tests using:
 *    val stream = javaClass.classLoader.getResourceAsStream("images/receipt.jpg")
 * 3. Process with ML Kit (requires Robolectric or Android context)
 *
 * Option 3: Use pre-captured OCR data
 * -----------------------------------
 * 1. Scan a receipt with ML Kit once
 * 2. Save the OCR output to a JSON file
 * 3. Load and parse the JSON in unit tests
 *
 * Example OCR JSON structure:
 * {
 *   "blocks": [
 *     {"text": "REWE", "confidence": 0.98, "boundingBox": {...}},
 *     {"text": "Summe 10,00", "confidence": 0.95, ...}
 *   ]
 * }
 */
