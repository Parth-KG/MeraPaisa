package com.kg.merapaisa.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rate service rejects a negative `amount` with HTTP 422 "invalid amount". Balances here
 * are signed — negative is what you owe — so the request carries the magnitude and the sign is
 * restored afterwards. Sending the raw figure failed for every person you owed, and reported it
 * as a connection problem.
 */
class ExchangeRateApiTest {

    @Test
    fun `the amount asked about is never negative`() {
        val owed = requestAmountMajor(50_000L)
        val owing = requestAmountMajor(-50_000L)

        assertEquals(500.0, owed, 0.0)
        assertEquals(500.0, owing, 0.0)
    }

    @Test
    fun `every plausible balance is requested as a magnitude`() {
        val balances = listOf(
            -99_999_999_900L, -1_000_000_000L, -100_000L, -1L,
            0L, 1L, 100_000L, 1_000_000_000L, 99_999_999_900L
        )

        balances.forEach { balance ->
            assertTrue(
                "balance $balance was asked about as a negative amount",
                requestAmountMajor(balance) >= 0.0
            )
        }
    }

    @Test
    fun `converting what you are owed stays owed`() {
        // ₹500 owed to you, converted at a rate that makes it $6.
        assertEquals(600L, convertedMinor(amountMinor = 50_000L, convertedMagnitudeMajor = 6.0))
    }

    @Test
    fun `converting what you owe stays owed by you`() {
        // The whole point: the sign survives a request that could not carry it.
        assertEquals(-600L, convertedMinor(amountMinor = -50_000L, convertedMagnitudeMajor = 6.0))
    }

    @Test
    fun `a zero balance converts to zero rather than to minus zero`() {
        assertEquals(0L, convertedMinor(amountMinor = 0L, convertedMagnitudeMajor = 0.0))
    }

    @Test
    fun `the converted amount is rounded to the nearest minor unit`() {
        // 6.005 major is 600.5 minor, which rounds away from zero on both sides.
        assertEquals(601L, convertedMinor(amountMinor = 50_000L, convertedMagnitudeMajor = 6.005))
        assertEquals(-601L, convertedMinor(amountMinor = -50_000L, convertedMagnitudeMajor = 6.005))
    }

    @Test
    fun `a large balance keeps its sign and its magnitude`() {
        // ₹1 crore owed. Doubles render this as 1.0E7, which the service does accept.
        assertEquals(
            -1_000_000_000L,
            convertedMinor(amountMinor = -1_000_000_000L, convertedMagnitudeMajor = 10_000_000.0)
        )
    }
}
