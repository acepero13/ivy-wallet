package com.ivy.receipts.parser.locales

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

/**
 * Tests for locale-specific receipt patterns.
 * Validates that regex patterns correctly match expected receipt text.
 */
class ReceiptPatternsTest {

    // ========== German Pattern Tests ==========

    @Test
    fun `GermanReceiptPatterns - totalPattern matches German totals`() {
        val patterns = GermanReceiptPatterns()

        patterns.totalPattern.find("Summe 12,99") shouldNotBe null
        patterns.totalPattern.find("SUMME EUR 12,99") shouldNotBe null
        patterns.totalPattern.find("Gesamt 42,50") shouldNotBe null
        patterns.totalPattern.find("Total: 99,99") shouldNotBe null
    }

    @Test
    fun `GermanReceiptPatterns - totalGrossPattern matches brutto`() {
        val patterns = GermanReceiptPatterns()

        patterns.totalGrossPattern.find("Brutto 15,99") shouldNotBe null
        patterns.totalGrossPattern.find("BRUTTOUMSATZ 42,50") shouldNotBe null
        patterns.totalGrossPattern.find("Bruttoumsatz + 99,99") shouldNotBe null
    }

    @Test
    fun `GermanReceiptPatterns - itemPattern matches line items`() {
        val patterns = GermanReceiptPatterns()

        val match1 = patterns.itemPattern.find("Milch 3,5%                     2,49")
        match1 shouldNotBe null
        match1?.groupValues?.get(1)?.trim() shouldBe "Milch 3,5%"
        match1?.groupValues?.get(2) shouldBe "2,49"

        val match2 = patterns.itemPattern.find("Brot                           1,99€")
        match2 shouldNotBe null
    }

    @Test
    fun `GermanReceiptPatterns - netPattern matches netto lines`() {
        val patterns = GermanReceiptPatterns()

        patterns.netPattern.find("Netto 19% MwSt 5,00") shouldNotBe null
        patterns.netPattern.find("NETTOUMSATZ 42,50") shouldNotBe null
    }

    @Test
    fun `GermanReceiptPatterns - cashPaymentPattern matches cash indicators`() {
        val patterns = GermanReceiptPatterns()

        patterns.cashPaymentPattern.matches("Barzahlung").shouldBeTrue()
        patterns.cashPaymentPattern.matches("BAR").shouldBeTrue()
    }

    @Test
    fun `GermanReceiptPatterns - datePattern matches German date formats`() {
        val patterns = GermanReceiptPatterns()

        patterns.datePattern.find("15.03.2024") shouldNotBe null
        patterns.datePattern.find("1.1.2024") shouldNotBe null
        patterns.datePattern.find("31/12/2024") shouldNotBe null
        patterns.datePattern.find("Datum: 22-11-2025") shouldNotBe null
    }

    @Test
    fun `GermanReceiptPatterns - has correct locale settings`() {
        val patterns = GermanReceiptPatterns()

        patterns.localeCode shouldBe "de_DE"
        patterns.defaultCurrency shouldBe "EUR"
        patterns.dateFormats.shouldContain("dd.MM.yyyy")
    }

    // ========== English Pattern Tests ==========

    @Test
    fun `EnglishReceiptPatterns - totalPattern matches English totals`() {
        val patterns = EnglishReceiptPatterns()

        patterns.totalPattern.find("Total $12.99") shouldNotBe null
        patterns.totalPattern.find("TOTAL: $42.50") shouldNotBe null
        patterns.totalPattern.find("Amount Due $99.99") shouldNotBe null
        patterns.totalPattern.find("Balance Due: 15.50") shouldNotBe null
    }

    @Test
    fun `EnglishReceiptPatterns - totalGrossPattern matches gross totals`() {
        val patterns = EnglishReceiptPatterns()

        patterns.totalGrossPattern.find("Gross $15.99") shouldNotBe null
        patterns.totalGrossPattern.find("GRAND TOTAL: $99.99") shouldNotBe null
    }

    @Test
    fun `EnglishReceiptPatterns - itemPattern matches line items`() {
        val patterns = EnglishReceiptPatterns()

        val match1 = patterns.itemPattern.find("Milk Whole                     $2.99")
        match1 shouldNotBe null
        match1?.groupValues?.get(1)?.trim() shouldBe "Milk Whole"
        match1?.groupValues?.get(2) shouldBe "2.99"

        val match2 = patterns.itemPattern.find("Bread                          1.99")
        match2 shouldNotBe null
    }

    @Test
    fun `EnglishReceiptPatterns - netPattern matches subtotal lines`() {
        val patterns = EnglishReceiptPatterns()

        patterns.netPattern.find("Net 15.00") shouldNotBe null
        patterns.netPattern.find("Subtotal $42.50") shouldNotBe null
    }

    @Test
    fun `EnglishReceiptPatterns - cashPaymentPattern matches cash indicators`() {
        val patterns = EnglishReceiptPatterns()

        patterns.cashPaymentPattern.matches("Cash Payment").shouldBeTrue()
        patterns.cashPaymentPattern.matches("CASH").shouldBeTrue()
    }

    @Test
    fun `EnglishReceiptPatterns - datePattern matches US date formats`() {
        val patterns = EnglishReceiptPatterns()

        patterns.datePattern.find("03/15/2024") shouldNotBe null
        patterns.datePattern.find("3/1/2024") shouldNotBe null
        patterns.datePattern.find("12-25-2024") shouldNotBe null
        patterns.datePattern.find("Date: 11/22/25") shouldNotBe null
    }

    @Test
    fun `EnglishReceiptPatterns - has correct locale settings`() {
        val patterns = EnglishReceiptPatterns()

        patterns.localeCode shouldBe "en_US"
        patterns.defaultCurrency shouldBe "USD"
        patterns.dateFormats.shouldContain("MM/dd/yyyy")
    }

    // ========== French Pattern Tests ==========

    @Test
    fun `FrenchReceiptPatterns - totalPattern matches French totals`() {
        val patterns = FrenchReceiptPatterns()

        patterns.totalPattern.find("Total 12,99") shouldNotBe null
        patterns.totalPattern.find("MONTANT: 42,50") shouldNotBe null
        patterns.totalPattern.find("Somme 99,99€") shouldNotBe null
    }

    @Test
    fun `FrenchReceiptPatterns - totalGrossPattern matches brut totals`() {
        val patterns = FrenchReceiptPatterns()

        patterns.totalGrossPattern.find("Brut 15,99") shouldNotBe null
        patterns.totalGrossPattern.find("Total TTC: 42,50") shouldNotBe null
    }

    @Test
    fun `FrenchReceiptPatterns - cashPaymentPattern matches espèces`() {
        val patterns = FrenchReceiptPatterns()

        patterns.cashPaymentPattern.matches("Espèces").shouldBeTrue()
        patterns.cashPaymentPattern.matches("ESPECES").shouldBeTrue()
        patterns.cashPaymentPattern.matches("Liquide").shouldBeTrue()
    }

    @Test
    fun `FrenchReceiptPatterns - has correct locale settings`() {
        val patterns = FrenchReceiptPatterns()

        patterns.localeCode shouldBe "fr_FR"
        patterns.defaultCurrency shouldBe "EUR"
        patterns.dateFormats.shouldContain("dd/MM/yyyy")
    }

    // ========== Pattern Extraction Tests ==========

    @Test
    fun `German pattern extracts correct amount from total`() {
        val patterns = GermanReceiptPatterns()
        val match = patterns.totalPattern.find("Summe EUR 123,45")

        match shouldNotBe null
        val amountGroup = match?.groupValues?.get(2)
        amountGroup shouldBe "123,45"
    }

    @Test
    fun `English pattern extracts correct amount from total`() {
        val patterns = EnglishReceiptPatterns()
        val match = patterns.totalPattern.find("Total: $123.45")

        match shouldNotBe null
        // Extract numeric part
        val numericPart = match?.groupValues?.find { it.matches(Regex("\\d+[,.]?\\d+\\.\\d{2}")) }
        numericPart shouldNotBe null
    }

    @Test
    fun `German pattern extracts item name and price`() {
        val patterns = GermanReceiptPatterns()
        val match = patterns.itemPattern.find("Bio-Milch 3,5%                 2,49")

        match shouldNotBe null
        match?.groupValues?.get(1)?.trim() shouldBe "Bio-Milch 3,5%"
        match?.groupValues?.get(2) shouldBe "2,49"
    }

    @Test
    fun `English pattern extracts item name and price`() {
        val patterns = EnglishReceiptPatterns()
        val match = patterns.itemPattern.find("Organic Milk                   $2.99")

        match shouldNotBe null
        match?.groupValues?.get(1)?.trim() shouldBe "Organic Milk"
        match?.groupValues?.get(2) shouldBe "2.99"
    }

    // ========== Edge Cases ==========

    @Test
    fun `patterns are case insensitive`() {
        val germanPatterns = GermanReceiptPatterns()
        val englishPatterns = EnglishReceiptPatterns()

        germanPatterns.totalPattern.find("SUMME 12,99") shouldNotBe null
        germanPatterns.totalPattern.find("summe 12,99") shouldNotBe null
        germanPatterns.totalPattern.find("Summe 12,99") shouldNotBe null

        englishPatterns.totalPattern.find("TOTAL $12.99") shouldNotBe null
        englishPatterns.totalPattern.find("total $12.99") shouldNotBe null
        englishPatterns.totalPattern.find("Total $12.99") shouldNotBe null
    }

    @Test
    fun `patterns handle various whitespace`() {
        val patterns = GermanReceiptPatterns()

        patterns.totalPattern.find("Summe    12,99") shouldNotBe null
        patterns.totalPattern.find("Summe:12,99") shouldNotBe null
        patterns.totalPattern.find("Summe EUR   12,99") shouldNotBe null
    }

    @Test
    fun `patterns handle amounts with spaces`() {
        val patterns = GermanReceiptPatterns()

        patterns.totalPattern.find("Summe 12, 99") shouldNotBe null
        patterns.totalPattern.find("Summe 1 2,99") shouldNotBe null
    }

    // ========== Helper Extensions ==========

    private fun List<String>.shouldContain(value: String) {
        (this.contains(value)).shouldBeTrue()
    }
}