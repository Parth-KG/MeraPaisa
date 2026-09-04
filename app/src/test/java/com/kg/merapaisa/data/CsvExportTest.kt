package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class CsvExportTest {

    private fun ledger(
        name: String,
        currency: String = "INR",
        balanceMinor: Long = 0,
        settled: Boolean = false,
        transactions: List<Transaction> = emptyList()
    ) = PersonLedger(
        PersonWithBalance(
            Person(id = 1, name = name, currency = currency, isSettled = settled),
            balanceMinor
        ),
        transactions
    )

    private fun rows(csv: String) = csv.trimEnd('\r', '\n').split("\r\n")

    @Test
    fun startsWithAStableHeader() {
        val csv = buildLedgerCsv(emptyList())
        assertEquals("person,currency,balance,settled,timestamp,date,amount,note", rows(csv).single())
    }

    @Test
    fun writesOneRowPerTransactionWithThePersonRepeated() {
        val csv = buildLedgerCsv(
            timeZone = TimeZone.getTimeZone("UTC"),
            ledgers =
            listOf(
                ledger(
                    "Asha", balanceMinor = 250_50,
                    transactions = listOf(
                        Transaction(personId = 1, amountMinor = 100_00, timestamp = 1_700_000_000_000, note = "cab"),
                        Transaction(personId = 1, amountMinor = 150_50, timestamp = 1_700_000_060_000, note = "dinner")
                    )
                )
            )
        )
        val body = rows(csv).drop(1)
        assertEquals(2, body.size)
        assertEquals("Asha,INR,250.50,no,1700000000000,2023-11-14 22:13:20,100.00,cab", body[0])
        assertEquals("Asha,INR,250.50,no,1700000060000,2023-11-14 22:14:20,150.50,dinner", body[1])
    }

    @Test
    fun aPersonWithNoTransactionsStillAppears() {
        val csv = buildLedgerCsv(listOf(ledger("Bare", balanceMinor = 0)))
        val body = rows(csv).drop(1)
        assertEquals(listOf("Bare,INR,0.00,no,,,,"), body)
    }

    @Test
    fun quotesFieldsThatWouldOtherwiseBreakTheRow() {
        val csv = buildLedgerCsv(
            listOf(
                ledger(
                    "Smith, John",
                    transactions = listOf(
                        Transaction(personId = 1, amountMinor = 10_00, timestamp = 0, note = "said \"thanks\""),
                        Transaction(personId = 1, amountMinor = 20_00, timestamp = 1, note = "two\nlines")
                    )
                )
            )
        )
        assertTrue("comma in name must be quoted", csv.contains("\"Smith, John\""))
        assertTrue("quotes must be doubled", csv.contains("\"said \"\"thanks\"\"\""))
        assertTrue("newline in note must be quoted", csv.contains("\"two\nlines\""))
    }

    @Test
    fun negativeAmountsAndSettledFlagAreRecorded() {
        val csv = buildLedgerCsv(
            listOf(
                ledger(
                    "Owed", balanceMinor = -75_25, settled = true,
                    transactions = listOf(Transaction(personId = 1, amountMinor = -75_25, timestamp = 0))
                )
            )
        )
        val row = rows(csv).drop(1).single()
        assertTrue(row.startsWith("Owed,INR,-75.25,yes,"))
        assertTrue(row.endsWith(",-75.25,"))
    }

    @Test
    fun amountsFollowTheCurrencysOwnDecimals() {
        val csv = buildLedgerCsv(
            listOf(
                ledger(
                    "Yen", currency = "JPY", balanceMinor = 1_234,
                    transactions = listOf(Transaction(personId = 1, amountMinor = 1_234, timestamp = 0))
                )
            )
        )
        assertTrue("JPY has no minor unit in circulation", rows(csv).drop(1).single().contains(",12,"))
    }

    @Test
    fun peopleAreOrderedByNameAndTransactionsOldestFirst() {
        val csv = buildLedgerCsv(
            listOf(
                ledger("zara", transactions = listOf(Transaction(personId = 1, amountMinor = 1, timestamp = 5))),
                ledger(
                    "Amit",
                    transactions = listOf(
                        Transaction(personId = 1, amountMinor = 2, timestamp = 9, note = "late"),
                        Transaction(personId = 1, amountMinor = 3, timestamp = 1, note = "early")
                    )
                )
            )
        )
        val body = rows(csv).drop(1)
        assertTrue(body[0].startsWith("Amit,"))
        assertTrue(body[0].endsWith("early"))
        assertTrue(body[1].endsWith("late"))
        assertTrue(body[2].startsWith("zara,"))
    }

    @Test
    fun rowsAreCrlfTerminatedAsRfc4180Requires() {
        val csv = buildLedgerCsv(
            listOf(ledger("Asha", transactions = listOf(Transaction(personId = 1, amountMinor = 1, timestamp = 0))))
        )
        assertTrue("the file must end with a terminated row", csv.endsWith("\r\n"))
        // Header plus one data row, and no bare newline anywhere.
        assertEquals(2, rows(csv).size)
        assertEquals(2, csv.split("\r\n").dropLast(1).size)
    }
}
