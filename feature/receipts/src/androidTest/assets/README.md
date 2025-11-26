# Test Receipt Images

This directory contains sample receipt images for integration testing.

## Adding Test Images

To test the OCR parser with real images:

1. Take photos of receipts or download sample receipt images
2. Save them in this directory with descriptive names:
   - `german_receipt_rewe.jpg` - German supermarket receipt
   - `german_receipt_aldi.jpg` - German discount store receipt
   - `us_receipt_walmart.jpg` - US retail receipt
   - `us_receipt_starbucks.jpg` - US coffee shop receipt
   - etc.

3. Update the test file `ReceiptImageParsingTest.kt` to load your images:

```kotlin
@Test
fun testParseRealGermanReceipt() = runTest {
    val inputStream = context.assets.open("german_receipt_rewe.jpg")
    val bitmap = BitmapFactory.decodeStream(inputStream)
    inputStream.close()

    val ocrBlocks = performOcr(bitmap)
    val result = parser.parse(ocrBlocks)

    result.total shouldBeGreaterThan 0.0
    result.currency shouldBe "EUR"
}
```

## Image Guidelines

For best OCR results:

- **Resolution**: At least 1000x1500 pixels
- **Format**: JPG or PNG
- **Lighting**: Even, well-lit, no shadows
- **Angle**: Straight-on, not skewed
- **Focus**: Sharp, not blurry
- **Content**: Full receipt visible, including total and date

## Privacy Note

**Do not commit real receipt images with personal information to version control!**

Add to `.gitignore`:
```
*.jpg
*.png
*.jpeg
```

Keep test images local or use anonymized/synthetic receipts for CI/CD.

## Running Tests

### Unit Tests (No Images Required)
```bash
./gradlew :feature:receipts:test
```

### Integration Tests (Requires Android Device/Emulator)
```bash
./gradlew :feature:receipts:connectedAndroidTest
```

## Sample Image Sources

You can find sample receipt images for testing from:

- [Receipt templates](https://www.template.net/business/receipt-templates/)
- [Mockup generators](https://mockuphone.com/)
- Create synthetic receipts with tools like Photoshop or Canva
- Take photos of your own receipts (anonymize first!)
