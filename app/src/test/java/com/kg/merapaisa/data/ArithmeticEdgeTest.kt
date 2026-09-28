package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic at the edges, where a ledger stops being believable.
 *
 * Money here is a `Long` of minor units, and every bug this file is about has the same shape: a
 * total that no longer equals the sum of its parts. Nothing on screen looks wrong when that
 * happens — each individual number is plausible — which is exactly why it has to be pinned by
 * tests rather than noticed.
 */
class ArithmeticEdgeTest {

    // -----------------------------------------------------------------------------------------
    // evenShares: the parts must always add back to the whole
    // -----------------------------------------------------------------------------------------

    @Test
    fun `shares always sum to the amount, for every size and remainder`() {
        for (people in 1..12) {
            for (amount in listOf(0L, 1L, 7L, 99L, 100L, 101L, 10_001L, 999_999L)) {
                val ids = (1L..people).toList()
                val shares = evenShares(amount, ids)
                assertEquals(
                    "$amount split $people ways did not add back up",
                    amount,
                    shares.values.sum()
                )
            }
        }
    }

    @Test
    fun `a negative amount still splits into parts that sum to it`() {
        for (people in 1..8) {
            val shares = evenShares(-10_001L, (1L..people).toList())
            assertEquals(-10_001L, shares.values.sum())
        }
    }

    /**
     * A repeated member must not silently eat somebody's share.
     *
     * The split is built with `associateWith`, which collapses duplicate keys — so a list with the
     * same person twice produced a map whose values no longer added up to the expense. Nothing
     * downstream checks that, so the group's balances would simply be wrong by the difference, and
     * every individual figure would still look reasonable.
     */
    @Test
    fun `a duplicated member does not break the sum`() {
        val shares = evenShares(30_000L, listOf(1L, 1L, 2L, 3L))
        assertEquals("one entry per distinct person", 3, shares.size)
        assertEquals("and it still adds up", 30_000L, shares.values.sum())
    }

    @Test
    fun `an empty membership splits to nothing rather than crashing`() {
        assertEquals(emptyMap<Long, Long>(), evenShares(5_000L, emptyList()))
    }

    // -----------------------------------------------------------------------------------------
    // Formatting: a figure must never render as nonsense
    // -----------------------------------------------------------------------------------------

    /**
     * `Long.MIN_VALUE.absoluteValue` is still negative — there is no positive counterpart — so a
     * formatter that takes the magnitude and divides produces a negative body behind a minus sign.
     * It cannot arrive from the keypad or a share link, both of which are bounded, but a restored
     * backup is read straight from a file.
     */
    @Test
    fun `the most negative Long does not render as a nonsense figure`() {
        val shown = formatMinor(Long.MIN_VALUE, "INR")
        assertTrue("got: $shown", shown.count { it == '-' } <= 1)
        assertTrue("a figure must not contain a negative body: $shown", !shown.contains("-9223372036854775808"))
    }

    @Test
    fun `plain formatting survives the same value`() {
        val shown = formatMinorPlain(Long.MIN_VALUE, "INR")
        assertTrue("got: $shown", shown.count { it == '-' } <= 1)
    }

    // -----------------------------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------------------------

    @Test
    fun `an amount too large to hold is refused rather than wrapped`() {
        assertEquals(null, parseAmountToMinor("9".repeat(16)))
        assertEquals(null, parseAmountToMinor("-" + "9".repeat(16)))
    }

    @Test
    fun `the largest accepted amount still round trips`() {
        val minor = parseAmountToMinor("9".repeat(15))
        assertTrue(minor != null && minor > 0)
        assertEquals(minor, parseAmountToMinor(formatMinorPlain(minor!!, "INR")))
    }

    // -----------------------------------------------------------------------------------------
    // settleUp and directTransfers agree with the positions they claim to square
    // -----------------------------------------------------------------------------------------

    @Test
    fun `settle up squares every member for a spread of awkward balances`() {
        val cases = listOf(
            listOf(1L to 1L, 2L to -1L),
            listOf(1L to 10_001L, 2L to -3_334L, 3L to -3_334L, 4L to -3_333L),
            listOf(1L to 0L, 2L to 0L),
            listOf(1L to 7L, 2L to 11L, 3L to -18L)
        )
        cases.forEach { case ->
            val balances = case.map { MemberBalance(it.first, it.second) }
            val after = balances.associate { it.personId to it.amountMinor }.toMutableMap()
            settleUp(balances).forEach { t ->
                after[t.fromPersonId] = (after[t.fromPersonId] ?: 0L) + t.amountMinor
                after[t.toPersonId] = (after[t.toPersonId] ?: 0L) - t.amountMinor
            }
            after.forEach { (p, b) -> assertEquals("person $p left unsquared in $case", 0L, b) }
        }
    }
}
