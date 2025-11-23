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
import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.ocr.TextLine
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        parser = RegexMlkitParser.withAutoDetection()
        spatialParser = SpatialReceiptParser()
    }



    @Test
    fun testParseReceiptFromAssets_RegexParser() {
        runBlocking {
            try {
                val inputStream = context.assets.open("dm1.jpeg")
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                val ocrBlocks = performOcr(bitmap)

                // Debug: Print all extracted text
                println("=== OCR BLOCKS (Regex Parser) ===")
                ocrBlocks.forEachIndexed { index, block ->
                    println("Block $index: ${block.text} | BBox: ${block.boundingBox}")
                }

                val result = parser.parse(ocrBlocks)

                println("=== REGEX PARSER RESULT ===")
                println("Total: ${result.total}")
                println("Currency: ${result.currency}")

                result.total shouldBe  27.66
                result.currency shouldNotBe null
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

                result.total shouldBe  27.66
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