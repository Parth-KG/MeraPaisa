package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * Where a UPI ID may go: into a backup, as an optional key, and nowhere else the app writes. Not the
 * CSV, not a summary, not an update link (whose payload has no place for one at all: it carries
 * the sender's name and the entries only).
 */
class UpiStaysHomeTest {

    private val asha = PersonWithBalance(Person(id = 2, name = "Asha", currency = "INR", upiId = "asha.rao@okaxis"), 500_00)
    private val entries = listOf(
        Transaction(id = 1, personId = 2, amountMinor = 700_00, timestamp = 1_000, note = "Dinner"),
        Transaction(id = 2, personId = 2, amountMinor = -200_00, timestamp = 2_000, paidByUpi = true)
    )

    @Test
    fun `the CSV never carries a UPI ID`() {
        val csv = buildLedgerCsv(listOf(PersonLedger(asha, entries)), Locale.US, TimeZone.getTimeZone("UTC"))
        assertFalse(csv.contains("okaxis"))
    }

    @Test
    fun `a summary never carries a UPI ID`() {
        val summary = buildPersonSummary(asha, entries, locale = Locale.US, timeZone = TimeZone.getTimeZone("UTC"))
        assertFalse(summary.contains("okaxis"))
    }

    @Test
    fun `an update link's payload has no field for one`() {
        val fields = SharePayload::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue("the payload must not grow a UPI field without a decision", fields.none { "upi" in it })
        val entryFields = SharedEntry::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(entryFields.none { "upi" in it })
    }

    @Test
    fun `a backup carries the UPI ID and the UPI mark, and reads them back`() {
        val snapshot = BackupSnapshot(
            persons = listOf(Person(id = 1, name = "You", isSelf = true), asha.person),
            transactions = entries,
            groups = emptyList(), groupMembers = emptyList(), expenses = emptyList(),
            expenseShares = emptyList(), appliedPayloads = emptyList()
        )
        val text = encodeBackup(snapshot, 1, "4.0.0")
        assertTrue(text.contains("\"upiId\": \"asha.rao@okaxis\""))
        val back = (decodeBackup(text) as BackupResult.Ok).snapshot
        assertEquals(snapshot, back)
    }

    @Test
    fun `a backup with no UPI reads exactly as before`() {
        val plain = BackupSnapshot(
            persons = listOf(Person(id = 1, name = "Bilal")),
            transactions = listOf(Transaction(id = 1, personId = 1, amountMinor = 1, timestamp = 1)),
            groups = emptyList(), groupMembers = emptyList(), expenses = emptyList(),
            expenseShares = emptyList(), appliedPayloads = emptyList()
        )
        val text = encodeBackup(plain, 1, "4.0.0")
        assertFalse(text.contains("upiId"))
        assertFalse(text.contains("paidByUpi"))
    }

    @Test
    fun `a UPI ID that is not one is dropped, not the restore`() {
        val snapshot = BackupSnapshot(
            persons = listOf(asha.person), transactions = emptyList(),
            groups = emptyList(), groupMembers = emptyList(), expenses = emptyList(),
            expenseShares = emptyList(), appliedPayloads = emptyList()
        )
        val broken = encodeBackup(snapshot, 1, "4.0.0").replace("asha.rao@okaxis", "not an id")
        val back = (decodeBackup(broken) as BackupResult.Ok).snapshot
        assertEquals(null, back.persons.single().upiId)
        assertEquals("Asha", back.persons.single().name)
    }
}
