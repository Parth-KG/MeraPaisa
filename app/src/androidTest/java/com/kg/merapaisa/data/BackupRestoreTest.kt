package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.repository.BackupRepository
import com.kg.merapaisa.repository.GroupRepository
import com.kg.merapaisa.repository.PersonRepository
import com.kg.merapaisa.repository.toSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Backup and restore against a real database.
 *
 * The planner is covered by JVM tests; what needs a device is everything those cannot reach: that
 * a snapshot really does read every table, that a restore writes back through Room's foreign keys
 * without tripping them, and that the balances a user actually sees come out the same on the other
 * side. A backup nobody has restored is a file, not a backup.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private lateinit var db: AppDatabase
    private lateinit var personDao: PersonDao
    private lateinit var groupDao: GroupDao
    private lateinit var backups: BackupRepository
    private lateinit var people: PersonRepository
    private lateinit var groups: GroupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        personDao = db.personDao()
        groupDao = db.groupDao()
        backups = BackupRepository(db, personDao, groupDao)
        people = PersonRepository(personDao)
        groups = GroupRepository(groupDao, personDao)
    }

    @After
    fun tearDown() = db.close()

    /** A ledger with something in every table, so a snapshot has something to miss. */
    private suspend fun seed(): Pair<Long, Long> {
        val selfId = personDao.ensureSelf().id
        val asha = personDao.insertPerson(Person(name = "Asha", currency = "INR", sortOrder = 0))
        val ravi = personDao.insertPerson(Person(name = "Ravi", currency = "USD", sortOrder = 1))

        personDao.recordEntry(Transaction(personId = asha, amountMinor = 20_000, timestamp = 1_000, note = "Dinner"))
        personDao.recordEntry(Transaction(personId = asha, amountMinor = 5_050, timestamp = 2_000, note = "Chai"))
        personDao.recordEntry(Transaction(personId = ravi, amountMinor = -4_000, timestamp = 3_000, note = "Cab"))

        val groupId = groups.createGroup("Goa", "INR", listOf(selfId, asha))
        groups.addExpense(groupId, "Hotel", 20_000, selfId, listOf(selfId, asha))

        personDao.insertAppliedPayloads(
            listOf(AppliedPayload("pay1", 500, asha, "Parth", 1, -5_000))
        )
        return asha to ravi
    }

    // -----------------------------------------------------------------------------------------
    // Round trip through a real database
    // -----------------------------------------------------------------------------------------

    @Test
    fun aSnapshotReadsEveryTable() = runBlocking {
        seed()
        val snapshot = backups.snapshot()

        assertEquals("self plus two people", 3, snapshot.persons.size)
        assertEquals(3, snapshot.transactions.size)
        assertEquals(1, snapshot.groups.size)
        assertEquals(2, snapshot.groupMembers.size)
        assertEquals(1, snapshot.expenses.size)
        assertEquals(2, snapshot.expenseShares.size)
        assertEquals(1, snapshot.appliedPayloads.size)
        assertTrue("the self row belongs in a backup", snapshot.persons.any { it.isSelf })
    }

    /**
     * The whole feature in one test: back up, lose everything, restore, and end up where you
     * started. This is the case the v1.0 to v1.2 data loss would have needed.
     */
    @Test
    fun aFullBackupSurvivesLosingEverything() = runBlocking {
        val (asha, ravi) = seed()
        val ashaBefore = personDao.getBalanceNow(asha)
        val raviBefore = personDao.getBalanceNow(ravi)
        val json = backups.exportJson(9_999, "2.2.0")

        // The phone is replaced: a brand-new database with nothing but a self row.
        personDao.deleteAllPersons()
        groupDao.deleteAllGroups()
        personDao.deleteAllAppliedPayloads()
        personDao.ensureSelf()
        assertEquals(0, backups.snapshot().transactions.size)

        val decoded = decodeBackup(json) as BackupResult.Ok
        backups.apply(backups.plan(decoded.snapshot, RestoreMode.Replace))

        val after = backups.snapshot()
        assertEquals(3, after.persons.size)
        assertEquals(3, after.transactions.size)
        assertEquals(1, after.groups.size)
        assertEquals(1, after.expenses.size)
        assertEquals(2, after.expenseShares.size)
        assertEquals("an applied share link must stay applied", 1, after.appliedPayloads.size)

        val ashaAfter = after.persons.first { it.name == "Asha" }
        val raviAfter = after.persons.first { it.name == "Ravi" }
        assertEquals(ashaBefore, personDao.getBalanceNow(ashaAfter.id))
        assertEquals(raviBefore, personDao.getBalanceNow(raviAfter.id))
    }

    @Test
    fun replaceLeavesExactlyOneSelfRow() = runBlocking {
        seed()
        val json = backups.exportJson(1, "2.2.0")
        val decoded = decodeBackup(json) as BackupResult.Ok

        backups.apply(backups.plan(decoded.snapshot, RestoreMode.Replace))

        assertEquals(
            "two rows claiming to be you would make ensureSelf pick arbitrarily",
            1,
            backups.snapshot().persons.count { it.isSelf }
        )
    }

    /** A backup with no self row of its own must still leave the app with one. */
    @Test
    fun replacingFromABackupWithoutASelfRowStillLeavesOne() = runBlocking {
        seed()
        val snapshot = backups.snapshot()
        val withoutSelf = snapshot.copy(
            persons = snapshot.persons.filterNot { it.isSelf },
            groupMembers = snapshot.groupMembers.filterNot { m -> snapshot.persons.first { it.id == m.personId }.isSelf },
            expenses = emptyList(),
            expenseShares = emptyList()
        )

        backups.apply(backups.plan(withoutSelf, RestoreMode.Replace))

        assertEquals(1, backups.snapshot().persons.count { it.isSelf })
    }

    // -----------------------------------------------------------------------------------------
    // Merge
    // -----------------------------------------------------------------------------------------

    /** The property the planner promises, proved against Room's own constraints. */
    @Test
    fun mergingTheSameBackupTwiceChangesNothingTheSecondTime() = runBlocking {
        val (asha, _) = seed()
        val json = backups.exportJson(1, "2.2.0")
        val decoded = decodeBackup(json) as BackupResult.Ok
        val balanceBefore = personDao.getBalanceNow(asha)

        backups.apply(backups.plan(decoded.snapshot, RestoreMode.Merge))
        val afterFirst = backups.snapshot()

        backups.apply(backups.plan(decoded.snapshot, RestoreMode.Merge))
        val afterSecond = backups.snapshot()

        assertEquals(afterFirst.persons.size, afterSecond.persons.size)
        assertEquals(afterFirst.transactions.size, afterSecond.transactions.size)
        assertEquals(afterFirst.expenses.size, afterSecond.expenses.size)
        assertEquals(afterFirst.expenseShares.size, afterSecond.expenseShares.size)
        assertEquals("balances must not drift", balanceBefore, personDao.getBalanceNow(asha))
    }

    @Test
    fun mergingIntoAFreshLedgerBringsEverythingAcross() = runBlocking {
        seed()
        val json = backups.exportJson(1, "2.2.0")
        val decoded = decodeBackup(json) as BackupResult.Ok

        personDao.deleteAllPersons()
        groupDao.deleteAllGroups()
        personDao.deleteAllAppliedPayloads()
        personDao.ensureSelf()

        backups.apply(backups.plan(decoded.snapshot, RestoreMode.Merge))

        val after = backups.snapshot()
        assertEquals(3, after.persons.size)
        assertEquals(3, after.transactions.size)
        assertEquals(1, after.groups.size)
        assertEquals("group membership must point at this phone's self row", 2, after.groupMembers.size)
    }

    @Test
    fun mergingAnOlderBackupAfterClearHistoryAddsNothingOnTopOfTheOpeningBalance() = runBlocking {
        val (asha, _) = seed()
        val older = decodeBackup(backups.exportJson(1, "3.7.0")) as BackupResult.Ok
        val balance = personDao.getBalanceNow(asha)
        people.clearTransactions(asha)
        assertEquals(balance, personDao.getBalanceNow(asha))

        backups.apply(backups.plan(older.snapshot, RestoreMode.Merge))

        assertEquals("the old entries are in the opening balance already", balance, personDao.getBalanceNow(asha))
    }

    @Test
    fun aClearHistoryAfterMergingAClearedBackupThenTheSameMergeAgainCountsNothingTwice() = runBlocking {
        val (asha, _) = seed()
        val balance = personDao.getBalanceNow(asha)
        val older = decodeBackup(backups.exportJson(1, "3.7.1")) as BackupResult.Ok
        people.clearTransactions(asha)
        val cleared = decodeBackup(backups.exportJson(2, "3.7.1")) as BackupResult.Ok
        // Back to the old entries, as a phone restored from the older backup would be.
        backups.apply(backups.plan(older.snapshot, RestoreMode.Replace))
        backups.apply(backups.plan(cleared.snapshot, RestoreMode.Merge))
        assertEquals("the opening balance is left out beside the entries it replaced", balance, personDao.getBalanceNow(asha))

        people.clearTransactions(asha)
        assertEquals(balance, personDao.getBalanceNow(asha))
        backups.apply(backups.plan(cleared.snapshot, RestoreMode.Merge))

        assertEquals("this phone's own opening balance holds the same entries", balance, personDao.getBalanceNow(asha))
    }

    @Test
    fun mergingAnOlderBackupAfterAnEditKeepsTheEdit() = runBlocking {
        val (asha, _) = seed()
        val older = decodeBackup(backups.exportJson(1, "3.7.0")) as BackupResult.Ok
        val dinner = personDao.getTransactionsForPersonNow(asha).single { it.note == "Dinner" }
        people.editTransaction(dinner.copy(amountMinor = 2_000))
        val balance = personDao.getBalanceNow(asha)

        backups.apply(backups.plan(older.snapshot, RestoreMode.Merge))

        assertEquals(balance, personDao.getBalanceNow(asha))
        assertEquals(2, personDao.getTransactionsForPersonNow(asha).size)
    }

    @Test
    fun mergingAddsOnlyWhatIsMissing() = runBlocking {
        val (asha, _) = seed()
        val snapshot = backups.snapshot()

        // One extra entry that is not in the ledger yet.
        val extra = snapshot.copy(
            transactions = snapshot.transactions +
                Transaction(id = 999, personId = asha, amountMinor = 1_000, timestamp = 9_000, note = "New")
        )
        val before = personDao.getBalanceNow(asha)

        backups.apply(backups.plan(extra, RestoreMode.Merge))

        assertEquals(4, backups.snapshot().transactions.size)
        assertEquals(before + 1_000, personDao.getBalanceNow(asha))
    }

    /**
     * Deleting Asha hands her share of your dinner to you and takes her taxi with her. Merging the
     * backup back must bring both halves of her group history: she owes you ₹50 again, not you her.
     */
    @Test
    fun mergingBackADeletedMemberRestoresHerGroupPosition() = runBlocking {
        val selfId = personDao.ensureSelf().id
        val asha = personDao.insertPerson(Person(name = "Asha", currency = "INR", sortOrder = 0))
        val groupId = groups.createGroup("Goa", "INR", listOf(selfId, asha))
        groups.addExpense(groupId, "Dinner", 20_000, selfId, listOf(selfId, asha))
        groups.addExpense(groupId, "Taxi", 10_000, asha, listOf(selfId, asha))
        val json = backups.exportJson(1, "3.7.0")

        people.deletePerson(personDao.getPersonNow(asha)!!)
        val decoded = decodeBackup(json) as BackupResult.Ok
        backups.apply(backups.plan(decoded.snapshot, RestoreMode.Merge))

        val back = personDao.getAllPersonsForBackup().single { it.name == "Asha" }.id
        val positions = groupDao.paidByMember(groupId).associate { it.personId to it.amountMinor }
            .let { paid ->
                groupDao.owedByMember(groupId).associate { it.personId to (paid[it.personId] ?: 0L) - it.amountMinor }
            }
        assertEquals(mapOf(selfId to 5_000L, back to -5_000L), positions)

        val again = backups.plan(decoded.snapshot, RestoreMode.Merge)
        assertTrue("a second Merge changes nothing", again.changesNothing)
    }

    /**
     * As above, with Asha added again by name before the Merge, the way someone would put a
     * deleted friend back. Merge matches her rather than adding her, and her share of your dinner
     * must still come back with her taxi.
     */
    @Test
    fun mergingBackADeletedMemberAddedAgainRestoresHerGroupPosition() = runBlocking {
        val selfId = personDao.ensureSelf().id
        val asha = personDao.insertPerson(Person(name = "Asha", currency = "INR", sortOrder = 0))
        val groupId = groups.createGroup("Goa", "INR", listOf(selfId, asha))
        groups.addExpense(groupId, "Dinner", 20_000, selfId, listOf(selfId, asha))
        groups.addExpense(groupId, "Taxi", 10_000, asha, listOf(selfId, asha))
        val json = backups.exportJson(1, "3.7.0")

        people.deletePerson(personDao.getPersonNow(asha)!!)
        val again = personDao.insertPerson(Person(name = "Asha", currency = "INR", sortOrder = 0))
        val decoded = decodeBackup(json) as BackupResult.Ok
        backups.apply(backups.plan(decoded.snapshot, RestoreMode.Merge))

        assertEquals("the Asha added again is the one matched", listOf(again), personDao.getAllPersonsForBackup().filter { it.name == "Asha" }.map { it.id })
        val positions = groupDao.paidByMember(groupId).associate { it.personId to it.amountMinor }
            .let { paid ->
                groupDao.owedByMember(groupId).associate { it.personId to (paid[it.personId] ?: 0L) - it.amountMinor }
            }
        assertEquals(mapOf(selfId to 5_000L, again to -5_000L), positions)
        assertTrue("a second Merge changes nothing", backups.plan(decoded.snapshot, RestoreMode.Merge).changesNothing)
    }

    /**
     * Ravi is in Goa and has no entries of his own: a taxi he paid, split with you. Renamed "Ravi K"
     * since the backup, nothing finds him by uid, so a Merge brought him back as someone new and
     * the taxi again with him: you owed ₹100 instead of ₹50.
     */
    @Test
    fun mergingAfterRenamingAMemberWithNoEntriesCountsNothingHePaidTwice() = runBlocking {
        val selfId = personDao.ensureSelf().id
        val ravi = personDao.insertPerson(Person(name = "Ravi", currency = "INR", sortOrder = 0))
        val groupId = groups.createGroup("Goa", "INR", listOf(selfId, ravi))
        groups.addExpense(groupId, "Taxi", 10_000, ravi, listOf(selfId, ravi))
        val json = backups.exportJson(1, "3.7.1")

        people.updatePerson(personDao.getPersonNow(ravi)!!.copy(name = "Ravi K"))
        val decoded = decodeBackup(json) as BackupResult.Ok
        backups.apply(backups.plan(decoded.snapshot, RestoreMode.Merge))

        val positions = groupDao.paidByMember(groupId).associate { it.personId to it.amountMinor }
            .let { paid ->
                groupDao.owedByMember(groupId).associate { it.personId to (paid[it.personId] ?: 0L) - it.amountMinor }
            }
        assertEquals("you still owe Ravi ₹50", mapOf(selfId to -5_000L, ravi to 5_000L), positions.filterValues { it != 0L })
        assertEquals(listOf(selfId, ravi).sorted(), groupDao.getMemberIds(groupId).sorted())
    }

    // -----------------------------------------------------------------------------------------
    // CSV
    // -----------------------------------------------------------------------------------------

    /**
     * A CSV restores people and entries and nothing else. Proving it here rather than trusting the
     * comment, because a Replace from one deletes groups it cannot put back.
     */
    @Test
    fun aCsvRoundTripRestoresPeopleAndEntriesButNotGroups() = runBlocking {
        val (asha, _) = seed()
        val ashaBefore = personDao.getBalanceNow(asha)
        val csv = buildLedgerCsv(people.ledgerSnapshot())

        personDao.deleteAllPersons()
        groupDao.deleteAllGroups()
        personDao.ensureSelf()

        val parsed = readLedgerCsv(csv) as CsvImportResult.Ok
        backups.apply(backups.plan(parsed.people.toSnapshot(), RestoreMode.Merge))

        val after = backups.snapshot()
        assertEquals("people come back", 2, after.persons.count { !it.isSelf })
        assertTrue("entries come back", after.transactions.isNotEmpty())
        assertTrue("a csv carries no groups", after.groups.isEmpty())

        val ashaAfter = after.persons.first { it.name == "Asha" }
        assertEquals(
            "the direct entries must add up to what they did before",
            ashaBefore,
            personDao.getBalanceNow(ashaAfter.id)
        )
    }

    @Test
    fun aCsvRestoreBringsASettledPersonBackEven() = runBlocking {
        val id = personDao.insertPerson(Person(name = "Closed", currency = "INR"))
        personDao.recordEntry(Transaction(personId = id, amountMinor = 5_000, timestamp = 1, note = "a"))
        personDao.recordEntry(Transaction(personId = id, amountMinor = 3_000, timestamp = 2, note = "b"))
        personDao.settle(id)

        val csv = buildLedgerCsv(people.ledgerSnapshot())
        personDao.deleteAllPersons()
        personDao.ensureSelf()

        val parsed = readLedgerCsv(csv) as CsvImportResult.Ok
        backups.apply(backups.plan(parsed.people.toSnapshot(), RestoreMode.Merge))

        val restored = backups.snapshot().persons.first { it.name == "Closed" }
        assertTrue("the CSV still says settled, for older versions", csv.contains("Closed,INR,0.00,yes,"))
        assertEquals("a settled person must come back even", 0L, personDao.getBalanceNow(restored.id))
    }

    // -----------------------------------------------------------------------------------------
    // Atomicity
    // -----------------------------------------------------------------------------------------

    /**
     * A restore spans every table, so it runs inside one transaction. If it could fail halfway,
     * the result would be a ledger with people but no entries, worse than the state the user was
     * trying to recover from.
     */
    @Test
    fun aRestoreThatFailsLeavesTheLedgerUntouched() = runBlocking {
        val (asha, _) = seed()
        val before = backups.snapshot()
        val balanceBefore = personDao.getBalanceNow(asha)

        // A plan whose expense points at a group that will not exist. Room's foreign keys reject
        // it partway through the inserts, which is exactly the failure this is about.
        val poisoned = backups.plan(before, RestoreMode.Merge).copy(
            expenses = listOf(
                Expense(id = 9_999, groupId = 8_888, description = "Bad", amountMinor = 1, paidByPersonId = asha, timestamp = 1)
            )
        )

        val failed = runCatching { backups.apply(poisoned) }.isFailure
        assertTrue("the poisoned plan should have been rejected", failed)

        val after = backups.snapshot()
        assertEquals(before.persons.size, after.persons.size)
        assertEquals(before.transactions.size, after.transactions.size)
        assertEquals(before.expenses.size, after.expenses.size)
        assertEquals(balanceBefore, personDao.getBalanceNow(asha))
        assertFalse("no partial row may survive", after.expenses.any { it.description == "Bad" })
    }

    @Test
    fun theExportNamesItselfAndCanBeReadBack() = runBlocking {
        seed()
        val json = backups.exportJson(4_242, "2.2.0")
        val decoded = decodeBackup(json)
        assertTrue("expected Ok, got $decoded", decoded is BackupResult.Ok)
        assertEquals(4_242L, (decoded as BackupResult.Ok).exportedAt)
        assertEquals("2.2.0", decoded.appVersion)
        assertNotNull(backupFileName(BackupWriterStamp))
    }

    private val BackupWriterStamp = "20260928-143000"
}
