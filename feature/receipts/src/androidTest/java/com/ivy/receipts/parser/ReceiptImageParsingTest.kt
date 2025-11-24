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
                val germanPatterns = com.ivy.receipts.parser.locales.GermanReceiptPatterns()
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
    fun testParseReceiptFromAssets_SpatialParser() {
        runBlocking {
            try {
                val inputStream = context.assets.open("dm1.jpeg")
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

                val result = spatialParser.parse(ocrBlocks)

                println("=== SPATIAL PARSER RESULT ===")
                println("Total: ${result.total}")
                println("Currency: ${result.currency}")

                result.total.toDouble() shouldBe  27.66
                result.currency shouldNotBe null
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
                val inputStream = context.assets.open("betreg_not_working.jpg")
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

                // Debug: Check if "Betrag" is recognized as a keyword
                val germanPatterns = com.ivy.receipts.parser.locales.GermanReceiptPatterns()
                println("=== KEYWORD CHECK ===")
                println("Is 'Betrag' a total keyword? ${germanPatterns.isTotalKeyword("Betrag")}")
                println("Is 'SUMME' a total keyword? ${germanPatterns.isTotalKeyword("SUMME")}")

                val result = spatialParser.parse(ocrBlocks)

                println("=== SPATIAL PARSER RESULT ===")
                println("Total: ${result.total}")
                println("Currency: ${result.currency}")

                result.total.toDouble() shouldBe  51.54
                result.currency shouldNotBe null
            } catch (e: Exception) {
                println("Error: ${e.message}")
                e.printStackTrace()
                throw e
            }
        }
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


}