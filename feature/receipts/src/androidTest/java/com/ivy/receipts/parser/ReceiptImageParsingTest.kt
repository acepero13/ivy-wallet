package com.ivy.receipts.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.repository.CategoryRepository
import com.ivy.receipts.category.CategoryDetector
import com.ivy.receipts.category.CompositeCategoryDetector
import com.ivy.receipts.ocr.OcrReceipt
import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.ocr.TextLine
import com.ivy.receipts.parser.locales.GermanReceiptPatterns
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId

/**
 * Android instrumentation test that reads actual images and parses them with ML Kit OCR.
 *
 * To run this test:
 * 1. Place test receipt images in src/androidTest/assets/
 * 2. Run: ./gradlew :feature:receipts:connectedAndroidTest
 *
 * Note: This test requires an Android device or emulator to run.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptImageParsingTest {

    private lateinit var context: Context
    private lateinit var parser: RegexMlkitParser

    private lateinit var spatialParser: SpatialReceiptParser
    private lateinit var mockCategoryRepository: CategoryRepository
    private lateinit var patterns: ReceiptPatterns
    private lateinit var categoryDetector: CategoryDetector

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        parser = RegexMlkitParser.withAutoDetection()

        // Create mock CategoryRepository with relaxed mode to handle inline value classes
        mockCategoryRepository = mockk<CategoryRepository>(relaxed = true)
        coEvery { mockCategoryRepository.findAll() } returns emptyList()

        // Create patterns and category detector
        patterns = GermanReceiptPatterns()
        categoryDetector = CompositeCategoryDetector()

        // Create SpatialReceiptParser with dependencies
        spatialParser = SpatialReceiptParser(
            categoryRepository = mockCategoryRepository,
            patterns = patterns,
            categoryDetector = categoryDetector
        )
    }





    @Test
    fun testParseBeregNotWorking() {
        runBlocking {
            try {
                val inputStream = context.assets.open("betreg_not_working.jpg")
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                val ocrBlocks = performOcr(bitmap)

                // Debug: Print ALL extracted text with coordinates
                println("=== BETREG RECEIPT OCR OUTPUT ===")
                ocrBlocks.forEachIndexed { index, block ->
                    val bbox = block.boundingBox
                    if (bbox != null) {
                        println("$index = \"${block.text}\" | X:${bbox.left}-${bbox.right} Y:${bbox.top}-${bbox.bottom}")
                    } else {
                        println("$index = \"${block.text}\" | No bbox")
                    }
                }

                // Debug: Check if "Betrag" is recognized as a keyword
                val germanPatterns = GermanReceiptPatterns()
                println("=== KEYWORD CHECK ===")
                println("Is 'Betrag' a total keyword? ${germanPatterns.isTotalKeyword("Betrag")}")
                println("Is 'SUMME' a total keyword? ${germanPatterns.isTotalKeyword("SUMME")}")

                val result = spatialParser.parse(ocrBlocks)

                println("=== SPATIAL PARSER RESULT ===")
                println("Total: ${result.total}")
                println("Currency: ${result.currency}")

                // This will fail if total is 0.0, which helps us debug
                result.total.toDouble() shouldBeGreaterThan 0.0
            } catch (e: Exception) {
                println("Error: ${e.message}")
                e.printStackTrace()
                throw e
            }
        }
    }



    @Test
    fun testParseReceiptFromAssets_NewPic_SpatialParser() {
        runBlocking {
            try {
                val result = loadPicture("betreg_not_working.jpg")
                result.total.toDouble() shouldBe  51.54
                result.currency shouldNotBe null
            } catch (e: Exception) {
                raiseError(e)
            }
        }
    }

    @Test fun total_from_shell_not_recognized() {
        runBlocking {
            try {
                val result = loadPicture("wrong_total/tankstelle.jpg")
                result.total.toDouble() shouldBe  66.63
                result.currency shouldNotBe null
                result.merchantName shouldBe "Shell"
            } catch (e: Exception) {
                raiseError(e)
            }
        }
    }

    @Test fun total_from_ernsties_not_recognized() {
        runBlocking {
            try {
                val result = loadPicture("wrong_total/ernsties_family.jpg")
                result.total.toDouble() shouldBe  29.99
                result.currency shouldNotBe null
                result.merchantName shouldBe "Ernstngi Family"
            } catch (e: Exception) {
                raiseError(e)
            }
        }
    }

    @Test fun total_from_alid_name_missing() {
        runBlocking {
            try {
                val result = loadPicture("missing_name/aldi_missing.jpg")
                result.total.toDouble() shouldBe  84.25
                result.currency shouldNotBe null
                //result.merchantName shouldBe "Aldi" // The picture does not contain Aldi
            } catch (e: Exception) {
                raiseError(e)
            }
        }
    }

    private fun raiseError(e: Exception): Nothing {
        println("Error: ${e.message}")
        e.printStackTrace()
        throw e
    }


    /**
     * Perform OCR on a bitmap using ML Kit.
     */
    private suspend fun performOcr(bitmap: Bitmap): List<TextBlock> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(inputImage).await()

            return visionText.textBlocks.map { block ->
                TextBlock(
                    text = block.text,
                    boundingBox = block.boundingBox,
                    lines = block.lines.map { line ->
                        TextLine(
                            text = line.text,
                            confidence = line.confidence
                        )
                    }
                )
            }
        } finally {
            recognizer.close()
        }
    }

    /**
     * ASCII visualization of OCR blocks showing spatial layout.
     * Creates a true spatial representation mimicking the actual receipt layout.
     */
    private fun visualizeOcrBlocks(blocks: List<TextBlock>) {
        val filteredBlocks = blocks.filter { it.boundingBox != null }
        if (filteredBlocks.isEmpty()) {
            println("No blocks with bounding boxes to visualize")
            return
        }

        // Calculate image dimensions
        val allBounds = filteredBlocks.mapNotNull { it.boundingBox }
        val minX = allBounds.minOf { it.left }
        val maxX = allBounds.maxOf { it.right }
        val minY = allBounds.minOf { it.top }
        val maxY = allBounds.maxOf { it.bottom }

        val imageWidth = maxX - minX
        val imageHeight = maxY - minY
        val isLandscape = imageWidth.toDouble() / imageHeight > 1.2

        println("=== ASCII SPATIAL VISUALIZATION ===")
        println("Image dimensions: ${imageWidth}x${imageHeight} (${if (isLandscape) "LANDSCAPE" else "PORTRAIT"})")
        println("Coordinate range: X:$minX-$maxX, Y:$minY-$maxY")
        println()

        // Create a 2D grid to place text
        val terminalWidth = 120  // Terminal width in characters
        val terminalHeight = 40  // Terminal height in lines

        // Scale factors to map image coordinates to terminal coordinates
        val scaleX = terminalWidth.toDouble() / imageWidth
        val scaleY = terminalHeight.toDouble() / imageHeight

        // Create empty canvas
        val canvas = Array(terminalHeight) { CharArray(terminalWidth) { ' ' } }

        // Track which blocks contain keywords for highlighting
        val germanPatterns = GermanReceiptPatterns()

        println("Legend: [#] = Total keyword, [*] = Amount, [ ] = Other text")
        println("=".repeat(terminalWidth))

        // Place each block on the canvas
        for (block in filteredBlocks) {
            val bbox = block.boundingBox!!

            // Calculate terminal position (normalize coordinates first)
            val termX = ((bbox.left - minX) * scaleX).toInt().coerceIn(0, terminalWidth - 1)
            val termY = ((bbox.top - minY) * scaleY).toInt().coerceIn(0, terminalHeight - 1)

            // Get text to display (truncate if needed)
            val displayText = block.text.replace("\n", " ").take(20).trim()
            if (displayText.isEmpty()) continue

            // Determine marker based on content
            val marker = when {
                germanPatterns.isTotalKeyword(block.text) -> '#'
                block.text.matches(Regex(".*\\d+[,.]\\d{2}.*")) -> '*'  // Contains amount
                else -> ' '
            }

            // Try to place text at position
            val textWithMarker = "[$marker]$displayText"
            for (i in textWithMarker.indices) {
                val x = termX + i
                if (x < terminalWidth && termY < terminalHeight) {
                    // Don't overwrite existing text if it would collide
                    if (canvas[termY][x] == ' ' || i == 0) {
                        canvas[termY][x] = textWithMarker[i]
                    }
                }
            }
        }

        // Print the canvas
        for (row in canvas) {
            println(row.concatToString())
        }

        println("=" .repeat(terminalWidth))
        println()

        // Print detailed list of keyword blocks
        println("=== KEYWORD & AMOUNT DETECTION ===")
        filteredBlocks
            .filter { germanPatterns.isTotalKeyword(it.text) || it.text.matches(Regex(".*\\d+[,.]\\d{2}.*")) }
            .sortedBy { it.boundingBox!!.top }
            .forEach { block ->
                val bbox = block.boundingBox!!
                val isKeyword = germanPatterns.isTotalKeyword(block.text)
                val hasAmount = block.text.matches(Regex(".*\\d+[,.]\\d{2}.*"))
                val type = when {
                    isKeyword -> "[#] TOTAL KEYWORD"
                    hasAmount -> "[*] AMOUNT     "
                    else -> "[ ] OTHER      "
                }
                val text = block.text.replace("\n", " ").take(30)
                println("$type at Y:${bbox.top.toString().padEnd(5)} X:${bbox.left.toString().padEnd(5)} | \"$text\"")
            }
        println()
    }

    suspend fun loadPicture(pictureUri: String): OcrReceipt {
        val inputStream = context.assets.open(pictureUri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val ocrBlocks = performOcr(bitmap)

        // Debug: Print all extracted text with coordinates
        println("=== OCR BLOCKS (Spatial Parser) ===")
        ocrBlocks.forEachIndexed { index, block ->
            val bbox = block.boundingBox
            if (bbox != null) {
                println("Block $index: '${block.text}' | X:${bbox.left}-${bbox.right} Y:${bbox.top}-${bbox.bottom}")
            } else {
                println("Block $index: '${block.text}' | No bounding box")
            }
        }
        println()

        // NEW: ASCII visualization
        visualizeOcrBlocks(ocrBlocks)

        // Debug: Check if "Betrag" is recognized as a keyword
        val germanPatterns = com.ivy.receipts.parser.locales.GermanReceiptPatterns()
        println("=== KEYWORD CHECK ===")
        println("Is 'Betrag' a total keyword? ${germanPatterns.isTotalKeyword("Betrag")}")
        println("Is 'SUMME' a total keyword? ${germanPatterns.isTotalKeyword("SUMME")}")

        val result =  spatialParser.parse(ocrBlocks)

        println("=== SPATIAL PARSER RESULT ===")
        println("Total: ${result.total}")
        println("Currency: ${result.currency}")
        return result
    }


}