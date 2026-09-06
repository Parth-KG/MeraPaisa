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
        dao.getPersonsWithBalances(dao.ensureSelf().id).first().single { it.id == id }

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

    // --- group activity reaching the people list ---

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

    /** The exact scenario: three people with existing balances, then a 500 split three ways. */
    @Test
    fun aGroupSplitYouPaidForRaisesEveryonesBalance() = runBlocking {
        val me = dao.ensureSelf().id
        val a = newPerson("A"); val b = newPerson("B"); val c = newPerson("C")
        dao.recordEntry(Transaction(personId = a, amountMinor = 50_00))
        dao.recordEntry(Transaction(personId = b, amountMinor = 100_00))
        dao.recordEntry(Transaction(personId = c, amountMinor = 120_00))

        val group = groupWith(listOf(me, a, b, c))
        spend(group, paidBy = me, amountMinor = 500_00, sharedWith = listOf(a, b, c))

        val byId = dao.getPersonsWithBalances(me).first().associateBy { it.id }
        // 500 over three is 166.67 / 166.67 / 166.66 — the extra paisa goes to the first.
        assertEquals(50_00L + 166_67L, byId[a]!!.balanceMinor)
        assertEquals(100_00L + 166_67L, byId[b]!!.balanceMinor)
        assertEquals(120_00L + 166_66L, byId[c]!!.balanceMinor)
        assertEquals(
            "the whole 500 should be reflected, not a paisa more or less",
            50_00L + 100_00L + 120_00L + 500_00L,
            byId.values.sumOf { it.balanceMinor }
        )
    }

    @Test
    fun anExpenseSomebodyElsePaidForLowersYourPositionWithThem() = runBlocking {
        val me = dao.ensureSelf().id
        val a = newPerson("A")
        val group = groupWith(listOf(me, a))
        spend(group, paidBy = a, amountMinor = 100_00, sharedWith = listOf(me, a))

        // They covered 50 of mine, so I owe them 50.
        assertEquals(-50_00L, dao.getPersonsWithBalances(me).first().single { it.id == a }.balanceMinor)
    }

    @Test
    fun anExpenseBetweenTwoOtherPeopleDoesNotTouchYourBalances() = runBlocking {
        val me = dao.ensureSelf().id
        val a = newPerson("A"); val b = newPerson("B")
        val group = groupWith(listOf(me, a, b))
        spend(group, paidBy = a, amountMinor = 80_00, sharedWith = listOf(b))

        val byId = dao.getPersonsWithBalances(me).first().associateBy { it.id }
        assertEquals("A paid for B, which is nothing to do with me", 0L, byId[a]!!.balanceMinor)
        assertEquals(0L, byId[b]!!.balanceMinor)
    }

    @Test
    fun settlingClosesTheDirectDebtAndLeavesTheGroupPositionAlone() = runBlocking {
        val me = dao.ensureSelf().id
        val a = newPerson("A")
        dao.recordEntry(Transaction(personId = a, amountMinor = 50_00))
        val group = groupWith(listOf(me, a))
        spend(group, paidBy = me, amountMinor = 100_00, sharedWith = listOf(a))

        dao.settle(a)

        // Settle records against the direct ledger, so what remains is the group position.
        assertEquals("the direct debt is closed", 0L, dao.getBalanceNow(a))
        assertEquals(
            "what they owe from the group is still owed, and is settled inside the group",
            100_00L,
            dao.getPersonsWithBalances(me).first().single { it.id == a }.balanceMinor
        )
    }
}
