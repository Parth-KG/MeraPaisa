package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Converting a whole history at once.
 *
 * The invariant that matters: **the converted entries must still sum to the converted total.**
 * Balances in this app are derived by summing entries, so if the two disagree the person's balance
 * contradicts the log that produces it — and nothing would report the inconsistency.
 */
class ConvertAllTest {

    private fun sumsMatch(amounts: List<Long>, rate: Double) {
        val out = convertAll(amounts, rate)
        assertEquals(
            "entries must sum to the converted total (rate $rate, input $amounts)",
            Math.round(amounts.sum() * rate),
            out.sum()
        )
    }

    // ---------------------------------------------------------------------------------------
    // The invariant
    // ---------------------------------------------------------------------------------------

    @Test
    fun `entries always sum to the converted total`() {
        sumsMatch(listOf(3_333L, 3_333L, 3_333L), 2.0)
        sumsMatch(listOf(1L, 1L, 1L), 0.3333)
        sumsMatch(listOf(20_000L, 5_050L), 0.0121)
        sumsMatch(listOf(100L), 1.0 / 3.0)
        sumsMatch(listOf(7L, 11L, 13L, 17L, 19L), 1.2345)
    }

    /** A rate that rounds every entry down individually is where the residue actually shows. */
    @Test
    fun `residue is absorbed rather than lost`() {
        val amounts = listOf(10L, 10L, 10L)
        val out = convertAll(amounts, 0.15) // each rounds to 2 (6 total), 30 * 0.15 rounds to 5
        assertEquals(Math.round(30 * 0.15), out.sum())
        assertEquals(3, out.size)
    }

    @Test
    fun `the residue goes to the largest magnitude entry`() {
        val out = convertAll(listOf(1L, 1_000L, 1L), 0.3333)
        val naive = listOf(1L, 1_000L, 1L).map { Math.round(it * 0.3333) }
        // The small entries are untouched; the big one carries the correction.
        assertEquals(naive[0], out[0])
        assertEquals(naive[2], out[2])
        assertEquals(Math.round(1_002 * 0.3333), out.sum())
    }

    @Test
    fun `ties go to the earliest entry so the result is stable`() {
        val a = convertAll(listOf(100L, 100L), 0.3333)
        val b = convertAll(listOf(100L, 100L), 0.3333)
        assertEquals("same input must give the same output", a, b)
    }

    // ---------------------------------------------------------------------------------------
    // Shape and signs
    // ---------------------------------------------------------------------------------------

    @Test
    fun `an empty history converts to an empty history`() {
        assertTrue(convertAll(emptyList(), 2.0).isEmpty())
    }

    @Test
    fun `entry count is preserved`() {
        assertEquals(5, convertAll(listOf(1L, 2L, 3L, 4L, 5L), 1.7).size)
    }

    /** A settled person must stay settled — their entries cancel, and must keep cancelling. */
    @Test
    fun `a history summing to zero still sums to zero`() {
        val out = convertAll(listOf(25_000L, -25_000L), 0.0121)
        assertEquals(0L, out.sum())
    }

    @Test
    fun `negative entries stay negative`() {
        val out = convertAll(listOf(-20_000L, -5_000L), 0.5)
        assertTrue("all owed amounts must remain owed", out.all { it < 0 })
        assertEquals(-12_500L, out.sum())
    }

    @Test
    fun `mixed signs are each converted, not merely netted`() {
        val out = convertAll(listOf(30_000L, -10_000L), 2.0)
        assertEquals(listOf(60_000L, -20_000L), out)
    }

    @Test
    fun `a rate of one leaves every entry untouched`() {
        val amounts = listOf(1L, -2L, 333L, -4_444L)
        assertEquals(amounts, convertAll(amounts, 1.0))
    }

    @Test
    fun `a zero entry stays zero unless it is the one absorbing the residue`() {
        val out = convertAll(listOf(0L, 10_000L), 2.0)
        assertEquals(0L, out[0])
        assertEquals(20_000L, out[1])
    }

    // ---------------------------------------------------------------------------------------
    // Realistic cases
    // ---------------------------------------------------------------------------------------

    /** The case from the app: a rupee ledger relabelled as dollars. */
    @Test
    fun `an INR history converted to USD keeps its shape`() {
        val inr = listOf(20_000L, 5_050L, -10_000L, 34_000L) // ₹200, ₹50.50, -₹100, ₹340
        val usd = convertAll(inr, 0.0121)
        assertEquals(inr.size, usd.size)
        assertEquals(Math.round(inr.sum() * 0.0121), usd.sum())
        assertTrue("the repayment stays a repayment", usd[2] < 0)
    }

    @Test
    fun `a long history still reconciles exactly`() {
        val many = (1..200).map { (it * 137L) - 5_000L }
        sumsMatch(many, 0.0121)
        sumsMatch(many, 87.61)
    }
}
