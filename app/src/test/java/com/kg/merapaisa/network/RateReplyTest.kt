package com.kg.merapaisa.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Frankfurter's reply has to look like before its rate is used.
 *
 * A rate is written into a person's whole history when their currency changes, and into every
 * share of a split across currencies, so a wrong one is worse than none: none says "couldn't get
 * today's rate", a wrong one quietly changes what people owe.
 */
class RateReplyTest {

    /** Exactly what api.frankfurter.dev/v1/latest?base=INR&symbols=USD answered on 11 Nov 2026. */
    private val real = """{"amount":1.0,"base":"INR","date":"2026-11-11","rates":{"USD":0.010673}}"""

    @Test
    fun `the real reply gives its rate`() {
        assertEquals(0.010673, parseRate(real, "INR", "USD")!!, 0.0)
    }

    @Test
    fun `a reply in another base is refused`() {
        val inEuros = """{"amount":1.0,"base":"EUR","date":"2026-11-11","rates":{"USD":1.0873}}"""
        assertNull("a euro rate must never be taken for the rupee one", parseRate(inEuros, "INR", "USD"))
    }

    @Test
    fun `a reply with no base is refused`() {
        assertNull(parseRate("""{"rates":{"USD":0.010673}}""", "INR", "USD"))
    }

    @Test
    fun `a reply without the asked-for rate is refused`() {
        assertNull(parseRate("""{"base":"INR","rates":{"EUR":0.0098}}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR","rates":{}}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR"}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR","rates":[0.010673]}""", "INR", "USD"))
    }

    @Test
    fun `a rate that is zero, negative or not a number is refused`() {
        assertNull(parseRate("""{"base":"INR","rates":{"USD":0}}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR","rates":{"USD":0.0}}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR","rates":{"USD":-0.010673}}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR","rates":{"USD":"0.010673"}}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR","rates":{"USD":null}}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR","rates":{"USD":true}}""", "INR", "USD"))
    }

    @Test
    fun `a rate too large to be finite is refused`() {
        assertNull(parseRate("""{"base":"INR","rates":{"USD":1e400}}""", "INR", "USD"))
    }

    @Test
    fun `exponent notation is a number like any other`() {
        assertEquals(0.010673, parseRate("""{"base":"INR","rates":{"USD":1.0673E-2}}""", "INR", "USD")!!, 1e-12)
        assertEquals(150.0, parseRate("""{"base":"USD","rates":{"JPY":1.5e+2}}""", "USD", "JPY")!!, 0.0)
    }

    @Test
    fun `key order, spacing and other currencies do not matter`() {
        val shuffled = """
            {
              "rates" : { "EUR" : 0.0098 , "USD" : 0.010673 , "GBP" : 0.0083 },
              "date" : "2026-11-11",
              "base" : "INR",
              "amount" : 1.0
            }
        """.trimIndent()
        assertEquals(0.010673, parseRate(shuffled, "INR", "USD")!!, 0.0)
    }

    @Test
    fun `escaped characters in other fields are read, not tripped over`() {
        val escaped = """{"note":"a \"quoted\" \\ \/ ₹ line\n","base":"INR","rates":{"USD":0.010673}}"""
        assertEquals(0.010673, parseRate(escaped, "INR", "USD")!!, 0.0)
    }

    @Test
    fun `anything that is not a JSON object is refused`() {
        assertNull(parseRate("", "INR", "USD"))
        assertNull(parseRate("   ", "INR", "USD"))
        assertNull(parseRate("<html><body>502 Bad Gateway</body></html>", "INR", "USD"))
        assertNull(parseRate("""{"message":"not found"}""", "INR", "USD"))
        assertNull(parseRate("[1,2,3]", "INR", "USD"))
        assertNull(parseRate("0.010673", "INR", "USD"))
        assertNull(parseRate("null", "INR", "USD"))
    }

    @Test
    fun `broken JSON is refused rather than half read`() {
        assertNull(parseRate("""{"base":"INR","rates":{"USD":0.010673}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR","rates":{"USD":0.010673}}}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR","rates":{"USD":0.010673,}}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR","rates":{"USD":.010673}}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR","rates":{"USD":00.1}}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR","rates":{"USD":0.010673}} trailing""", "INR", "USD"))
        assertNull(parseRate("""{base:"INR","rates":{"USD":0.010673}}""", "INR", "USD"))
        assertNull(parseRate("""{"base":"INR,"rates":{"USD":0.010673}}""", "INR", "USD"))
    }

    @Test
    fun `nesting far deeper than a reply is refused without overflowing`() {
        val deep = "[".repeat(10_000) + "]".repeat(10_000)
        assertNull(parseRate(deep, "INR", "USD"))
    }

    @Test
    fun `currency codes are three capital letters`() {
        assertTrue(isCurrencyCode("INR"))
        assertTrue(isCurrencyCode("USD"))
        assertFalse(isCurrencyCode("inr"))
        assertFalse(isCurrencyCode("IN"))
        assertFalse(isCurrencyCode("INRS"))
        assertFalse(isCurrencyCode("IN&"))
        assertFalse(isCurrencyCode(""))
    }
}
