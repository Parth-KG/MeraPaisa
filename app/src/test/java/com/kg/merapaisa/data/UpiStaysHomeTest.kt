package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where your own UPI ID may go: into your QR and a reminder you send, and nowhere else the app
 * writes. It lives in DataStore alone, so nothing built from the ledger (the CSV, a summary, the
 * widget, an update link, the JSON backup) has a place to carry it. These tests keep it that way:
 * a UPI field on a person, an entry, an update link or a backup needs a decision first.
 */
class UpiStaysHomeTest {

    private fun fieldsOf(type: Class<*>) = type.declaredFields.map { it.name.lowercase() }

    @Test
    fun `a person has no place for a UPI ID`() {
        assertTrue(fieldsOf(Person::class.java).none { "upi" in it })
        assertTrue(fieldsOf(PersonWithBalance::class.java).none { "upi" in it })
    }

    @Test
    fun `an update link's payload has no field for one, or for the mark`() {
        assertTrue(fieldsOf(SharePayload::class.java).none { "upi" in it })
        assertTrue(fieldsOf(SharedEntry::class.java).none { "upi" in it })
    }

    @Test
    fun `a backup has no field for one, only the mark on an entry`() {
        assertTrue(fieldsOf(BackupSnapshot::class.java).none { "upi" in it })
        assertEquals(listOf("paidbyupi"), fieldsOf(Transaction::class.java).filter { "upi" in it })
    }

    @Test
    fun `a backup carries the UPI mark and reads it back`() {
        val snapshot = BackupSnapshot(
            persons = listOf(Person(id = 1, name = "You", isSelf = true), Person(id = 2, name = "Asha", currency = "INR")),
            transactions = listOf(
                Transaction(id = 1, personId = 2, amountMinor = 700_00, timestamp = 1_000, note = "Dinner"),
                Transaction(id = 2, personId = 2, amountMinor = -200_00, timestamp = 2_000, paidByUpi = true)
            ),
            groups = emptyList(), groupMembers = emptyList(), expenses = emptyList(),
            expenseShares = emptyList(), appliedPayloads = emptyList()
        )
        val text = encodeBackup(snapshot, 1, "3.6.0")
        assertEquals("only the marked entry says so", 1, Regex("\"paidByUpi\"").findAll(text).count())
        assertEquals(snapshot, (decodeBackup(text) as BackupResult.Ok).snapshot)
    }

    @Test
    fun `a backup with no UPI payments reads exactly as before`() {
        val plain = BackupSnapshot(
            persons = listOf(Person(id = 1, name = "Bilal")),
            transactions = listOf(Transaction(id = 1, personId = 1, amountMinor = 1, timestamp = 1)),
            groups = emptyList(), groupMembers = emptyList(), expenses = emptyList(),
            expenseShares = emptyList(), appliedPayloads = emptyList()
        )
        assertFalse(encodeBackup(plain, 1, "3.6.0").contains("upi", ignoreCase = true))
    }
}
