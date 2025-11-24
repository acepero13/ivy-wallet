package com.ivy.receipts.parser

import android.graphics.Rect
import com.ivy.receipts.category.locales.GermanMerchantPatterns
import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.ocr.TextLine
import io.kotest.matchers.shouldBe
import org.junit.Test

class MerchantExtractorTest {

    private val extractor = MerchantExtractor(GermanMerchantPatterns())

    @Test
    fun `should extract ALDI merchant name in title case`() {
        val blocks = listOf(
            createTextBlock("Aldi", top = 0),
            createTextBlock("Filiale 1234", top = 100)
        )

        val result = extractor.extractMerchantName(blocks)

        result shouldBe "Aldi"
    }

    @Test
    fun `should extract DM merchant name with hyphen`() {
        val blocks = listOf(
            createTextBlock("Dm", top = 0),
            createTextBlock("Filiale 5678", top = 100)
        )

        val result = extractor.extractMerchantName(blocks)

        result shouldBe "Dm"
    }

    @Test
    fun `should extract REWE merchant name`() {
        val blocks = listOf(
            createTextBlock("REWE", top = 0),
            createTextBlock("Center Hauptstraße", top = 100)
        )

        val result = extractor.extractMerchantName(blocks)

        result shouldBe "Rewe"
    }

    @Test
    fun `should fallback to first block when merchant not recognized`() {
        val blocks = listOf(
            createTextBlock("Unknown Store Name", top = 0),
            createTextBlock("Address Line", top = 100)
        )

        val result = extractor.extractMerchantName(blocks)

        result shouldBe "Unknown Store Name"
    }

    @Test
    fun `should return null for very short text`() {
        val blocks = listOf(
            createTextBlock("A", top = 0)
        )

        val result = extractor.extractMerchantName(blocks)

        result shouldBe null
    }

    @Test
    fun `should return null for very long text`() {
        val blocks = listOf(
            createTextBlock("This is a very long merchant name that exceeds the reasonable limit", top = 0)
        )

        val result = extractor.extractMerchantName(blocks)

        result shouldBe null
    }

    @Test
    fun `should handle empty blocks`() {
        val blocks = emptyList<TextBlock>()

        val result = extractor.extractMerchantName(blocks)

        result shouldBe null
    }

    @Test
    fun `should convert all uppercase to title case`() {
        val blocks = listOf(
            createTextBlock("LIDL", top = 0)
        )

        val result = extractor.extractMerchantName(blocks)

        result shouldBe "Lidl"
    }

    private fun createTextBlock(text: String, top: Int): TextBlock {
        return TextBlock(
            text = text,
            boundingBox = Rect(0, top, 100, top + 50),
            lines = listOf(TextLine(text, 0.9f))
        )
    }
}
