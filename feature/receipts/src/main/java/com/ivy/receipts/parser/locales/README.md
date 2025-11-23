# Receipt Parser Localization

This package contains locale-specific receipt parsing patterns for the OCR receipt feature.

## Architecture

The parser uses a strategy pattern to support multiple locales:

- **ReceiptPatterns**: Interface defining patterns that all locale implementations must provide
- **Locale implementations**: Concrete classes implementing `ReceiptPatterns` for specific locales
- **RegexMlkitParser**: Main parser that uses these patterns and can auto-detect locale

## Supported Locales

Currently supported locales:

| Locale | Class | Currency | Decimal Sep | Thousands Sep |
|--------|-------|----------|-------------|---------------|
| German (de_DE) | `GermanReceiptPatterns` | EUR | `,` | `.` |
| English (en_US) | `EnglishReceiptPatterns` | USD | `.` | `,` |
| French (fr_FR) | `FrenchReceiptPatterns` | EUR | `,` | ` ` (space) |

## Auto-Detection

The parser automatically detects the locale by:

1. **Keyword frequency**: Counting locale-specific keywords (e.g., "summe" for German, "total" for English)
2. **Decimal separator analysis**: Checking whether commas or periods are used for decimals
3. **Scoring system**: Combining both signals to pick the most likely locale

Default behavior uses auto-detection, but you can specify a locale explicitly.

## Usage

### Default (Auto-Detection)

```kotlin
val parser = RegexMlkitParser() // Auto-detects locale from receipt text
val result = parser.parse(ocrBlocks)
```

### Explicit Locale

```kotlin
// Using factory method
val germanParser = RegexMlkitParser.forLocale("de_DE")
val englishParser = RegexMlkitParser.forLocale("en_US")

// Or passing patterns directly
val parser = RegexMlkitParser(GermanReceiptPatterns())
val result = parser.parse(ocrBlocks)
```

## Adding a New Locale

To add support for a new locale:

### 1. Create Pattern Class

Create a new file in this package implementing `ReceiptPatterns`:

```kotlin
package com.ivy.receipts.parser.locales

import com.ivy.receipts.parser.ReceiptPatterns

class SpanishReceiptPatterns : ReceiptPatterns {
    // Pattern for net amounts (optional)
    override val netPattern = Regex("(?i).*\\b(neto)\\b.*?(\\d+[,.]\\d+)")

    // Pattern for cash payment (optional)
    override val cashPaymentPattern = Regex("(?i).*\\b(efectivo)\\b.*")

    // Pattern for line items (optional but recommended)
    override val itemPattern = Regex("(.+?)\\s+(\\d+,\\d{2})\\s*€?\\s*$")

    // Pattern for total amount (required)
    override val totalPattern = Regex("(?i)\\b(total|importe)\\b\\s*:?\\s*(\\d+\\s*,\\s*\\d{2})")

    // Pattern for gross total (optional)
    override val totalGrossPattern = Regex("(?i)\\b(total bruto)\\b\\s*:?\\s*(\\d+[,.]\\d{2})")

    // Pattern for dates (required)
    override val datePattern = Regex("\\b(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})\\b")

    // Date format strings for parsing (required)
    override val dateFormats = listOf(
        "dd/MM/yyyy",
        "dd-MM-yyyy"
    )

    // Default currency (required)
    override val defaultCurrency = "EUR"

    // Locale code (required)
    override val localeCode = "es_ES"

    companion object {
        const val DECIMAL_SEPARATOR = ','
        const val THOUSANDS_SEPARATOR = '.'
    }
}
```

### 2. Update RegexMlkitParser

Add your locale to the factory method in `RegexMlkitParser.kt`:

```kotlin
fun forLocale(localeCode: String): RegexMlkitParser {
    val patterns = when (localeCode.lowercase()) {
        "de", "de_de", "german" -> GermanReceiptPatterns()
        "en", "en_us", "en_gb", "english" -> EnglishReceiptPatterns()
        "es", "es_es", "spanish" -> SpanishReceiptPatterns() // Add this
        else -> GermanReceiptPatterns() // Default
    }
    return RegexMlkitParser(patterns)
}
```

### 3. Update Auto-Detection (Optional)

Add locale-specific keywords to the `detectLocale()` method:

```kotlin
// Spanish indicators
val spanishScore = listOf(
    "total", "importe", "efectivo", "tarjeta", "gracias",
    "iva", "neto", "bruto", "recibo"
).count { fullText.contains(it) }
```

### 4. Update Separator Helpers

Add your patterns class to the separator helper methods:

```kotlin
private fun getDecimalSeparator(patterns: ReceiptPatterns): Char {
    return when (patterns) {
        is GermanReceiptPatterns -> GermanReceiptPatterns.DECIMAL_SEPARATOR
        is EnglishReceiptPatterns -> EnglishReceiptPatterns.DECIMAL_SEPARATOR
        is SpanishReceiptPatterns -> SpanishReceiptPatterns.DECIMAL_SEPARATOR
        else -> '.'
    }
}
```

## Pattern Tips

### Regex Best Practices

1. **Use case-insensitive matching**: Add `(?i)` at the start of patterns
2. **Use word boundaries**: Use `\\b` to match whole words only
3. **Handle whitespace**: Use `\\s*` or `\\s+` for flexible whitespace matching
4. **Capture groups**: Use parentheses to capture the parts you need
5. **Optional elements**: Use `?` for optional parts

### Common Patterns

**Total amounts:**
```kotlin
Regex("(?i)\\b(total|sum)\\b\\s*:?\\s*(\\d+[,.]\\d{2})")
```

**Dates (DD/MM/YYYY or MM/DD/YYYY):**
```kotlin
Regex("\\b(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})\\b")
```

**Line items:**
```kotlin
Regex("(.+?)\\s+(\\d+[,.]\\d{2})\\s*$")
```

### Testing Your Patterns

Create unit tests with sample receipt text from your target locale:

```kotlin
@Test
fun `test Spanish receipt parsing`() {
    val parser = RegexMlkitParser(SpanishReceiptPatterns())
    val sampleReceipt = """
        Supermercado ABC
        01/03/2024

        Pan              2,50
        Leche            1,80

        Total           4,30 €
    """.trimIndent()

    // ... test assertions
}
```

## Contributing

When adding a new locale:

1. Research common receipt formats from that locale
2. Collect sample receipts for testing
3. Implement the patterns class
4. Add unit tests
5. Update this README
6. Consider regional variations (e.g., en_US vs en_GB)

## Resources

- [Receipt format examples by country](https://en.wikipedia.org/wiki/Receipt)
- [Decimal separator by locale](https://en.wikipedia.org/wiki/Decimal_separator)
- [Date format by locale](https://en.wikipedia.org/wiki/Date_format_by_country)
- [Kotlin Regex documentation](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/-regex/)