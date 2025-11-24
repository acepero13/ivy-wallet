package com.ivy.receipts.category

import android.graphics.Rect
import com.ivy.data.model.CategoryId
import com.ivy.receipts.category.locales.GermanMerchantPatterns
import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.ocr.TextLine
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID

class MerchantCategoryDetectorTest {

    private val detector = MerchantCategoryDetector(GermanMerchantPatterns())

    @Test
    fun `should detect ALDI as groceries`()   {
        runBlocking {
            val blocks = listOf(
                createTextBlock("ALDI SÜD", top = 0),
                createTextBlock("Filiale 1234", top = 100)
            )

            val availableCategories = listOf(
                CategoryInfo(CategoryId(UUID.randomUUID()), "Lebensmittel"),
                CategoryInfo(CategoryId(UUID.randomUUID()), "Transport"),
                CategoryInfo(CategoryId(UUID.randomUUID()), "Health")
            )

            val result = detector.detectCategory(blocks, availableCategories)

            result shouldBe availableCategories[0].id
        }
    }

    @Test
    fun `should detect DM as health and personal care`()  {
        runBlocking {
            val blocks = listOf(
                createTextBlock("DM-DROGERIE MARKT", top = 0),
                createTextBlock("Filiale 5678", top = 100)
            )

            val availableCategories = listOf(
                CategoryInfo(CategoryId(UUID.randomUUID()), "Groceries"),
                CategoryInfo(CategoryId(UUID.randomUUID()), "Health"),
                CategoryInfo(CategoryId(UUID.randomUUID()), "Transport")
            )

            val result = detector.detectCategory(blocks, availableCategories)

            result shouldBe availableCategories[1].id
        }
    }

    @Test
    fun `should match with partial keyword match`() {
        runBlocking {
            val blocks = listOf(
                createTextBlock("REWE", top = 0),
                createTextBlock("Center", top = 100)
            )

            val availableCategories = listOf(
                CategoryInfo(CategoryId(UUID.randomUUID()), "Food & Drinks"),
                CategoryInfo(CategoryId(UUID.randomUUID()), "Transport")
            )

            val result = detector.detectCategory(blocks, availableCategories)

            result shouldBe availableCategories[0].id
        }
    }

    @Test
    fun `should return null when merchant not found`()  {
        runBlocking {
            val blocks = listOf(
                createTextBlock("UNKNOWN STORE", top = 0),
                createTextBlock("Unknown Location", top = 100)
            )

            val availableCategories = listOf(
                CategoryInfo(CategoryId(UUID.randomUUID()), "Groceries"),
                CategoryInfo(CategoryId(UUID.randomUUID()), "Transport")
            )

            val result = detector.detectCategory(blocks, availableCategories)

            result shouldBe null
        }
    }

    @Test
    fun `should return null when no matching category exists`()  {
        runBlocking {
            val blocks = listOf(
                createTextBlock("ALDI", top = 0)
            )

            val availableCategories = listOf(
                CategoryInfo(CategoryId(UUID.randomUUID()), "Transport"),
                CategoryInfo(CategoryId(UUID.randomUUID()), "Entertainment")
            )

            val result = detector.detectCategory(blocks, availableCategories)

            result shouldBe null
        }
    }

    private fun createTextBlock(text: String, top: Int): TextBlock {
        return TextBlock(
            text = text,
            boundingBox = Rect(0, top, 100, top + 50),
            lines = listOf(TextLine(text, 0.9f))
        )
    }
}
