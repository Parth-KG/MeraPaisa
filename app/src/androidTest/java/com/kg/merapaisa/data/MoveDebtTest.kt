package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.repository.PersonRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Moving part of one person's debt onto another.
 *
 * The invariant worth protecting: **the total owed to you must not change.** Only who owes it. A
 * move that quietly created or destroyed money would be the worst possible bug in a ledger, and it
 * would be invisible — every individual balance would still look plausible.
 */
@RunWith(AndroidJUnit4::class)
class MoveDebtTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PersonDao
    private lateinit var repo: PersonRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.personDao()
        repo = PersonRepository(dao)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun person(name: String, owes: Long, currency: String = "INR"): Long {
        val id = dao.insertPerson(Person(name = name, currency = currency))
        if (owes != 0L) {
            dao.recordEntry(Transaction(personId = id, amountMinor = owes, timestamp = 1_000, note = "seed"))
        }
        return id
    }

    private suspend fun totalOwed(): Long =
        dao.getAllTransactionsNow().sumOf { it.amountMinor }

    // -----------------------------------------------------------------------------------------
    // The invariant
    // -----------------------------------------------------------------------------------------

    @Test
    fun movingChangesWhoOwesButNotTheTotal() = runBlocking {
        val rondu = person("Rondu", 62_400)
        val sasti = person("Sasti", 0)
        val before = totalOwed()

        val result = repo.moveDebt(rondu, sasti, 10_000)

        assertTrue("expected Moved, got $result", result is MoveDebtResult.Moved)
        assertEquals(52_400L, dao.getBalanceNow(rondu))
        assertEquals(10_000L, dao.getBalanceNow(sasti))
        assertEquals("the total owed to you must not move", before, totalOwed())
    }

    @Test
    fun theTwoEntriesShareATimestampAndNameEachOther() = runBlocking {
        val rondu = person("Rondu", 62_400)
        val sasti = person("Sasti", 0)
        repo.moveDebt(rondu, sasti, 10_000)

        val out = dao.getTransactionsForPersonNow(rondu).last()
        val into = dao.getTransactionsForPersonNow(sasti).single()

        assertEquals("they are one event", out.timestamp, into.timestamp)
        assertEquals(-10_000L, out.amountMinor)
        assertEquals(10_000L, into.amountMinor)
        assertTrue("each side should name the other", out.note.contains("Sasti"))
        assertTrue(into.note.contains("Rondu"))
    }

    @Test
    fun anOptionalNoteReachesBothEntries() = runBlocking {
        val a = person("A", 20_000)
        val b = person("B", 0)
        repo.moveDebt(a, b, 5_000, note = "Sasti covered it")

        assertTrue(dao.getTransactionsForPersonNow(a).last().note.contains("Sasti covered it"))
        assertTrue(dao.getTransactionsForPersonNow(b).single().note.contains("Sasti covered it"))
    }

    @Test
    fun movingTheWholeBalanceLeavesTheSourceAtZero() = runBlocking {
        val a = person("A", 30_000)
        val b = person("B", 0)
        val before = totalOwed()

        assertTrue(repo.moveDebt(a, b, 30_000) is MoveDebtResult.Moved)
        assertEquals(0L, dao.getBalanceNow(a))
        assertEquals(30_000L, dao.getBalanceNow(b))
        assertEquals(before, totalOwed())
    }

    /** Receiving a debt means they owe something again, so they belong back in the active list. */
    @Test
    fun theReceiverIsReopened() = runBlocking {
        val a = person("A", 30_000)
        val b = person("B", 0)
        dao.settle(b)
        assertTrue(dao.getPersonNow(b)!!.isSettled)

        repo.moveDebt(a, b, 10_000)

        assertFalse(dao.getPersonNow(b)!!.isSettled)
    }

    // -----------------------------------------------------------------------------------------
    // Refusals — each must write nothing
    // -----------------------------------------------------------------------------------------

    private suspend fun assertWroteNothing(block: suspend () -> MoveDebtResult): MoveDebtResult {
        val before = dao.getAllTransactionsNow().size
        val total = totalOwed()
        val result = block()
        assertEquals("a refusal must write no entries", before, dao.getAllTransactionsNow().size)
        assertEquals(total, totalOwed())
        return result
    }

    @Test
    fun movingMoreThanIsOwedIsRefused() = runBlocking {
        val a = person("A", 10_000)
        val b = person("B", 0)
        val result = assertWroteNothing { repo.moveDebt(a, b, 15_000) }

        assertTrue("expected MoreThanOwed, got $result", result is MoveDebtResult.MoreThanOwed)
        assertEquals(10_000L, (result as MoveDebtResult.MoreThanOwed).availableMinor)
    }

    /**
     * Refused rather than converted: the amounts are minor units with no rate attached, so moving
     * ₹100 onto a dollar balance would silently claim $100.
     */
    @Test
    fun movingBetweenCurrenciesIsRefused() = runBlocking {
        val a = person("A", 10_000, currency = "INR")
        val b = person("B", 0, currency = "USD")
        val result = assertWroteNothing { repo.moveDebt(a, b, 5_000) }

        assertTrue("expected CurrencyMismatch, got $result", result is MoveDebtResult.CurrencyMismatch)
        result as MoveDebtResult.CurrencyMismatch
        assertEquals("INR", result.from)
        assertEquals("USD", result.to)
    }

    /** An older row may store a symbol rather than a code; those are the same currency. */
    @Test
    fun aLegacyCurrencySymbolStillCountsAsAMatch() = runBlocking {
        val a = dao.insertPerson(Person(name = "A", currency = "₹"))
        dao.recordEntry(Transaction(personId = a, amountMinor = 10_000, timestamp = 1, note = "seed"))
        val b = person("B", 0, currency = "INR")

        assertTrue(repo.moveDebt(a, b, 5_000) is MoveDebtResult.Moved)
    }

    @Test
    fun movingFromSomeoneWhoOwesNothingIsRefused() = runBlocking {
        val a = person("A", 0)
        val b = person("B", 0)
        assertTrue(assertWroteNothing { repo.moveDebt(a, b, 1_000) } is MoveDebtResult.NothingToMove)
    }

    /** You owe them, so there is no debt of theirs to hand on. */
    @Test
    fun movingFromSomeoneYouOweIsRefused() = runBlocking {
        val a = person("A", -20_000)
        val b = person("B", 0)
        assertTrue(assertWroteNothing { repo.moveDebt(a, b, 1_000) } is MoveDebtResult.NothingToMove)
    }

    @Test
    fun movingToTheSamePersonIsRefused() = runBlocking {
        val a = person("A", 20_000)
        assertTrue(assertWroteNothing { repo.moveDebt(a, a, 1_000) } is MoveDebtResult.SamePerson)
    }

    @Test
    fun zeroAndNegativeAmountsAreRefused() = runBlocking {
        val a = person("A", 20_000)
        val b = person("B", 0)
        assertTrue(assertWroteNothing { repo.moveDebt(a, b, 0) } is MoveDebtResult.NotAnAmount)
        assertTrue(assertWroteNothing { repo.moveDebt(a, b, -5_000) } is MoveDebtResult.NotAnAmount)
    }

    // -----------------------------------------------------------------------------------------
    // Repeated moves
    // -----------------------------------------------------------------------------------------

    @Test
    fun movingTwiceAccumulatesWithoutDrift() = runBlocking {
        val a = person("A", 30_000)
        val b = person("B", 0)
        val before = totalOwed()

        repo.moveDebt(a, b, 10_000)
        repo.moveDebt(a, b, 5_000)

        assertEquals(15_000L, dao.getBalanceNow(a))
        assertEquals(15_000L, dao.getBalanceNow(b))
        assertEquals(before, totalOwed())
    }

    @Test
    fun aDebtCanBeMovedOnAgain() = runBlocking {
        val a = person("A", 30_000)
        val b = person("B", 0)
        val c = person("C", 0)
        val before = totalOwed()

        repo.moveDebt(a, b, 20_000)
        repo.moveDebt(b, c, 20_000)

        assertEquals(10_000L, dao.getBalanceNow(a))
        assertEquals(0L, dao.getBalanceNow(b))
        assertEquals(20_000L, dao.getBalanceNow(c))
        assertEquals(before, totalOwed())
    }
}
