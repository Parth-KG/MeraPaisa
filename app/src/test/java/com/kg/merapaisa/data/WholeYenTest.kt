package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Yen has no decimals, so every amount that lands in yen is whole yen (v3.7.1). Amounts are stored
 * in hundredths for every currency, so whole yen are multiples of 100.
 */
class WholeYenTest {

    private val yen = minorStep("JPY")
    private fun whole(amount: Long) = amount % 100 == 0L

    @Test
    fun `only yen has a step`() {
        assertEquals(100L, minorStep("JPY"))
        assertEquals(100L, minorStep("¥"))
        assertEquals(1L, minorStep("INR"))
        assertEquals(1L, minorStep("USD"))
    }

    @Test
    fun `rounding to whole yen goes to the nearest and keeps the sign`() {
        assertEquals(33_100L, roundToStep(33_086, yen))
        assertEquals(-33_100L, roundToStep(-33_086, yen))
        assertEquals(100L, roundToStep(50, yen))
        assertEquals(-100L, roundToStep(-50, yen))
        assertEquals(12_345L, roundToStep(12_345, 1))
    }

    @Test
    fun `a currency change into yen stores whole yen that still add up`() {
        val rupees = listOf(1_234_56L, -333_33L, 99_99L, 1L)
        val rate = 1.7911
        val converted = convertAll(rupees, rate, yen)
        assertTrue(converted.all(::whole))
        assertEquals(roundToStep(Math.round(rupees.sum() * rate), yen), converted.sum())
        converted.zip(rupees).forEach { (c, r) -> assertTrue("no entry turns the other way", c == 0L || (c > 0) == (r > 0)) }
    }

    @Test
    fun `a currency change into rupees is as before`() {
        val amounts = listOf(33_33L, 33_33L, 33_34L)
        assertEquals(convertAll(amounts, 2.0), convertAll(amounts, 2.0, 1L))
    }

    @Test
    fun `many small entries into yen never turn one the other way`() {
        val amounts = List(30) { 1L } + listOf(-2L)
        val converted = convertAll(amounts, 1.5, yen)
        assertTrue(converted.all(::whole))
        converted.zip(amounts).forEach { (c, r) -> assertTrue(c == 0L || (c > 0) == (r > 0)) }
    }

    @Test
    fun `a yen expense shared three ways is whole yen each`() {
        val shares = evenShares(100_00, listOf(1, 2, 3), yen)
        assertEquals(mapOf(1L to 34_00L, 2L to 33_00L, 3L to 33_00L), shares)
        assertEquals(100_00L, shares.values.sum())
    }

    @Test
    fun `the split's equal shares in yen are whole yen`() {
        val shares = equalSplit(1_000_00, listOf(1, 2, 3), yen)
        assertTrue(shares.values.all(::whole))
        assertEquals(1_000_00L, shares.values.sum())
    }

    @Test
    fun `adjusted shares in yen leave the others whole`() {
        val start = equalSplit(1_000_00, listOf(1, 2, 3), yen)
        val after = redistribute(start, setOf(1), 1, 500_00, 1_000_00, yen)
        assertEquals(500_00L, after[1])
        assertTrue(after.values.all(::whole))
        assertEquals(1_000_00L, after.values.sum())
    }

    @Test
    fun `an older amount with a fraction keeps it, on the first share`() {
        val shares = evenShares(100_40, listOf(1, 2, 3), yen)
        assertEquals(100_40L, shares.values.sum())
        assertEquals(listOf(2L, 3L).map { shares.getValue(it) }.all(::whole), true)
    }

    @Test
    fun `rupees split as before`() {
        assertEquals(evenShares(100_00, listOf(1, 2, 3)), evenShares(100_00, listOf(1, 2, 3), 1))
        assertEquals(mapOf(1L to 33_34L, 2L to 33_33L, 3L to 33_33L), equalSplit(100_00, listOf(1, 2, 3)))
    }
}
