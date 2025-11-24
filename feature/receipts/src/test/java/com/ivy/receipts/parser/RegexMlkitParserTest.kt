package com.ivy.receipts.parser

import android.graphics.Rect
import com.ivy.receipts.ocr.TextBlock
import com.ivy.receipts.ocr.TextLine
import com.ivy.receipts.parser.locales.EnglishReceiptPatterns
import com.ivy.receipts.parser.locales.GermanReceiptPatterns
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RegexMlkitParserTest {

    private lateinit var germanParser: RegexMlkitParser
    private lateinit var englishParser: RegexMlkitParser
    private lateinit var autoParser: RegexMlkitParser

    @Before
    fun setup() {
        germanParser = RegexMlkitParser(GermanReceiptPatterns())
        englishParser = RegexMlkitParser(EnglishReceiptPatterns())
        autoParser = RegexMlkitParser.withAutoDetection()
    }

    // ========== German Receipt Tests ==========

    @Test
    fun `parse German receipt - with total and items`()  {
        runBlocking {
            // given
            val receiptText = """
            REWE Markt GmbH
            Musterstraße 123
            12345 Berlin

            Datum: 15.03.2024

            Milch                    2,49
            Brot                     1,99
            Käse                     3,99

            Summe                    8,47 EUR

            Vielen Dank für Ihren Einkauf
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = germanParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 8.47
            result.currency shouldBe "EUR"
            result.date.toLocalDate() shouldBe LocalDate.of(2024, 3, 15)
        }
    }

    @Test
    fun `parse German receipt - with brutto total`()  {
        runBlocking {
            // given
            val receiptText = """
            Supermarkt XYZ

            Datum: 20.12.2023

            Artikel A                5,00
            Artikel B                10,50

            Brutto                   15,50
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = germanParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 15.50
            result.currency shouldBe "EUR"
        }
    }

    @Test
    fun `parse German receipt - calculates total from items when no total line`()  {
        runBlocking {
            // given
            val receiptText = """
            Shop ABC

            Item 1                   3,50
            Item 2                   2,25
            Item 3                   4,75
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = germanParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 10.50
        }
    }

    // ========== English Receipt Tests ==========

    @Test
    fun `parse English receipt - with total and date`()  {
        runBlocking {
            // given
            val receiptText = """
            Walmart Supercenter
            123 Main Street
            New York, NY 10001

            Date: 03/15/2024

            Milk                     $2.99
            Bread                    $1.49
            Cheese                   $4.99

            Total                    $9.47

            Thank you for shopping!
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = englishParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 9.47
            result.currency shouldBe "USD"
            result.date.toLocalDate() shouldBe LocalDate.of(2024, 3, 15)
        }
    }

    @Test
    fun `parse English receipt - with amount due`()  {
        runBlocking {
            // given
            val receiptText = """
            Target Store

            Date: 12/25/2023

            Product A                $15.00
            Product B                $25.50

            Amount Due               $40.50
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = englishParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 40.50
            result.currency shouldBe "USD"
        }
    }

    @Test
    fun `parse English receipt - with gross total`()  {
        runBlocking {
            // given
            val receiptText = """
            Grocery Store

            Item 1                   $10.00
            Item 2                   $20.00

            Gross                    $30.00
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = englishParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 30.00
        }
    }

    // ========== Auto-Detection Tests ==========

    @Test
    fun `auto-detect German receipt`()  {
        runBlocking {
            // given
            val germanReceipt = """
            EDEKA Markt

            Datum: 10.01.2024

            Brot                     2,50
            Butter                   1,80

            Summe                    4,30 EUR

            Vielen Dank
        """.trimIndent()

            val blocks = createTextBlocks(germanReceipt)

            // when
            val result = autoParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 4.30
            result.currency shouldBe "EUR"
        }
    }

    @Test
    fun `auto-detect English receipt`()  {
        runBlocking {
            // given
            val englishReceipt = """
            Costco Wholesale

            Date: 01/10/2024

            Bread                    $2.50
            Butter                   $1.80

            Total                    $4.30

            Thank you
        """.trimIndent()

            val blocks = createTextBlocks(englishReceipt)

            // when
            val result = autoParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 4.30
            result.currency shouldBe "USD"
        }
    }

    // ========== Edge Cases ==========

    @Test
    fun `parse receipt - empty blocks returns zero total`()  {
        runBlocking {
            // given
            val blocks = emptyList<TextBlock>()

            // when
            val result = germanParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 0.0
        }
    }

    @Test
    fun `parse receipt - no total line sums items`()  {
        runBlocking {
            // given
            val receiptText = """
            Simple Receipt

            Item A                   5,00
            Item B                   3,50
            Item C                   1,50
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = germanParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 10.00
        }
    }

    @Test
    fun `parse receipt - defaults to current date when no date found`()  {
        runBlocking {
            // given
            val receiptText = """
            No Date Receipt

            Item                     5,00

            Summe                    5,00
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)
            val beforeParse = Instant.now()

            // when
            val result = germanParser.parse(blocks)

            // then
            val afterParse = Instant.now()
            result.date.isAfter(beforeParse.minusSeconds(1)) shouldBe true
            result.date.isBefore(afterParse.plusSeconds(1)) shouldBe true
        }
    }

    @Test
    fun `parse receipt - skips net and cash payment lines`()  {
        runBlocking {
            // given
            val receiptText = """
            Receipt with extras

            Item                     10,00

            Netto                    8,50
            Barzahlung
            Summe                    10,00
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = germanParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 10.00
        }
    }

    @Test
    fun `parse receipt - handles comma decimal separator correctly`()  {
        runBlocking {
            // given
            val receiptText = """
            German Receipt

            Item 1                   12,99
            Item 2                   7,50

            Summe                    20,49
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = germanParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 20.49
        }
    }

    @Test
    fun `parse receipt - handles period decimal separator correctly`()  {
        runBlocking {
            // given
            val receiptText = """
            US Receipt

            Item 1                   $12.99
            Item 2                   $7.50

            Total                    $20.49
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = englishParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 20.49
        }
    }

    // ========== Multiple Date Format Tests ==========

    @Test
    fun `parse German receipt - date with dots`()  {
        runBlocking {
            // given
            val receiptText = """
            Receipt
            15.03.2024
            Summe                    5,00
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = germanParser.parse(blocks)

            // then
            result.date.toLocalDate() shouldBe LocalDate.of(2024, 3, 15)
        }
    }

    @Test
    fun `parse German receipt - date with slashes`()  {
        runBlocking {
            // given
            val receiptText = """
            Receipt
            15/03/2024
            Summe                    5,00
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = germanParser.parse(blocks)

            // then
            result.date.toLocalDate() shouldBe LocalDate.of(2024, 3, 15)
        }
    }

    @Test
    fun `parse English receipt - MM-DD-YYYY format`()  {
        runBlocking {
            // given
            val receiptText = """
            Receipt
            03/15/2024
            Total                    $5.00
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = englishParser.parse(blocks)

            // then
            result.date.toLocalDate() shouldBe LocalDate.of(2024, 3, 15)
        }
    }

    // ========== Factory Method Tests ==========

    @Test
    fun `forLocale - creates German parser`()  {
        runBlocking {
            // when
            val parser = RegexMlkitParser.forLocale("de_DE")
            val receiptText = "Summe 10,50"
            val result = parser.parse(createTextBlocks(receiptText))

            // then
            result.currency shouldBe "EUR"
        }
    }

    @Test
    fun `forLocale - creates English parser`()  {
        runBlocking {
            // when
            val parser = RegexMlkitParser.forLocale("en_US")
            val receiptText = "Total 10.50"
            val result = parser.parse(createTextBlocks(receiptText))

            // then
            result.currency shouldBe "USD"
        }
    }

    @Test
    fun `forLocale - defaults to German for unknown locale`()  {
        runBlocking {
            // when
            val parser = RegexMlkitParser.forLocale("unknown")
            val receiptText = "Summe 10,50"
            val result = parser.parse(createTextBlocks(receiptText))

            // then
            result.currency shouldBe "EUR"
        }
    }

    // ========== Real-world Receipt Tests ==========

    @Test
    fun `parse complex German receipt`()  {
        runBlocking {
            // given
            val receiptText = """
            ALDI SÜD
            Filiale 4711
            Hauptstr. 1
            80331 München

            Datum: 22.11.2025
            Zeit: 14:35

            Milch 3,5% Fett          1,29
            Bio Eier 10 Stk          2,99
            Vollkornbrot 500g        1,49
            Gouda jung 200g          1,79
            Äpfel Elstar 1kg         2,49

            Summe                   10,05 EUR
            Gegeben                 20,00 EUR
            Rückgeld                 9,95 EUR

            MwSt 7%                  0,42
            MwSt 19%                 1,48

            Vielen Dank für Ihren Einkauf!
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = germanParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 10.05
            result.currency shouldBe "EUR"
            result.date.toLocalDate() shouldBe LocalDate.of(2025, 11, 22)
        }
    }

    @Test
    fun `parse complex English receipt`()  {
        runBlocking {
            // given
            val receiptText = """
            WHOLE FOODS MARKET
            Store #10250
            555 Market St
            San Francisco, CA 94105

            Date: 11/22/2025
            Time: 2:35 PM

            Organic Milk             $4.99
            Free Range Eggs          $6.49
            Whole Grain Bread        $3.99
            Aged Cheddar 8oz         $5.99
            Honeycrisp Apples 2lb    $8.98

            Subtotal                $30.44
            Tax 8.5%                 $2.59
            Total                   $33.03

            Cash                    $40.00
            Change                   $6.97

            Thank you for shopping!
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = englishParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 33.03
            result.currency shouldBe "USD"
            result.date.toLocalDate() shouldBe LocalDate.of(2025, 11, 22)
        }
    }

    @Test
    fun `parse German receipt - total keyword and amount on separate lines`()  {
        runBlocking {
            // given - simulates OCR output where text is extracted line-by-line without spatial layout
            // This happens when ML Kit extracts "SUMME EUR" on one line and "27,66" many lines later
            val receiptText = """
            REWE Markt GmbH
            Musterstraße 123
            12345 Berlin

            Datum: 22.11.2025

            Milch
            2,49
            Brot
            1,99
            Eier
            3,50

            SUMME EUR

            Item Description Line
            Another Line
            Random Text
            Some More Info

            27,66

            Vielen Dank für Ihren Einkauf
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = germanParser.parse(blocks)

            // then - should find the total amount even though it's far from the keyword
            result.total.toDouble() shouldBe 27.66
            result.currency shouldBe "EUR"
        }
    }

    @Test
    fun `parse English receipt - total keyword and amount on separate lines`()  {
        runBlocking {
            // given
            val receiptText = """
            Walmart Supercenter
            123 Main Street
            New York, NY 10001

            Date: 11/22/2025

            Milk
            ${'$'}2.99
            Bread
            ${'$'}1.49

            TOTAL

            More text here
            Some other info

            ${'$'}15.99

            Thank you!
        """.trimIndent()

            val blocks = createTextBlocks(receiptText)

            // when
            val result = englishParser.parse(blocks)

            // then
            result.total.toDouble() shouldBe 15.99
            result.currency shouldBe "USD"
        }
    }

    // ========== Helper Methods ==========

    /**
     * Create TextBlocks from plain text.
     * Simulates OCR output by converting text lines into structured blocks.
     */
    private fun createTextBlocks(text: String): List<TextBlock> {
        val lines = text.lines()
        return lines.mapIndexed { index, line ->
            TextBlock(
                text = line,
                boundingBox = Rect(0, index * 20, 1000, (index + 1) * 20),
                lines = listOf(
                    TextLine(
                        text = line,
                        confidence = 0.95f
                    )
                )
            )
        }
    }

    /**
     * Extension to convert Instant to LocalDate for easier comparison.
     */
    private fun Instant.toLocalDate(): LocalDate {
        return this.atZone(ZoneId.systemDefault()).toLocalDate()
    }
}