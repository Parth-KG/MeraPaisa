package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class LedgerSummaryTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun person(name: String = "Asha", currency: String = "INR", balanceMinor: Long) =
        PersonWithBalance(Person(id = 1, name = name, currency = currency), balanceMinor)

    private fun entry(amountMinor: Long, timestamp: Long, note: String = "") =
        Transaction(personId = 1, amountMinor = amountMinor, timestamp = timestamp, note = note)

    private fun summary(p: PersonWithBalance, t: List<Transaction>, limit: Int = SUMMARY_ENTRY_LIMIT) =
        buildPersonSummary(p, t, limit, Locale.US, utc)

    @Test
    fun headlineIsWrittenToThePersonBeingSent() {
        assertEquals("Asha — you owe me ₹250.50", summary(person(balanceMinor = 250_50), emptyList()))
        assertEquals("Asha — I owe you ₹40.00", summary(person(balanceMinor = -40_00), emptyList()))
        assertEquals("Asha — we're all settled up", summary(person(balanceMinor = 0), emptyList()))
    }

    @Test
    fun listsEntriesOldestFirstWithTheBalanceAfterEach() {
        val text = summary(
            person(balanceMinor = 150_00),
            listOf(entry(100_00, 1_700_000_000_000, "cab"), entry(50_00, 1_700_000_060_000, "dinner"))
        )
        val lines = text.lines()
        assertEquals("Asha — you owe me ₹150.00", lines[0])
        assertEquals("Recent activity:", lines[2])
        assertEquals("14 Nov, 10:13 PM: +₹100.00 (cab)  →  ₹100.00", lines[3])
        assertEquals("14 Nov, 10:14 PM: +₹50.00 (dinner)  →  ₹150.00", lines[4])
    }

    @Test
    fun anEntryWithoutANoteOmitsTheParentheses() {
        val text = summary(person(balanceMinor = 100_00), listOf(entry(100_00, 0)))
        assertTrue(text.contains("+₹100.00  →  ₹100.00"))
        assertFalse(text.contains("()"))
    }

    @Test
    fun truncatedLogsStillReportTrueRunningBalances() {
        val entries = (1..5).map { entry(10_00, it.toLong(), "entry $it") }
        val text = summary(person(balanceMinor = 50_00), entries, limit = 2)

        // Only the last two lines appear, but they carry balances of 40 and 50, not 10 and 20.
        assertFalse("the first entry should be hidden", text.contains("entry 1"))
        assertTrue(text.contains("(entry 4)  →  ₹40.00"))
        assertTrue(text.contains("(entry 5)  →  ₹50.00"))
        assertTrue(text.endsWith("(3 earlier entries not shown)"))
    }

    @Test
    fun oneHiddenEntryIsSingular() {
        val entries = listOf(entry(10_00, 1), entry(10_00, 2))
        assertTrue(summary(person(balanceMinor = 20_00), entries, limit = 1)
            .endsWith("(1 earlier entry not shown)"))
    }

    @Test
    fun nothingIsMarkedHiddenWhenEverythingFits() {
        val text = summary(person(balanceMinor = 20_00), listOf(entry(10_00, 1), entry(10_00, 2)))
        assertFalse(text.contains("not shown"))
    }

    @Test
    fun negativeEntriesKeepTheirSign() {
        val text = summary(person(balanceMinor = 50_00), listOf(entry(100_00, 1), entry(-50_00, 2, "refund")))
        assertTrue(text.contains("-₹50.00 (refund)  →  ₹50.00"))
    }

    @Test
    fun aPersonWithNoHistoryIsJustTheHeadline() {
        assertEquals("Asha — you owe me ₹10.00", summary(person(balanceMinor = 10_00), emptyList()))
    }

    @Test
    fun amountsFollowTheCurrencysOwnDecimals() {
        val text = summary(person(currency = "JPY", balanceMinor = 1_234), listOf(entry(1_234, 1)))
        assertTrue(text.startsWith("Asha — you owe me ¥12"))
        assertTrue(text.contains("+¥12  →  ¥12"))
    }

    @Test
    fun theFullLogIsAvailableWithoutTruncation() {
        val entries = (1..30).map { entry(1_00, it.toLong()) }
        val log = buildActivityLog(entries, "INR", locale = Locale.US, timeZone = utc)
        assertEquals(30, log.lines().size)
        assertFalse(log.contains("not shown"))
    }
}
