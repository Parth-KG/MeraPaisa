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

    /**
     * A legacy row stores the symbol where a newer one stores the code, and both mean rupees.
     *
     * Grouped by the raw string they became two separate totals, shown one above the other on the
     * main screen and the widget, both rendered with a ₹, as though the user tracked two different
     * rupees. `normaliseCurrency` exists for exactly this and every other comparison in the app
     * already goes through it.
     */
    @Test
    fun `a legacy symbol and its code are one currency`() {
        val totals = netTotalsByCurrency(
            listOf(
                person(1, "\u20B9", 30_000),
                person(2, "INR", 12_000)
            )
        )

        assertEquals("one rupee total, not two", 1, totals.size)
        assertEquals("INR", totals.single().currency)
        assertEquals(42_000L, totals.single().amountMinor)
    }

    /** And they must be able to cancel each other out rather than showing as +30 and -30. */
    @Test
    fun `a legacy symbol nets against its code`() {
        val totals = netTotalsByCurrency(
            listOf(
                person(1, "\u20B9", 30_000),
                person(2, "INR", -30_000)
            )
        )

        assertEquals("they cancel, so nothing is reported", 0, totals.size)
    }

    // -----------------------------------------------------------------------------------------
    // Both sides: what you are owed and what you owe, kept apart
    // -----------------------------------------------------------------------------------------

    @Test
    fun bothSidesKeepsOwedAndOwingApartWithinACurrency() {
        val sides = sidesByCurrency(
            listOf(person(1, "INR", 1_000_00), person(2, "INR", 250_00), person(3, "INR", -900_00))
        )
        assertEquals(listOf(CurrencySides("INR", 1_250_00, -900_00)), sides)
    }

    @Test
    fun bothSidesNeverAddsOneCurrencyToAnother() {
        val sides = sidesByCurrency(
            listOf(person(1, "USD", 20_00), person(2, "INR", -40_00), person(3, "JPY", 3_000_00))
        )
        assertEquals(
            listOf(
                CurrencySides("INR", 0, -40_00),
                CurrencySides("JPY", 3_000_00, 0),
                CurrencySides("USD", 20_00, 0)
            ),
            sides
        )
    }

    /** Where the net cancels to nothing, both sides still has something to say. */
    @Test
    fun bothSidesShowsWhatTheNetCancelsAway() {
        val people = listOf(person(1, "INR", 500_00), person(2, "INR", -500_00))
        assertTrue(netTotalsByCurrency(people).isEmpty())
        assertEquals(listOf(CurrencySides("INR", 500_00, -500_00)), sidesByCurrency(people))
    }

    @Test
    fun `a legacy symbol and its code are one currency on both sides`() {
        val sides = sidesByCurrency(listOf(person(1, "\u20B9", 300_00), person(2, "INR", -100_00)))
        assertEquals(listOf(CurrencySides("INR", 300_00, -100_00)), sides)
    }

    @Test
    fun aCurrencyWhereEveryoneIsEvenIsLeftOut() {
        val sides = sidesByCurrency(listOf(person(1, "EUR", 0), person(2, "INR", 0), person(3, "USD", -5_00)))
        assertEquals(listOf(CurrencySides("USD", 0, -5_00)), sides)
        assertTrue(sidesByCurrency(listOf(person(1, "INR", 0))).isEmpty())
    }
}
