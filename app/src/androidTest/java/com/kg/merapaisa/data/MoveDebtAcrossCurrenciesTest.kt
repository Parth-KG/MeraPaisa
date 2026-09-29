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
 * Moving a debt onto someone kept in another currency, at a rate fetched for the move.
 *
 * The plain move still refuses a currency mismatch, and MoveDebtTest still holds it to that: with
 * no rate, ₹100 would be claimed as $100. This is the path with a rate. The sender goes down in
 * rupees, the receiver up in dollars, both entries at one moment, both saying what was converted.
 */
@RunWith(AndroidJUnit4::class)
class MoveDebtAcrossCurrenciesTest {

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

    private suspend fun person(name: String, owes: Long, currency: String): Long {
        val id = dao.insertPerson(Person(name = name, currency = currency))
        if (owes != 0L) {
            dao.recordEntry(Transaction(personId = id, amountMinor = owes, timestamp = 1_000, note = "seed"))
        }
        return id
    }

    @Test
    fun eachSideMovesInItsOwnCurrency() = runBlocking {
        val asha = person("Asha", 5_000_00, "INR")
        val diego = person("Diego", 0, "USD")

        val result = repo.moveDebtConverted(asha, diego, amountMinor = 1_000_00, convertedMinor = 12_05)

        assertTrue("expected Moved, got $result", result is MoveDebtResult.Moved)
        assertEquals("Asha owes ₹1,000 less", 4_000_00L, dao.getBalanceNow(asha))
        assertEquals("Diego owes the converted $12.05", 12_05L, dao.getBalanceNow(diego))
    }

    @Test
    fun bothEntriesShareAMomentAndSayWhatWasConverted() = runBlocking {
        val asha = person("Asha", 5_000_00, "INR")
        val diego = person("Diego", 0, "USD")
        repo.moveDebtConverted(asha, diego, 1_000_00, 12_05, note = "Diego covered it")

        val out = dao.getTransactionsForPersonNow(asha).last()
        val into = dao.getTransactionsForPersonNow(diego).single()
        assertEquals(out.timestamp, into.timestamp)
        assertTrue(out.note, out.note.startsWith("Moved to Diego"))
        assertTrue(into.note, into.note.startsWith("Moved from Asha"))
        listOf(out, into).forEach {
            assertTrue("names both sides of the conversion: ${it.note}", "12.05" in it.note && "1,000" in it.note)
            assertTrue("keeps the reason: ${it.note}", it.note.endsWith("Diego covered it"))
        }
    }

    @Test
    fun theUsualChecksStillApply() = runBlocking {
        val asha = person("Asha", 500_00, "INR")
        val diego = person("Diego", 0, "USD")

        assertTrue(repo.moveDebtConverted(asha, diego, 600_00, 7_20) is MoveDebtResult.MoreThanOwed)
        assertTrue(repo.moveDebtConverted(asha, diego, 0, 0) is MoveDebtResult.NotAnAmount)
        assertTrue(repo.moveDebtConverted(asha, asha, 100_00, 100_00) is MoveDebtResult.SamePerson)
        assertEquals("a refused move writes nothing", 500_00L, dao.getBalanceNow(asha))
        assertEquals(0L, dao.getBalanceNow(diego))
    }

    @Test
    fun thePlainMoveStillRefusesWithoutARate() = runBlocking {
        val asha = person("Asha", 500_00, "INR")
        val diego = person("Diego", 0, "USD")
        assertTrue(repo.moveDebt(asha, diego, 100_00) is MoveDebtResult.CurrencyMismatch)
        assertFalse(dao.getTransactionsForPersonNow(diego).isNotEmpty())
    }
}
