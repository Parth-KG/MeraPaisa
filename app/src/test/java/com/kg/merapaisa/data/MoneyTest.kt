package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun parsesWholeAndFractionalAmounts() {
        assertEquals(0L, parseAmountToMinor("0"))
        assertEquals(0L, parseAmountToMinor("0.00"))
        assertEquals(1000L, parseAmountToMinor("10"))
        assertEquals(1050L, parseAmountToMinor("10.5"))
        assertEquals(1050L, parseAmountToMinor("10.50"))
        assertEquals(1L, parseAmountToMinor("0.01"))
        assertEquals(50L, parseAmountToMinor(".5"))
        assertEquals(1000L, parseAmountToMinor("10."))
    }

    @Test
    fun parsesNegativeAmounts() {
        assertEquals(-1050L, parseAmountToMinor("-10.50"))
        assertEquals(-1L, parseAmountToMinor("-0.01"))
        assertEquals(0L, parseAmountToMinor("-0"))
    }

    @Test
    fun roundsHalfAwayFromZeroAtTheThirdDecimal() {
        assertEquals(1001L, parseAmountToMinor("10.005"))
        assertEquals(-1001L, parseAmountToMinor("-10.005"))
        assertEquals(1000L, parseAmountToMinor("10.004"))
        assertEquals(1001L, parseAmountToMinor("10.006"))
        assertEquals(1L, parseAmountToMinor("0.005"))
        assertEquals(0L, parseAmountToMinor("0.004"))
    }

    @Test
    fun rejectsInputThatIsNotAnAmount() {
        assertNull(parseAmountToMinor(""))
        assertNull(parseAmountToMinor("."))
        assertNull(parseAmountToMinor("-"))
        assertNull(parseAmountToMinor("-."))
        assertNull(parseAmountToMinor("1.2.3"))
        assertNull(parseAmountToMinor("12a"))
        assertNull(parseAmountToMinor("1234567890123456"))
    }

    @Test
    fun formatsWithTheCurrencySymbolAndSign() {
        assertEquals("₹10.50", formatMinor(1050L, "INR"))
        assertEquals("-₹10.50", formatMinor(-1050L, "INR"))
        assertEquals("₹0.00", formatMinor(0L, "INR"))
        assertEquals("₹0.05", formatMinor(5L, "INR"))
        assertEquals("\$1234.00", formatMinor(123400L, "USD"))
        assertEquals("£0.01", formatMinor(1L, "GBP"))
    }

    @Test
    fun formatsZeroDecimalCurrenciesWithoutDecimals() {
        // Stored in hundredths like everything else; only the display drops the decimals.
        assertEquals("¥12", formatMinor(1234L, "JPY"))
        assertEquals("¥13", formatMinor(1250L, "JPY"))
        assertEquals("-¥13", formatMinor(-1250L, "JPY"))
        assertEquals("¥0", formatMinor(0L, "JPY"))
    }

    @Test
    fun parseAndFormatRoundTrip() {
        listOf("0.00", "0.01", "10.50", "1.99", "999999.99", "-0.01", "-10.50", "-999999.99")
            .forEach { text ->
                val minor = parseAmountToMinor(text)
                    ?: throw AssertionError("failed to parse $text")
                assertEquals(text, formatMinorPlain(minor, "INR"))
            }
    }

    @Test
    fun normalisesLegacySymbolsAndPassesCodesThrough() {
        assertEquals("INR", normaliseCurrency("₹"))
        assertEquals("USD", normaliseCurrency("$"))
        assertEquals("EUR", normaliseCurrency("€"))
        assertEquals("GBP", normaliseCurrency("£"))
        assertEquals("JPY", normaliseCurrency("¥"))
        assertEquals("INR", normaliseCurrency("INR"))
        assertEquals("USD", normaliseCurrency("USD"))
    }

    @Test
    fun everySupportedCurrencyHasASymbol() {
        SUPPORTED_CURRENCIES.forEach { code ->
            assertEquals("$code should map to a symbol, not fall back to its code", true, currencySymbol(code) != code)
        }
    }

    @Test
    fun numpadKeysBuildOnlyParseableEntries() {
        assertEquals("5", appendAmountKey("", "5"))
        assertEquals("0.", appendAmountKey("", "."))
        assertEquals("5", appendAmountKey("0", "5"))
        assertEquals("10.", appendAmountKey("10", "."))
        assertEquals("10.5", appendAmountKey("10.", "5"))
        assertEquals("10.", appendAmountKey("10.", "."))
        assertEquals("10.5", appendAmountKey("10.5", "."))
        assertEquals("10", appendAmountKey("10.", "\u232b"))
    }

    @Test
    fun numpadCapsWholeDigitsAndDecimalPlaces() {
        assertEquals("123456789", appendAmountKey("123456789", "1"))
        assertEquals("10.50", appendAmountKey("10.5", "0"))
        assertEquals("10.50", appendAmountKey("10.50", "7"))
    }

    @Test
    fun numpadCannotReachRepeatedLeadingZeroes() {
        var entry = ""
        repeat(8) { entry = appendAmountKey(entry, "0") }
        assertEquals("0", entry)
        assertEquals(0L, parseAmountToMinor(entry))
    }

    @Test
    fun everyNumpadSequenceStaysParseableOrEmpty() {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "\u232b")
        var entry = ""
        // Walk a long deterministic key sequence and check the field never becomes unparseable.
        repeat(500) { i ->
            entry = appendAmountKey(entry, keys[(i * 7) % keys.size])
            if (entry.isNotEmpty()) {
                assertNotNull("entry $entry stopped parsing", parseAmountToMinor(entry))
            }
        }
    }

    @Test
    fun usableAmountRejectsBlankZeroAndLoneDot() {
        assertEquals(false, isUsableAmount(""))
        assertEquals(false, isUsableAmount("."))
        assertEquals(false, isUsableAmount("0"))
        assertEquals(false, isUsableAmount("0.00"))
        assertEquals(true, isUsableAmount("0.01"))
        assertEquals(true, isUsableAmount("10.50"))
    }
}
