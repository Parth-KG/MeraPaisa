package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetTotalTest {

    private fun person(id: Long, currency: String, balanceMinor: Long) =
        PersonWithBalance(Person(id = id, name = "P$id", currency = currency), balanceMinor)

    @Test
    fun sumsWithinEachCurrencySeparately() {
        val totals = netTotalsByCurrency(
            listOf(
                person(1, "INR", 250_00),
                person(2, "INR", 90_00),
                person(3, "USD", -15_00)
            )
        )
        assertEquals(
            listOf(CurrencyTotal("INR", 340_00), CurrencyTotal("USD", -15_00)),
            totals
        )
    }

    @Test
    fun nettingOffCancelsWithinACurrencyButNotAcrossThem() {
        val totals = netTotalsByCurrency(
            listOf(
                person(1, "INR", 500_00),
                person(2, "INR", -500_00),
                person(3, "USD", 20_00)
            )
        )
        // INR nets out and disappears; USD is untouched by it.
        assertEquals(listOf(CurrencyTotal("USD", 20_00)), totals)
    }

    @Test
    fun peopleAtZeroContributeNothing() {
        val totals = netTotalsByCurrency(
            listOf(person(1, "INR", 0), person(2, "INR", 75_50), person(3, "EUR", 0))
        )
        assertEquals(listOf(CurrencyTotal("INR", 75_50)), totals)
    }

    @Test
    fun everythingSettledReportsNothing() {
        assertTrue(netTotalsByCurrency(emptyList()).isEmpty())
        assertTrue(netTotalsByCurrency(listOf(person(1, "INR", 0))).isEmpty())
    }

    @Test
    fun orderIsStableAcrossCurrencies() {
        val totals = netTotalsByCurrency(
            listOf(person(1, "USD", 1), person(2, "EUR", 1), person(3, "INR", 1))
        )
        assertEquals(listOf("EUR", "INR", "USD"), totals.map { it.currency })
    }

    @Test
    fun negativeOverallIsReportedAsNegative() {
        val totals = netTotalsByCurrency(
            listOf(person(1, "GBP", -40_00), person(2, "GBP", 12_25))
        )
        assertEquals(listOf(CurrencyTotal("GBP", -27_75)), totals)
        assertEquals("-£27.75", formatMinor(totals.single().amountMinor, "GBP"))
    }
}
