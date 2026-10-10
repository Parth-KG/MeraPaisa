package com.kg.merapaisa.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The amount formatter, which is the only thing on screen that has to be exactly right.
 *
 * Grouping is hand-rolled rather than taken from java.text or android.icu, and these tests are
 * the reason: a desktop JVM groups INR in threes, so a test written against the platform would
 * agree with itself and disagree with the phone. Every expectation here is written out by hand.
 */
class AmountTest {

    private fun plain(minor: Long, code: String, style: SignStyle = SignStyle.Always) =
        amountParts(minor, code, style).plain

    // -----------------------------------------------------------------------------------------
    // Indian grouping
    // -----------------------------------------------------------------------------------------

    @Test
    fun `rupees group in lakhs, not thousands`() {
        assertEquals("₹1,234", plain(1_234_00, "INR"))
        assertEquals("₹12,345", plain(12_345_00, "INR"))
        assertEquals("₹1,23,456", plain(1_23_456_00, "INR"))
        assertEquals("₹12,34,567", plain(12_34_567_00, "INR"))
        assertEquals("₹1,23,45,678", plain(1_23_45_678_00, "INR"))
        assertEquals("₹12,34,56,789", plain(12_34_56_789_00, "INR"))
    }

    @Test
    fun `the lakh figure from the fixtures reads correctly`() {
        assertEquals("₹12,34,567.50", plain(12_34_567_50, "INR"))
    }

    @Test
    fun `short rupee amounts are not grouped at all`() {
        assertEquals("₹1", plain(1_00, "INR"))
        assertEquals("₹12", plain(12_00, "INR"))
        assertEquals("₹999", plain(999_00, "INR"))
        assertEquals("₹1,000", plain(1_000_00, "INR"))
    }

    // -----------------------------------------------------------------------------------------
    // Western grouping
    // -----------------------------------------------------------------------------------------

    @Test
    fun `other currencies group in threes`() {
        assertEquals("$1,234", plain(1_234_00, "USD"))
        assertEquals("$12,345", plain(12_345_00, "USD"))
        assertEquals("$123,456", plain(123_456_00, "USD"))
        assertEquals("$1,234,567", plain(1_234_567_00, "USD"))
        assertEquals("£12,345,678", plain(12_345_678_00, "GBP"))
    }

    @Test
    fun `the same number groups differently in the two systems`() {
        val minor = 1_23_45_678_00L
        assertEquals("₹1,23,45,678", plain(minor, "INR"))
        assertEquals("$12,345,678", plain(minor, "USD"))
    }

    // -----------------------------------------------------------------------------------------
    // Decimals
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a whole amount shows no decimals`() {
        assertEquals("₹1,200", plain(1_200_00, "INR"))
        assertEquals("", amountParts(1_200_00, "INR").fraction)
    }

    @Test
    fun `paise show as two digits, trailing zero kept`() {
        assertEquals("₹237.40", plain(237_40, "INR"))
        assertEquals("₹237.04", plain(237_04, "INR"))
        assertEquals("₹0.05", plain(5, "INR"))
    }

    /** A column has to reserve the slot, or the decimal points wander. */
    @Test
    fun `a column can force the decimal slot open`() {
        assertEquals("00", amountParts(1_200_00, "INR", forceFraction = true).fraction)
        assertEquals("₹1,200.00", amountParts(1_200_00, "INR", forceFraction = true).plain)
        assertEquals("40", amountParts(237_40, "INR", forceFraction = true).fraction)
    }

    @Test
    fun `yen has no minor unit, so it never shows decimals`() {
        assertEquals("\u2212\u2009¥12,000", plain(-12_000_00, "JPY"))
        assertEquals("", amountParts(12_000_00, "JPY").fraction)
        assertEquals("", amountParts(12_000_00, "JPY", forceFraction = true).fraction)
    }

    /** Stored in hundredths like everything else, so display has to round rather than truncate. */
    @Test
    fun `yen rounds to the nearest whole unit`() {
        assertEquals("¥1", plain(50, "JPY"))
        assertEquals("¥1", plain(149, "JPY"))
        assertEquals("¥2", plain(150, "JPY"))
        assertEquals("¥1,235", plain(1_234_60, "JPY"))
    }

    // -----------------------------------------------------------------------------------------
    // Direction
    // -----------------------------------------------------------------------------------------

    /**
     * U+2212, not a hyphen, which is drawn shorter and higher and reads as a dash. Followed by a
     * thin space so it does not fuse with the currency symbol. Nothing on money owed to you.
     */
    @Test
    fun `the minus is a real minus sign and a plus is never shown`() {
        assertEquals("\u2212\u2009", amountParts(-40_00, "INR").sign)
        assertEquals("", amountParts(40_00, "INR").sign)
        assertEquals("", amountParts(0, "INR").sign)
    }

    @Test
    fun `zero carries no sign`() {
        assertEquals("₹0", plain(0, "INR"))
        assertEquals("$0", plain(0, "USD"))
    }

    @Test
    fun `sign style can drop the minus too`() {
        assertEquals("₹1,200", plain(1_200_00, "INR", SignStyle.NegativeOnly))
        assertEquals("\u2212\u2009₹40", plain(-40_00, "INR", SignStyle.NegativeOnly))
        assertEquals("₹40", plain(-40_00, "INR", SignStyle.None))
    }

    // -----------------------------------------------------------------------------------------
    // Edges
    // -----------------------------------------------------------------------------------------

    @Test
    fun `nine digit amounts group correctly in both systems`() {
        assertEquals("₹99,99,99,999", plain(99_99_99_999_00, "INR"))
        assertEquals("$999,999,999", plain(999_999_999_00, "USD"))
    }

    /**
     * Long.MIN_VALUE has no positive counterpart, so negating it leaves it negative and an
     * ordinary magnitude-then-divide produces a negative body behind a minus sign. It cannot come
     * from the keypad or a link, both of which are bounded, but a restored backup is read from a
     * file, and a figure the app cannot render is worse than one it can.
     */
    @Test
    fun `the most negative Long still renders as a figure`() {
        val parts = amountParts(Long.MIN_VALUE, "INR")
        assertEquals("\u2212\u2009", parts.sign)
        assertEquals(1, parts.plain.count { it == '−' })
        assertEquals(false, parts.integer.contains('-'))
        assertEquals("92,23,37,20,36,85,47,758", parts.integer)
    }

    @Test
    fun `an unknown currency falls back to its code`() {
        assertEquals("CHF1,200", plain(1_200_00, "CHF"))
    }

    /** Older rows store the symbol where newer ones store the code, and both mean rupees. */
    @Test
    fun `a legacy rupee symbol groups in lakhs like the code`() {
        assertEquals("₹12,34,567", plain(12_34_567_00, "₹"))
    }

    // -----------------------------------------------------------------------------------------
    // Spoken
    // -----------------------------------------------------------------------------------------

    /** TalkBack has no name for U+2212, so direction has to be carried by words. */
    @Test
    fun `a balance is spoken with its direction in words`() {
        assertEquals("Asha owes you 1,200 rupees", amountSpoken(1_200_00, "INR", "Asha"))
        assertEquals("you owe Bilal 40 rupees", amountSpoken(-40_00, "INR", "Bilal"))
        assertEquals("Farid, even", amountSpoken(0, "INR", "Farid"))
    }

    @Test
    fun `a total is spoken without a name`() {
        assertEquals("owed to you, 1,200 rupees", amountSpoken(1_200_00, "INR"))
        assertEquals("you owe 12,000 yen", amountSpoken(-12_000_00, "JPY"))
        assertEquals("even", amountSpoken(0, "INR"))
    }

    @Test
    fun `spoken figures keep their grouping and decimals`() {
        assertEquals("Asha owes you 12,34,567.50 rupees", amountSpoken(12_34_567_50, "INR", "Asha"))
    }

    // -----------------------------------------------------------------------------------------
    // Whether a column keeps a decimal slot
    // -----------------------------------------------------------------------------------------

    @Test
    fun `an amount shows a fraction only when it has paise`() {
        assertEquals(true, showsFraction(237_40, "INR"))
        assertEquals(true, showsFraction(-40_05, "USD"))
        assertEquals(false, showsFraction(1_200_00, "INR"))
        assertEquals(false, showsFraction(0, "INR"))
        assertEquals(true, showsFraction(Long.MIN_VALUE, "INR"))
    }

    @Test
    fun `yen never shows a fraction, though it is kept in hundredths`() {
        // A split can leave ¥33.34 in an entry; it is drawn as 33.
        assertEquals(false, showsFraction(33_34, "JPY"))
        assertEquals(false, showsFraction(12_000_00, "JPY"))
    }

    @Test
    fun `a column of whole amounts keeps no slot`() {
        assertEquals(false, columnShowsFraction(listOf(1_200_00L, -40_00L, 0L), "INR"))
        assertEquals(false, columnShowsFraction(listOf(1_200_00L to "INR", 33_34L to "JPY")))
        assertEquals(false, columnShowsFraction(emptyList<Long>(), "INR"))
    }

    @Test
    fun `one amount with paise opens the slot for the whole column`() {
        assertEquals(true, columnShowsFraction(listOf(1_200_00L, 75_50L), "INR"))
        assertEquals(true, columnShowsFraction(listOf(-12_000_00L to "JPY", 1_050_25L to "USD")))
    }
}
