package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/** The full backup: the only file in this app that can actually restore a ledger. */
class BackupFileTest {

    private fun snapshot(
        persons: List<Person> = listOf(
            Person(id = 1, name = "You", isSelf = true, sortOrder = -1),
            Person(id = 2, name = "Asha", currency = "INR", sortOrder = 0),
            Person(id = 3, name = "Ravi", currency = "USD", sortOrder = 1, isSettled = true)
        ),
        transactions: List<Transaction> = listOf(
            Transaction(id = 1, personId = 2, amountMinor = 25_050, timestamp = 1_000, note = "Dinner"),
            Transaction(id = 2, personId = 3, amountMinor = -4_000, timestamp = 2_000, note = "Cab")
        ),
        groups: List<Group> = listOf(Group(id = 1, name = "Goa", currency = "INR", createdAt = 500)),
        groupMembers: List<GroupMember> = listOf(GroupMember(1, 1), GroupMember(1, 2)),
        expenses: List<Expense> = listOf(
            Expense(id = 1, groupId = 1, description = "Hotel", amountMinor = 20_000, paidByPersonId = 1, timestamp = 600)
        ),
        expenseShares: List<ExpenseShare> = listOf(ExpenseShare(1, 1, 10_000), ExpenseShare(1, 2, 10_000)),
        appliedPayloads: List<AppliedPayload> = listOf(
            AppliedPayload("pay1", 3_000, 2, "Parth", 2, -34_000)
        )
    ) = BackupSnapshot(persons, transactions, groups, groupMembers, expenses, expenseShares, appliedPayloads)

    private fun roundTrip(s: BackupSnapshot = snapshot()): BackupResult.Ok =
        decodeBackup(encodeBackup(s, exportedAt = 9_999, appVersion = "2.2.0")) as BackupResult.Ok

    // -----------------------------------------------------------------------------------------
    // Round trip
    // -----------------------------------------------------------------------------------------

    @Test
    fun `round trips every table exactly`() {
        val original = snapshot()
        val back = roundTrip(original)
        assertEquals(original, back.snapshot)
        assertEquals(9_999L, back.exportedAt)
        assertEquals("2.2.0", back.appVersion)
    }

    /** Groups are the whole reason this format exists rather than reusing the CSV. */
    @Test
    fun `carries groups expenses and shares which the csv cannot`() {
        val back = roundTrip().snapshot
        assertEquals(1, back.groups.size)
        assertEquals("Goa", back.groups.single().name)
        assertEquals(2, back.groupMembers.size)
        assertEquals(1, back.expenses.size)
        assertEquals(20_000L, back.expenses.single().amountMinor)
        assertEquals(20_000L, back.expenseShares.sumOf { it.shareMinor })
    }

    @Test
    fun `carries the retired uids`() {
        val s = snapshot().copy(retiredUids = listOf(RetiredUid(2, "gone", RETIRED_CLEARED, 4_000)))
        assertEquals(s.retiredUids, roundTrip(s).snapshot.retiredUids)
    }

    @Test
    fun `a backup from before retired uids still reads`() {
        val text = encodeBackup(snapshot(), 1, "2.5.0").replace(Regex(",\\s*\"retiredUids\": \\[\\s*\\]"), "")
        assertTrue("the test must actually remove the key", "retiredUids" !in text)
        assertTrue((decodeBackup(text) as BackupResult.Ok).snapshot.retiredUids.isEmpty())
    }

    @Test
    fun `refuses retired uids for someone not in the backup`() {
        val s = snapshot().copy(retiredUids = listOf(RetiredUid(99, "x", RETIRED_DELETED, 1)))
        assertEquals(BackupResult.Damaged, decodeBackup(encodeBackup(s, 1, "2.5.0")))
    }

    /** Leave these out and restoring an old backup lets an applied share link land twice. */
    @Test
    fun `carries the applied payload records`() {
        val back = roundTrip().snapshot
        assertEquals(1, back.appliedPayloads.size)
        assertEquals("pay1", back.appliedPayloads.single().payloadId)
        assertEquals(-34_000L, back.appliedPayloads.single().netMinor)
    }

    @Test
    fun `carries the self row which the csv omits`() {
        assertTrue(roundTrip().snapshot.persons.any { it.isSelf })
    }

    @Test
    fun `carries the share watermark`() {
        val s = snapshot(
            persons = listOf(Person(id = 1, name = "Asha", lastSharedAt = 7_777)),
            transactions = emptyList(), groups = emptyList(), groupMembers = emptyList(),
            expenses = emptyList(), expenseShares = emptyList(), appliedPayloads = emptyList()
        )
        assertEquals(7_777L, roundTrip(s).snapshot.persons.single().lastSharedAt)
    }

    @Test
    fun `round trips an empty ledger`() {
        val empty = BackupSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, roundTrip(empty).snapshot)
        assertTrue(empty.isEmpty)
    }

    @Test
    fun `round trips names and notes that need escaping`() {
        val s = snapshot(
            persons = listOf(Person(id = 1, name = "A \"quoted\", comma\nnewline")),
            transactions = listOf(Transaction(id = 1, personId = 1, amountMinor = 1, timestamp = 1, note = "note\twith\\escapes ₹")),
            groups = emptyList(), groupMembers = emptyList(), expenses = emptyList(),
            expenseShares = emptyList(), appliedPayloads = emptyList()
        )
        assertEquals(s, roundTrip(s).snapshot)
    }

    @Test
    fun `round trips extreme amounts`() {
        val s = snapshot(
            persons = listOf(Person(id = 1, name = "Asha")),
            transactions = listOf(
                Transaction(id = 1, personId = 1, amountMinor = Long.MAX_VALUE, timestamp = 1, note = ""),
                Transaction(id = 2, personId = 1, amountMinor = Long.MIN_VALUE, timestamp = 2, note = "")
            ),
            groups = emptyList(), groupMembers = emptyList(), expenses = emptyList(),
            expenseShares = emptyList(), appliedPayloads = emptyList()
        )
        assertEquals(s, roundTrip(s).snapshot)
    }

    @Test
    fun `the file is human readable and names itself`() {
        val text = encodeBackup(snapshot(), 9_999, "2.2.0")
        assertTrue(text.contains("\"format\": \"mera-paisa-backup\""))
        assertTrue(text.contains("\"version\": 1"))
        assertTrue("a backup should be readable by eye", text.contains("\n"))
    }

    // -----------------------------------------------------------------------------------------
    // Refusals
    // -----------------------------------------------------------------------------------------

    @Test
    fun `refuses something that is not a backup`() {
        assertEquals(BackupResult.NotABackup, decodeBackup("{\"format\": \"something-else\"}"))
        assertEquals(BackupResult.NotABackup, decodeBackup("{}"))
        assertEquals(BackupResult.NotABackup, decodeBackup("[]"))
        assertEquals(BackupResult.NotABackup, decodeBackup("not json at all"))
    }

    @Test
    fun `reports a newer format rather than guessing at it`() {
        val text = encodeBackup(snapshot(), 1, "2.2.0").replace("\"version\": 1", "\"version\": 99")
        assertEquals(BackupResult.TooNew(99), decodeBackup(text))
    }

    @Test
    fun `refuses a truncated backup`() {
        val text = encodeBackup(snapshot(), 1, "2.2.0")
        for (fraction in listOf(2, 3, 4)) {
            val clipped = text.take(text.length / fraction)
            assertTrue(
                "a backup clipped to 1/$fraction must not decode",
                decodeBackup(clipped) !is BackupResult.Ok
            )
        }
    }

    @Test
    fun `refuses a missing table`() {
        val text = encodeBackup(snapshot(), 1, "2.2.0").replace("\"transactions\"", "\"transactionz\"")
        assertEquals(BackupResult.Damaged, decodeBackup(text))
    }

    @Test
    fun `refuses a field of the wrong type`() {
        val text = encodeBackup(snapshot(), 1, "2.2.0")
            .replace("\"amountMinor\": 25050", "\"amountMinor\": \"25050\"")
        assertEquals(BackupResult.Damaged, decodeBackup(text))
    }

    /**
     * Room's foreign keys would reject these anyway, but halfway through writing, which is how a
     * half-restored ledger happens. Catching it at parse time means the restore never starts.
     */
    @Test
    fun `refuses a transaction pointing at a person that is not in the file`() {
        val s = snapshot(
            persons = listOf(Person(id = 1, name = "Asha")),
            transactions = listOf(Transaction(id = 1, personId = 999, amountMinor = 1, timestamp = 1, note = "")),
            groups = emptyList(), groupMembers = emptyList(), expenses = emptyList(),
            expenseShares = emptyList(), appliedPayloads = emptyList()
        )
        assertEquals(BackupResult.Damaged, decodeBackup(encodeBackup(s, 1, "2.2.0")))
    }

    @Test
    fun `refuses an expense or share pointing at something absent`() {
        val orphanExpense = snapshot(
            persons = listOf(Person(id = 1, name = "Asha")), transactions = emptyList(),
            groups = emptyList(), groupMembers = emptyList(),
            expenses = listOf(Expense(id = 1, groupId = 42, description = "x", amountMinor = 1, paidByPersonId = 1, timestamp = 1)),
            expenseShares = emptyList(), appliedPayloads = emptyList()
        )
        assertEquals(BackupResult.Damaged, decodeBackup(encodeBackup(orphanExpense, 1, "2.2.0")))

        val orphanShare = snapshot(
            persons = listOf(Person(id = 1, name = "Asha")), transactions = emptyList(),
            groups = emptyList(), groupMembers = emptyList(), expenses = emptyList(),
            expenseShares = listOf(ExpenseShare(expenseId = 42, personId = 1, shareMinor = 1)),
            appliedPayloads = emptyList()
        )
        assertEquals(BackupResult.Damaged, decodeBackup(encodeBackup(orphanShare, 1, "2.2.0")))
    }

    @Test
    fun `refuses a group member pointing at an absent group`() {
        val s = snapshot(
            persons = listOf(Person(id = 1, name = "Asha")), transactions = emptyList(),
            groups = emptyList(), groupMembers = listOf(GroupMember(groupId = 42, personId = 1)),
            expenses = emptyList(), expenseShares = emptyList(), appliedPayloads = emptyList()
        )
        assertEquals(BackupResult.Damaged, decodeBackup(encodeBackup(s, 1, "2.2.0")))
    }

    /** A backup written before share links existed has no such list, and must still restore. */
    @Test
    fun `tolerates an absent appliedPayloads list for backward compatibility`() {
        val text = encodeBackup(snapshot(appliedPayloads = emptyList()), 1, "2.2.0")
        val without = text.replace(Regex(",\\s*\"appliedPayloads\": \\[\\]"), "")
        val result = decodeBackup(without)
        assertTrue("an older backup should still restore, got $result", result is BackupResult.Ok)
        assertTrue((result as BackupResult.Ok).snapshot.appliedPayloads.isEmpty())
    }

    @Test
    fun `the file name sorts chronologically`() {
        assertEquals("mera-paisa-backup-20260928-143000.json", backupFileName("20260928-143000"))
        assertTrue(backupFileName("20260101-000000") < backupFileName("20260928-143000"))
    }
}

/**
 * The CSV importer, read against the exporter it mirrors.
 *
 * The round-trip test is the one that matters: these two functions have to agree, and they live in
 * different files with no compiler relationship between them.
 */
class CsvImportTest {

    private fun ledger(
        name: String = "Asha",
        currency: String = "INR",
        balanceMinor: Long = 25_050,
        isSettled: Boolean = false,
        transactions: List<Transaction> = listOf(
            Transaction(id = 1, personId = 1, amountMinor = 20_000, timestamp = 1_000, note = "Dinner"),
            Transaction(id = 2, personId = 1, amountMinor = 5_050, timestamp = 2_000, note = "Chai")
        )
    ) = PersonLedger(
        person = PersonWithBalance(
            person = Person(id = 1, name = name, currency = currency, isSettled = isSettled),
            balanceMinor = balanceMinor
        ),
        transactions = transactions
    )

    private fun csvOf(vararg ledgers: PersonLedger) =
        buildLedgerCsv(ledgers.toList(), Locale.US, TimeZone.getTimeZone("UTC"))

    // -----------------------------------------------------------------------------------------
    // Round trip against the real exporter
    // -----------------------------------------------------------------------------------------

    @Test
    fun `round trips what the exporter writes`() {
        val result = readLedgerCsv(csvOf(ledger())) as CsvImportResult.Ok
        val person = result.people.single()
        assertEquals("Asha", person.name)
        assertEquals("INR", person.currency)
        assertEquals(2, person.transactions.size)
        assertEquals(listOf(20_000L, 5_050L), person.transactions.map { it.amountMinor })
        assertEquals(listOf("Dinner", "Chai"), person.transactions.map { it.note })
        assertEquals(listOf(1_000L, 2_000L), person.transactions.map { it.timestamp })
    }

    @Test
    fun `round trips several people`() {
        val csv = csvOf(
            ledger(name = "Asha"),
            ledger(name = "Ravi", currency = "USD", transactions = listOf(
                Transaction(id = 3, personId = 2, amountMinor = -4_000, timestamp = 3_000, note = "Cab")
            ))
        )
        val people = (readLedgerCsv(csv) as CsvImportResult.Ok).people
        assertEquals(2, people.size)
        assertEquals(setOf("Asha", "Ravi"), people.map { it.name }.toSet())
        assertEquals(-4_000L, people.first { it.name == "Ravi" }.transactions.single().amountMinor)
    }

    @Test
    fun `round trips negative amounts`() {
        val csv = csvOf(ledger(transactions = listOf(
            Transaction(id = 1, personId = 1, amountMinor = -12_345, timestamp = 1, note = "owed")
        )))
        val p = (readLedgerCsv(csv) as CsvImportResult.Ok).people.single()
        assertEquals(-12_345L, p.transactions.single().amountMinor)
    }

    /** Notes are free text and the exporter quotes them; the reader has to unquote identically. */
    @Test
    fun `round trips notes containing commas quotes and newlines`() {
        val nasty = "dinner, drinks and \"extras\"\nsecond line"
        val csv = csvOf(ledger(transactions = listOf(
            Transaction(id = 1, personId = 1, amountMinor = 100, timestamp = 1, note = nasty)
        )))
        val p = (readLedgerCsv(csv) as CsvImportResult.Ok).people.single()
        assertEquals(nasty, p.transactions.single().note)
    }

    @Test
    fun `round trips a name containing a comma`() {
        val csv = csvOf(ledger(name = "Singh, Ravi"))
        assertEquals("Singh, Ravi", (readLedgerCsv(csv) as CsvImportResult.Ok).people.single().name)
    }

    /** The exporter gives these a row with empty transaction columns; they are people, not errors. */
    @Test
    fun `reads a person who has no transactions`() {
        val csv = csvOf(ledger(balanceMinor = 0, transactions = emptyList()))
        val p = (readLedgerCsv(csv) as CsvImportResult.Ok).people.single()
        assertEquals("Asha", p.name)
        assertTrue(p.transactions.isEmpty())
    }

    @Test
    fun `carries the settled flag`() {
        val csv = csvOf(ledger(isSettled = true, balanceMinor = 0))
        assertTrue((readLedgerCsv(csv) as CsvImportResult.Ok).people.single().isSettled)
    }

    /**
     * The balance column includes group activity while the amount rows do not, so for anyone in a
     * group the two disagree by design. The importer must derive from the rows, or it would import
     * a balance with no entries to explain it.
     */
    @Test
    fun `ignores the balance column and derives from the rows`() {
        // A balance deliberately inconsistent with the entries, as a group member's would be.
        val csv = csvOf(ledger(balanceMinor = 999_999, transactions = listOf(
            Transaction(id = 1, personId = 1, amountMinor = 20_000, timestamp = 1, note = "Dinner")
        )))
        val p = (readLedgerCsv(csv) as CsvImportResult.Ok).people.single()
        assertEquals("only the entries count", 20_000L, p.transactions.sumOf { it.amountMinor })
        assertNotEquals(999_999L, p.transactions.sumOf { it.amountMinor })
    }

    @Test
    fun `the same name in two currencies is two people`() {
        val csv = csvOf(ledger(name = "Asha", currency = "INR"), ledger(name = "Asha", currency = "USD"))
        assertEquals(2, (readLedgerCsv(csv) as CsvImportResult.Ok).people.size)
    }

    // -----------------------------------------------------------------------------------------
    // Refusals and tolerances
    // -----------------------------------------------------------------------------------------

    @Test
    fun `refuses a csv that is not ours`() {
        assertEquals(CsvImportResult.NotALedgerCsv, readLedgerCsv("a,b,c\r\n1,2,3\r\n"))
        assertEquals(CsvImportResult.NotALedgerCsv, readLedgerCsv(""))
    }

    @Test
    fun `refuses an unterminated quoted field`() {
        val broken = "person,currency,balance,settled,timestamp,date,amount,note\r\n\"Asha,INR,1,no,1,x,1,n\r\n"
        assertTrue(readLedgerCsv(broken) is CsvImportResult.Damaged)
    }

    @Test
    fun `reports the line of a bad amount`() {
        val csv = csvOf(ledger()).replace("200.00", "not-a-number")
        val result = readLedgerCsv(csv)
        assertTrue("expected Damaged, got $result", result is CsvImportResult.Damaged)
        assertEquals("the first data row is line 2", 2, (result as CsvImportResult.Damaged).line)
        assertTrue(result.reason.contains("not-a-number"))
    }

    @Test
    fun `reports the line of a bad timestamp`() {
        val csv = csvOf(ledger()).replace(",1000,", ",abc,")
        val result = readLedgerCsv(csv)
        assertTrue("expected Damaged, got $result", result is CsvImportResult.Damaged)
        assertEquals(2, (result as CsvImportResult.Damaged).line)
    }

    @Test
    fun `refuses a row with the wrong number of columns`() {
        val csv = csvOf(ledger()) + "too,few,columns\r\n"
        val result = readLedgerCsv(csv)
        assertTrue("expected Damaged, got $result", result is CsvImportResult.Damaged)
        assertTrue((result as CsvImportResult.Damaged).reason.contains("columns"))
    }

    @Test
    fun `refuses an empty person name`() {
        val csv = "person,currency,balance,settled,timestamp,date,amount,note\r\n,INR,0.00,no,1,x,1.00,n\r\n"
        val result = readLedgerCsv(csv)
        assertTrue("expected Damaged, got $result", result is CsvImportResult.Damaged)
    }

    /** A spreadsheet round trip leaves a BOM and may switch the line endings. */
    @Test
    fun `tolerates a byte order mark and lone newlines`() {
        val csv = "﻿" + csvOf(ledger()).replace("\r\n", "\n")
        val result = readLedgerCsv(csv)
        assertTrue("a spreadsheet-saved file should still read, got $result", result is CsvImportResult.Ok)
        assertEquals("Asha", (result as CsvImportResult.Ok).people.single().name)
    }

    /** A spreadsheet may well turn "yes" into TRUE. */
    @Test
    fun `tolerates alternative spellings of the settled flag`() {
        val base = "person,currency,balance,settled,timestamp,date,amount,note\r\n"
        for (flag in listOf("yes", "TRUE", "True", "1")) {
            val csv = base + "Asha,INR,0.00,$flag,1,x,1.00,n\r\n"
            assertTrue("'$flag' should read as settled", (readLedgerCsv(csv) as CsvImportResult.Ok).people.single().isSettled)
        }
        for (flag in listOf("no", "FALSE", "0", "")) {
            val csv = base + "Asha,INR,0.00,$flag,1,x,1.00,n\r\n"
            assertTrue("'$flag' should read as not settled", !(readLedgerCsv(csv) as CsvImportResult.Ok).people.single().isSettled)
        }
    }

    /** Older exports stored a symbol where a code goes. */
    @Test
    fun `normalises a legacy currency symbol`() {
        val csv = "person,currency,balance,settled,timestamp,date,amount,note\r\nAsha,₹,0.00,no,1,x,1.00,n\r\n"
        assertEquals("INR", (readLedgerCsv(csv) as CsvImportResult.Ok).people.single().currency)
    }

    // ---------------------------------------------------------------------------------------
    // Fields added after the format shipped
    //
    // Two of these were missed in a row (simplifyDebts and isSettlement, both from v2.4), and
    // neither failed anything: a restore simply put every group back on the default plan with its
    // repayments filed as purchases. The round trip has to be checked field by field, because
    // "it restored" and "it restored correctly" are not the same test.
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a settlement is still a settlement after a round trip`() {
        val snapshot = BackupSnapshot(
            persons = listOf(Person(id = 1, name = "Me", currency = "INR", isSelf = true)),
            transactions = emptyList(),
            groups = listOf(Group(id = 1, name = "Goa", currency = "INR", createdAt = 1, simplifyDebts = false)),
            groupMembers = emptyList(),
            expenses = listOf(
                Expense(id = 1, groupId = 1, description = "Hotel", amountMinor = 20_000, paidByPersonId = 1, timestamp = 1),
                Expense(id = 2, groupId = 1, description = "Settlement", amountMinor = 5_000, paidByPersonId = 1, timestamp = 2, isSettlement = true)
            ),
            expenseShares = emptyList(),
            appliedPayloads = emptyList()
        )

        val back = (decodeBackup(encodeBackup(snapshot, 1_700_000_000_000L, "2.5.0")) as BackupResult.Ok).snapshot

        assertEquals("a purchase stays a purchase", false, back.expenses.first { it.id == 1L }.isSettlement)
        assertEquals("a repayment stays a repayment", true, back.expenses.first { it.id == 2L }.isSettlement)
        assertEquals("and the group keeps its plan", false, back.groups.single().simplifyDebts)
    }

    /** An older file has neither field, and must land where an older database upgrades to. */
    @Test
    fun `a backup written before those fields existed still restores sensibly`() {
        val old = """{"format":"mera-paisa-backup","version":1,"exportedAt":1,"appVersion":"2.4.0",
            "persons":[{"id":1,"name":"Me","balance":0,"pfpType":"initials","pfpValue":"ME","pfpColor":"#000000","sortOrder":0,"isSettled":false,"currency":"INR","isSelf":true,"lastSharedAt":0}],
            "transactions":[],
            "groups":[{"id":1,"name":"Goa","currency":"INR","createdAt":1,"archived":false}],
            "groupMembers":[],
            "expenses":[
              {"id":1,"groupId":1,"description":"Hotel","amountMinor":20000,"paidByPersonId":1,"timestamp":1},
              {"id":2,"groupId":1,"description":"Settlement","amountMinor":5000,"paidByPersonId":1,"timestamp":2}],
            "expenseShares":[],
            "appliedPayloads":[]}"""

        val back = (decodeBackup(old) as BackupResult.Ok).snapshot

        assertEquals("recognised from its description, as the migration does", true, back.expenses.first { it.id == 2L }.isSettlement)
        assertEquals(false, back.expenses.first { it.id == 1L }.isSettlement)
        assertEquals("groups predating the toggle netted down, so that is what they restore to", true, back.groups.single().simplifyDebts)
    }
}
