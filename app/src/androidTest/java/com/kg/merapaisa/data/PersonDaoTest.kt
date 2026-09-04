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
}
