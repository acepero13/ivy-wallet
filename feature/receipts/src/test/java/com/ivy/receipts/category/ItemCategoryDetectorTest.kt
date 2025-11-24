package com.ivy.receipts.category

import android.graphics.Rect
import com.ivy.data.model.CategoryId
import com.ivy.receipts.category.locales.GermanItemPatterns
import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.ocr.TextLine
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID

class ItemCategoryDetectorTest {

    private val detector = ItemCategoryDetector(GermanItemPatterns())

    @Test
    fun `should detect food items as groceries`() = runBlocking {
        val blocks = listOf(
            createTextBlock("Store Header", top = 0),
            createTextBlock("MILCH 1,5%", top = 100),
            createTextBlock("BROT VOLLKORN", top = 150),
            createTextBlock("BUTTER", top = 200),
            createTextBlock("EIER 10 STK", top = 250)
        )

        val availableCategories = listOf(
            CategoryInfo(CategoryId(UUID.randomUUID()), "Lebensmittel"),
            CategoryInfo(CategoryId(UUID.randomUUID()), "Transport")
        )

        val result = detector.detectCategory(blocks, availableCategories)

        result shouldBe availableCategories[0].id
    }

    @Test
    fun `should detect beverages as groceries`() = runBlocking {
        val blocks = listOf(
            createTextBlock("Store Header", top = 0),
            createTextBlock("COCA COLA", top = 100),
            createTextBlock("MINERALWASSER", top = 150),
            createTextBlock("ORANGENSAFT", top = 200)
        )

        val availableCategories = listOf(
            CategoryInfo(CategoryId(UUID.randomUUID()), "Getränke"),
            CategoryInfo(CategoryId(UUID.randomUUID()), "Transport")
        )

        val result = detector.detectCategory(blocks, availableCategories)

        result shouldBe availableCategories[0].id
    }

    @Test
    fun `should detect personal care items`() = runBlocking {
        val blocks = listOf(
            createTextBlock("Store Header", top = 0),
            createTextBlock("SHAMPOO", top = 100),
            createTextBlock("ZAHNPASTA", top = 150),
            createTextBlock("SEIFE", top = 200)
        )

        val availableCategories = listOf(
            CategoryInfo(CategoryId(UUID.randomUUID()), "Groceries"),
            CategoryInfo(CategoryId(UUID.randomUUID()), "Personal Care")
        )

        val result = detector.detectCategory(blocks, availableCategories)

        result shouldBe availableCategories[1].id
    }

    @Test
    fun `should return null when no items match`() = runBlocking {
        val blocks = listOf(
            createTextBlock("Store Header", top = 0),
            createTextBlock("UNKNOWN ITEM 1", top = 100),
            createTextBlock("RANDOM TEXT", top = 150)
        )

        val availableCategories = listOf(
            CategoryInfo(CategoryId(UUID.randomUUID()), "Groceries"),
            CategoryInfo(CategoryId(UUID.randomUUID()), "Transport")
        )

        val result = detector.detectCategory(blocks, availableCategories)

        result shouldBe null
    }

    @Test
    fun `should choose most dominant category when mixed items`() = runBlocking {
        val blocks = listOf(
            createTextBlock("Store Header", top = 0),
            createTextBlock("MILCH", top = 100),
            createTextBlock("BROT", top = 150),
            createTextBlock("KÄSE", top = 200),
            createTextBlock("SHAMPOO", top = 250) // Only one personal care item
        )

        val availableCategories = listOf(
            CategoryInfo(CategoryId(UUID.randomUUID()), "Food"),
            CategoryInfo(CategoryId(UUID.randomUUID()), "Personal Care")
        )

        val result = detector.detectCategory(blocks, availableCategories)

        // Should match Food category because it has 3 items vs 1 personal care
        result shouldBe availableCategories[0].id
    }

    private fun createTextBlock(text: String, top: Int): TextBlock {
        return TextBlock(
            text = text,
            boundingBox = Rect(0, top, 100, top + 50),
            lines = listOf(TextLine(text, 0.9f))
        )
    }
}
