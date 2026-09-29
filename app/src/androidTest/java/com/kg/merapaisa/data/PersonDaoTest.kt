package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the settle/reopen lifecycle and the derived balance it depends on. */
@RunWith(AndroidJUnit4::class)
class PersonDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PersonDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.personDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun newPerson(name: String = "Asha"): Long =
        dao.insertPerson(Person(name = name, currency = "INR"))

    private suspend fun personById(id: Long) =
        dao.getPersonsWithBalances().first().single { it.id == id }

    @Test
    fun settleClosesTheBalanceAndMarksThePersonSettled() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 250_00, note = "dinner"))
        assertEquals(250_00L, dao.getBalanceNow(id))

        dao.settle(id)

        assertEquals("settling must close the balance out exactly", 0L, dao.getBalanceNow(id))
        assertTrue(personById(id).isSettled)
        val closing = dao.getTransactionsForPerson(id).first().first { it.note == "Settled" }
        assertEquals(-250_00L, closing.amountMinor)
    }

    @Test
    fun settleFilesAwaySomeoneAlreadyAtZeroWithoutInventingAnEntry() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 100_00))
        dao.recordEntry(Transaction(personId = id, amountMinor = -100_00))

        dao.settle(id)

        assertTrue(personById(id).isSettled)
        assertEquals("no closing entry is needed at zero", 2, dao.getTransactionCount(id).first())
    }

    @Test
    fun reopenUnfilesWithoutResurrectingTheOldBalance() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 250_00))
        dao.settle(id)

        dao.reopen(id)

        assertFalse(personById(id).isSettled)
        assertEquals("the closing entry is real history and stays", 0L, dao.getBalanceNow(id))
    }

    @Test
    fun recordingMoneyAgainstASettledPersonReopensThem() = runBlocking {
        val id = newPerson()
        dao.settle(id)
        assertTrue(personById(id).isSettled)

        dao.recordEntry(Transaction(personId = id, amountMinor = 40_00, note = "cab"))

        assertFalse("money moving again means the debt is live again", personById(id).isSettled)
        assertEquals(40_00L, dao.getBalanceNow(id))
    }

    @Test
    fun aSplitReopensEveryPersonItTouches() = runBlocking {
        val a = newPerson("A")
        val b = newPerson("B")
        dao.settle(a)
        dao.settle(b)

        dao.recordEntries(
            listOf(
                Transaction(personId = a, amountMinor = 30_00, note = "Split"),
                Transaction(personId = b, amountMinor = 30_00, note = "Split")
            )
        )

        assertFalse(personById(a).isSettled)
        assertFalse(personById(b).isSettled)
    }

    @Test
    fun rollingBackTheSettlementRestoresTheBalanceAndReopensThem() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 80_00, timestamp = 1_000))
        dao.settle(id)
        val closing = dao.getTransactionsForPersonNow(id).single { it.note == "Settled" }

        dao.rollbackTo(id, since = closing.timestamp)

        assertEquals("the debt is owed again", 80_00L, dao.getBalanceNow(id))
        assertFalse(personById(id).isSettled)
    }

    @Test
    fun rollingBackARangeThatNetsToZeroChangesNothing() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 80_00, timestamp = 1_000))
        dao.settle(id)
        val countBefore = dao.getTransactionCount(id).first()

        // This range covers the original entry and the closing entry, which cancel out.
        dao.rollbackTo(id, since = 1_000)

        assertEquals(0L, dao.getBalanceNow(id))
        assertEquals(
            "with nothing to reverse, no entry should be written",
            countBefore,
            dao.getTransactionCount(id).first()
        )
        assertTrue("still square, so still settled", personById(id).isSettled)
    }

    @Test
    fun clearingTheLogKeepsTheBalanceAndTheSettledFlag() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 75_50, note = "one"))
        dao.recordEntry(Transaction(personId = id, amountMinor = 24_50, note = "two"))

        dao.clearTransactionsForPerson(id)

        assertEquals("clearing the log must not change what is owed", 100_00L, dao.getBalanceNow(id))
        assertEquals(1, dao.getTransactionCount(id).first())
        assertFalse(personById(id).isSettled)
    }

    @Test
    fun clearingTheLogKeepsWhatWasAlreadySharedOutOfTheNextLink() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 300_00, timestamp = 1_000, note = "shared"))
        dao.recordEntry(Transaction(personId = id, amountMinor = 40_00, timestamp = 2_000, note = "not yet"))
        dao.setLastSharedAt(id, 1_000)

        dao.clearTransactionsForPerson(id)

        assertEquals(340_00L, dao.getBalanceNow(id))
        val watermark = dao.getPersonNow(id)!!.lastSharedAt
        val nextLink = dao.getTransactionsSinceNow(id, watermark)
        assertEquals(
            "only the part the other phone has not seen may go out again",
            listOf(40_00L),
            nextLink.map { it.amountMinor }
        )
    }

    @Test
    fun clearingTheLogDoesNotSendTheirOwnEntriesBack() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 300_00, timestamp = 5_000, note = "theirs", fromShare = true))
        dao.recordEntry(Transaction(personId = id, amountMinor = 20_00, timestamp = 6_000, note = "mine"))

        dao.clearTransactionsForPerson(id)

        assertEquals(320_00L, dao.getBalanceNow(id))
        val nextLink = dao.getTransactionsSinceNow(id, dao.getPersonNow(id)!!.lastSharedAt)
        assertEquals("their ₹300 came from them, so only mine goes out", listOf(20_00L), nextLink.map { it.amountMinor })
    }

    @Test
    fun clearingAndDeletingKeepTheUidsTheyRemove() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 10_00, note = "typo", uid = "gone"))
        dao.recordEntry(Transaction(personId = id, amountMinor = 30_00, note = "cab", uid = "folded"))

        dao.removeTransaction(dao.getTransactionsForPersonNow(id).single { it.uid == "gone" })
        dao.clearTransactionsForPerson(id)

        val retired = dao.getRetiredUidsNow(id).associate { it.uid to it.reason }
        assertEquals(RETIRED_DELETED, retired["gone"])
        assertEquals(RETIRED_CLEARED, retired["folded"])
    }

    @Test
    fun clearingANeverSharedLogLeavesTheWholeBalanceToShare() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 75_00, note = "one"))

        dao.clearTransactionsForPerson(id)

        assertEquals(0L, dao.getPersonNow(id)!!.lastSharedAt)
        assertEquals(listOf(75_00L), dao.getTransactionsSinceNow(id, 0).map { it.amountMinor })
    }

    @Test
    fun editingAnEntryCorrectsTheBalanceAndKeepsItsTimestamp() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 1_000_00, timestamp = 42, note = "typo"))
        val entry = dao.getTransactionsForPersonNow(id).single()

        dao.editTransaction(entry.copy(amountMinor = 100_00, note = "cab"))

        val corrected = dao.getTransactionsForPersonNow(id).single()
        assertEquals(100_00L, corrected.amountMinor)
        assertEquals("cab", corrected.note)
        assertEquals("a correction keeps the entry's place in history", 42L, corrected.timestamp)
        assertEquals(100_00L, dao.getBalanceNow(id))
    }

    @Test
    fun deletingAnEntryRemovesItFromTheBalance() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 100_00, timestamp = 1, note = "keep"))
        dao.recordEntry(Transaction(personId = id, amountMinor = 250_00, timestamp = 2, note = "mistake"))
        val mistake = dao.getTransactionsForPersonNow(id).single { it.note == "mistake" }

        dao.removeTransaction(mistake)

        assertEquals(100_00L, dao.getBalanceNow(id))
        assertEquals(1, dao.getTransactionCount(id).first())
    }

    @Test
    fun correctingASettledPersonOffZeroReopensThem() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 100_00, timestamp = 1))
        dao.settle(id)
        assertTrue(personById(id).isSettled)

        val closing = dao.getTransactionsForPersonNow(id).single { it.note == "Settled" }
        dao.editTransaction(closing.copy(amountMinor = -40_00))

        assertEquals(60_00L, dao.getBalanceNow(id))
        assertFalse("a balance that is no longer zero is no longer settled", personById(id).isSettled)
    }

    @Test
    fun deletingAnEntryThatLeavesZeroKeepsThePersonSettled() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 100_00, timestamp = 1))
        dao.settle(id)
        // Remove a stray zero-value note; the balance is unaffected.
        dao.recordEntry(Transaction(personId = id, amountMinor = 0, timestamp = 2, note = "note only"))
        val stray = dao.getTransactionsForPersonNow(id).single { it.note == "note only" }

        dao.removeTransaction(stray)

        assertEquals(0L, dao.getBalanceNow(id))
        assertTrue("still square, so still settled", personById(id).isSettled)
    }

    // --- groups are kept apart from the people list ---

    private suspend fun groupWith(members: List<Long>, currency: String = "INR"): Long {
        val groupId = db.groupDao().insertGroup(Group(name = "Trip", currency = currency))
        db.groupDao().addMembers(members.map { GroupMember(groupId = groupId, personId = it) })
        return groupId
    }

    private suspend fun spend(groupId: Long, paidBy: Long, amountMinor: Long, sharedWith: List<Long>) {
        db.groupDao().recordExpense(
            expense = Expense(groupId = groupId, description = "Hotel", amountMinor = amountMinor, paidByPersonId = paidBy),
            sharesByPerson = evenShares(amountMinor, sharedWith)
        )
    }

    @Test
    fun aGroupExpenseLeavesEveryonesBalanceWhereItWas() = runBlocking {
        val me = dao.ensureSelf().id
        val a = newPerson("A"); val b = newPerson("B")
        dao.recordEntry(Transaction(personId = a, amountMinor = 50_00))

        val group = groupWith(listOf(me, a, b))
        spend(group, paidBy = me, amountMinor = 500_00, sharedWith = listOf(me, a, b))
        spend(group, paidBy = a, amountMinor = 90_00, sharedWith = listOf(me, a))

        val byId = dao.getPersonsWithBalances().first().associateBy { it.id }
        assertEquals("only the direct entry counts", 50_00L, byId[a]!!.balanceMinor)
        assertEquals(0L, byId[b]!!.balanceMinor)
    }

    @Test
    fun aGroupExpenseDoesNotReopenSomeoneSettled() = runBlocking {
        val me = dao.ensureSelf().id
        val a = newPerson("A")
        dao.settle(a)
        spend(groupWith(listOf(me, a)), paidBy = me, amountMinor = 100_00, sharedWith = listOf(me, a))

        assertTrue("what happens in a group stays in the group", personById(a).isSettled)
    }

    @Test
    fun settlingSomeoneWritesNothingIntoTheirGroups() = runBlocking {
        val me = dao.ensureSelf().id
        val a = newPerson("A")
        dao.recordEntry(Transaction(personId = a, amountMinor = 50_00))
        val group = groupWith(listOf(me, a))
        spend(group, paidBy = me, amountMinor = 100_00, sharedWith = listOf(me, a))

        dao.settle(a)

        assertEquals(0L, dao.getBalanceNow(a))
        assertEquals("the group still has only its one expense", 1, db.groupDao().getExpenses(group).first().size)
    }

    @Test
    fun deletingAGroupLeavesBalancesAlone() = runBlocking {
        val me = dao.ensureSelf().id
        val a = newPerson("A")
        dao.recordEntry(Transaction(personId = a, amountMinor = 70_00))
        val group = groupWith(listOf(me, a))
        spend(group, paidBy = me, amountMinor = 100_00, sharedWith = listOf(me, a))

        db.groupDao().deleteGroup(group)

        assertEquals(70_00L, personById(a).balanceMinor)
    }
}
