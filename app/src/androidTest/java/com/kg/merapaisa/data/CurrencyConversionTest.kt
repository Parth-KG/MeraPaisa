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
 * Converting a person's currency against a real database.
 *
 * `convertAll` is covered by JVM tests; what needs Room is that the rewritten rows actually land,
 * that the balance derived from them matches, and that the log is left wholly in one currency with
 * no adjustment entry — which was the whole complaint that produced this change.
 */
@RunWith(AndroidJUnit4::class)
class CurrencyConversionTest {

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

    private suspend fun seed(currency: String = "INR"): Long {
        val id = dao.insertPerson(Person(name = "Asha", currency = currency))
        dao.recordEntry(Transaction(personId = id, amountMinor = 20_000, timestamp = 1_000, note = "Dinner"))
        dao.recordEntry(Transaction(personId = id, amountMinor = 5_050, timestamp = 2_000, note = "Chai"))
        dao.recordEntry(Transaction(personId = id, amountMinor = -10_000, timestamp = 3_000, note = "Part payment"))
        return id
    }

    // -----------------------------------------------------------------------------------------
    // The core behaviour
    // -----------------------------------------------------------------------------------------

    @Test
    fun everyEntryIsRewritten() = runBlocking {
        val id = seed()
        repo.convertCurrency(id, "USD", 2.0)

        val entries = dao.getTransactionsForPersonNow(id)
        assertEquals("no entry may be added or lost", 3, entries.size)
        assertEquals(listOf(40_000L, 10_100L, -20_000L), entries.map { it.amountMinor })
    }

    /**
     * The invariant that matters. Balances are derived by summing entries, so if these disagree the
     * person's balance contradicts the log that produces it.
     */
    @Test
    fun theBalanceMatchesTheRewrittenEntries() = runBlocking {
        val id = seed()
        val before = dao.getBalanceNow(id)
        repo.convertCurrency(id, "USD", 0.0121)

        val entries = dao.getTransactionsForPersonNow(id)
        assertEquals(dao.getBalanceNow(id), entries.sumOf { it.amountMinor })
        assertEquals(Math.round(before * 0.0121), dao.getBalanceNow(id))
    }

    /** The complaint that produced this change: the conversion used to show up as an entry. */
    @Test
    fun noAdjustmentEntryIsLeftBehind() = runBlocking {
        val id = seed()
        repo.convertCurrency(id, "USD", 2.0)

        val notes = dao.getTransactionsForPersonNow(id).map { it.note }
        assertEquals(listOf("Dinner", "Chai", "Part payment"), notes)
        assertFalse("the log must not mention the conversion", notes.any { it.contains("Convert", true) })
    }

    @Test
    fun theCurrencyLabelChanges() = runBlocking {
        val id = seed()
        repo.convertCurrency(id, "USD", 2.0)
        assertEquals("USD", dao.getPersonNow(id)!!.currency)
    }

    @Test
    fun timestampsAndNotesSurvive() = runBlocking {
        val id = seed()
        repo.convertCurrency(id, "USD", 0.37)

        val entries = dao.getTransactionsForPersonNow(id)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), entries.map { it.timestamp })
        assertEquals(listOf("Dinner", "Chai", "Part payment"), entries.map { it.note })
    }

    @Test
    fun repaymentsStayRepayments() = runBlocking {
        val id = seed()
        repo.convertCurrency(id, "USD", 0.0121)
        val entries = dao.getTransactionsForPersonNow(id)
        assertTrue("the part payment must still reduce the balance", entries.last().amountMinor < 0)
    }

    // -----------------------------------------------------------------------------------------
    // Edges
    // -----------------------------------------------------------------------------------------

    /**
     * Someone settled still has a history worth converting. The old code skipped conversion when
     * the balance was zero, which left their entries in the old currency under a new label.
     */
    @Test
    fun aSettledPersonsHistoryIsStillConverted() = runBlocking {
        val id = dao.insertPerson(Person(name = "Squared", currency = "INR"))
        dao.recordEntry(Transaction(personId = id, amountMinor = 10_000, timestamp = 1, note = "a"))
        dao.recordEntry(Transaction(personId = id, amountMinor = -10_000, timestamp = 2, note = "b"))
        assertEquals(0L, dao.getBalanceNow(id))

        repo.convertCurrency(id, "USD", 2.0)

        assertEquals(listOf(20_000L, -20_000L), dao.getTransactionsForPersonNow(id).map { it.amountMinor })
        assertEquals("a settled person stays settled", 0L, dao.getBalanceNow(id))
    }

    @Test
    fun aPersonWithNoEntriesJustGetsRelabelled() = runBlocking {
        val id = dao.insertPerson(Person(name = "Empty", currency = "INR"))
        repo.convertCurrency(id, "USD", 2.0)

        assertTrue(dao.getTransactionsForPersonNow(id).isEmpty())
        assertEquals("USD", dao.getPersonNow(id)!!.currency)
    }

    @Test
    fun onlyThatPersonIsTouched() = runBlocking {
        val asha = seed()
        val ravi = dao.insertPerson(Person(name = "Ravi", currency = "INR"))
        dao.recordEntry(Transaction(personId = ravi, amountMinor = 7_000, timestamp = 1, note = "x"))
        val raviBefore = dao.getBalanceNow(ravi)

        repo.convertCurrency(asha, "USD", 2.0)

        assertEquals("a conversion must not reach other people", raviBefore, dao.getBalanceNow(ravi))
        assertEquals("INR", dao.getPersonNow(ravi)!!.currency)
    }

    /**
     * A round trip through a coarser currency loses a little, and that is arithmetic rather than a
     * defect — but it is worth pinning down, because conversion is irreversible.
     *
     * ₹150.50 converts to $1.82105, which can only be stored as $1.82. Converting that back
     * multiplies the discarded 0.00105 by 1/0.0121 ≈ 82.6, giving ₹150.41. Nine paise vanish, and
     * there is no undo — the original amounts were overwritten.
     *
     * So the bound is not a fixed number of paise; it is the granularity of the currency passed
     * through. Asserting a tight tolerance here (the first version of this test said 2) fails for
     * reasons that have nothing to do with the code.
     */
    @Test
    fun aRoundTripLosesOnlyTheIntermediateCurrencysRounding() = runBlocking {
        val id = seed()
        val before = dao.getBalanceNow(id)
        val rate = 0.0121

        repo.convertCurrency(id, "USD", rate)
        repo.convertCurrency(id, "INR", 1.0 / rate)

        val after = dao.getBalanceNow(id)
        // Half a US cent, expressed back in paise — the most a single rounding can cost.
        val bound = Math.ceil(0.5 / rate).toLong() + 1
        assertTrue(
            "round trip drifted $before -> $after, beyond the $bound paise the rounding can explain",
            Math.abs(after - before) <= bound
        )
        assertTrue("a round trip should not gain money", after <= before)
    }

    /** With a rate whose inverse is exact, a round trip is exact too — no drift to explain away. */
    @Test
    fun aRoundTripAtAnExactRateIsLossless() = runBlocking {
        val id = seed()
        val before = dao.getBalanceNow(id)
        val entriesBefore = dao.getTransactionsForPersonNow(id).map { it.amountMinor }

        repo.convertCurrency(id, "USD", 2.0)
        repo.convertCurrency(id, "INR", 0.5)

        assertEquals(before, dao.getBalanceNow(id))
        assertEquals(entriesBefore, dao.getTransactionsForPersonNow(id).map { it.amountMinor })
    }

    @Test
    fun aLongHistoryStillReconciles() = runBlocking {
        val id = dao.insertPerson(Person(name = "Busy", currency = "INR"))
        repeat(120) { i ->
            dao.recordEntry(
                Transaction(personId = id, amountMinor = (i * 137L) - 5_000L, timestamp = i.toLong(), note = "e$i")
            )
        }
        val before = dao.getBalanceNow(id)

        repo.convertCurrency(id, "USD", 0.0121)

        val entries = dao.getTransactionsForPersonNow(id)
        assertEquals(120, entries.size)
        assertEquals(dao.getBalanceNow(id), entries.sumOf { it.amountMinor })
        assertEquals(Math.round(before * 0.0121), dao.getBalanceNow(id))
    }
}
