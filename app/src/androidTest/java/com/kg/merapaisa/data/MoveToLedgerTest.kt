package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.repository.GroupRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Moving your part of a group to your ledger (v3.7.0), against a real database: both sides in one
 * transaction, Undo taking both, a second move taking only what is new, an edit after a move.
 *
 * From v3.7.1 the move takes what Settle up shows. The trip below is set to the debts as they arose
 * (Fewest payments off), so these cases stay pair by pair; the cases with it on are at the end.
 */
@RunWith(AndroidJUnit4::class)
class MoveToLedgerTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: GroupRepository
    private var me = 0L
    private var asha = 0L
    private var bilal = 0L
    private var chai = 0L
    private var group = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = GroupRepository(db.groupDao(), db.personDao())
        asha = db.personDao().insertPerson(Person(name = "Asha", currency = "INR"))
        bilal = db.personDao().insertPerson(Person(name = "Bilal", currency = "USD"))
        chai = db.personDao().insertPerson(Person(name = "Chaitanya", currency = "INR"))
        group = repo.createGroup("Goa", "INR", listOf(asha, bilal, chai), simplifyDebts = false)
        me = repo.self().id
        repo.addExpense(group, "Hotel", 900_00, me, listOf(me, asha, bilal))   // each owes me 300
        repo.addExpense(group, "Cab", 300_00, asha, listOf(me, asha, chai))    // I owe Asha 100
        repo.addExpense(group, "Dinner", 400_00, bilal, listOf(bilal, chai))   // nothing to do with me
    }

    @After
    fun tearDown() = db.close()

    private suspend fun detail() = repo.groupDetail(group).first()
    private suspend fun myPosition() = detail().balances.single { it.personId == me }.amountMinor
    /** What the move takes: Settle up's payments that involve you. */
    private suspend fun pairs() = moveLinesFor(detail().transfers, me)
    private suspend fun ledger(personId: Long) = db.personDao().getTransactionsForPerson(personId).first()

    /** Lines as the sheet makes them; Bilal's ledger is in dollars, at a made-up rate of 1/100. */
    private suspend fun lines() = pairs().map { (id, amount) ->
        MoveLine(id, amount, if (id == bilal) amount / 100 else amount)
    }

    @Test
    fun bothSidesAreWrittenAndYourPartComesToEven() = runBlocking {
        val before = detail().balances.associate { it.personId to it.amountMinor }
        val moved = pairs()
        val receipt = repo.moveToLedger(group, "Goa", lines())!!

        assertEquals(0L, myPosition())
        val after = detail().balances.associate { it.personId to it.amountMinor }
        assertEquals(before[asha]!! + moved[asha]!!, after[asha])
        assertEquals(before[bilal]!! + moved[bilal]!!, after[bilal])
        assertEquals("Chaitanya had no pair with you", before[chai], after[chai])

        val ashaEntry = ledger(asha).single()
        assertEquals(200_00L, ashaEntry.amountMinor)
        assertEquals("From Goa", ashaEntry.note)
        assertEquals("Bilal's entry is in his own currency", 3_00L, ledger(bilal).single().amountMinor)
        assertTrue(ledger(chai).isEmpty())
        assertEquals(2, receipt.people)
        assertTrue(detail().expenses.filter { it.isMovedToLedger }.size == 2)
    }

    @Test
    fun undoTakesBothSidesOut() = runBlocking {
        val before = detail()
        val receipt = repo.moveToLedger(group, "Goa", lines())!!
        repo.undoMove(receipt)
        assertEquals(before.balances, detail().balances)
        assertEquals(before.expenses.size, detail().expenses.size)
        assertTrue(ledger(asha).isEmpty())
        assertTrue(ledger(bilal).isEmpty())
    }

    @Test
    fun aSecondMoveTakesOnlyWhatIsNew() = runBlocking {
        repo.moveToLedger(group, "Goa", lines())!!
        repo.addExpense(group, "Breakfast", 200_00, me, listOf(me, asha))
        assertEquals(mapOf(asha to 100_00L), pairs())
        repo.moveToLedger(group, "Goa", lines())!!
        assertEquals(0L, myPosition())
        assertEquals(listOf(100_00L, 200_00L), ledger(asha).map { it.amountMinor }.sorted())
    }

    @Test
    fun anEditAfterAMoveShowsTheDifferenceAndASecondMoveCarriesIt() = runBlocking {
        repo.moveToLedger(group, "Goa", lines())!!
        val hotel = detail().expenses.single { it.description == "Hotel" }
        // The hotel was cheaper: Asha's share drops from 300 to 200.
        repo.editExpense(hotel.id, "Hotel", 800_00, me, mapOf(me to 300_00L, asha to 200_00L, bilal to 300_00L))
        assertEquals(mapOf(asha to -100_00L), pairs())
        assertEquals(-100_00L, myPosition())
        repo.moveToLedger(group, "Goa", lines())!!
        assertEquals(0L, myPosition())
        assertEquals("200 moved, then 100 back: her ledger holds 100", 100_00L, ledger(asha).sumOf { it.amountMinor })
    }

    @Test
    fun aStaleSheetWritesNothing() = runBlocking {
        val stale = lines()
        repo.addExpense(group, "Snacks", 100_00, me, listOf(me, asha))
        assertNull(repo.moveToLedger(group, "Goa", stale))
        assertTrue(ledger(asha).isEmpty())
        assertTrue(detail().expenses.none { it.isMovedToLedger })
    }

    @Test
    fun offTheLinesArePairByPair() = runBlocking {
        assertEquals(detail().let { pairsWithYou(it.expenses, it.shares, me) }, pairs())
    }

    @Test
    fun fewestPaymentsOnMovesWhatSettleUpShows() = runBlocking {
        repo.setSimplifyDebts(group, true)
        // You +500, Asha −100, Bilal −100, Chaitanya −300: the plan has all three pay you, Chaitanya
        // included, though she shared nothing with you.
        assertEquals(mapOf(chai to 300_00L, asha to 100_00L, bilal to 100_00L), pairs())
        repo.moveToLedger(group, "Goa", lines())!!
        assertEquals(0L, myPosition())
        assertEquals(300_00L, ledger(chai).single().amountMinor)
        assertEquals(100_00L, ledger(asha).single().amountMinor)
        assertTrue("nothing of yours left in the plan", pairs().isEmpty())
    }

    @Test
    fun turningTheSwitchWhileTheSheetIsOpenMakesItStale() = runBlocking {
        val sheet = lines()
        repo.setSimplifyDebts(group, true)
        assertNull(repo.moveToLedger(group, "Goa", sheet))
        assertTrue(ledger(asha).isEmpty())
    }

    @Test
    fun aFewestPaymentsPaymentThroughYouDoesNotReopenADebt() = runBlocking {
        // Audit run 6: you paid 200 with Asha, Ravi paid 200 with you, "Asha pays Ravi 100" was
        // recorded, then you paid 60 with Asha. Settle up says Asha owes you 30, and only that moves.
        val ravi = db.personDao().insertPerson(Person(name = "Ravi", currency = "INR"))
        val trip = repo.createGroup("Ooty", "INR", listOf(asha, ravi))
        repo.addExpense(trip, "Stay", 200_00, me, listOf(me, asha))
        repo.addExpense(trip, "Fuel", 200_00, ravi, listOf(ravi, me))
        repo.recordTransfer(trip, asha, ravi, 100_00)
        repo.addExpense(trip, "Tea", 60_00, me, listOf(me, asha))
        val tripLines = moveLinesFor(repo.groupDetail(trip).first().transfers, me)
        assertEquals(mapOf(asha to 30_00L), tripLines)
        repo.moveToLedger(trip, "Ooty", tripLines.map { (id, a) -> MoveLine(id, a, a) })!!
        assertEquals(30_00L, ledger(asha).single().amountMinor)
        assertTrue(ledger(ravi).isEmpty())
        val after = repo.groupDetail(trip).first()
        assertTrue(after.balances.all { it.amountMinor == 0L })
        assertTrue(after.transfers.isEmpty())
    }

    @Test
    fun anUnequalExpenseMovesByItsStoredShares() = runBlocking {
        val solo = repo.createGroup("Flat", "INR", listOf(asha))
        repo.addExpense(solo, "Deposit", 1_000_00, me, mapOf(me to 400_00L, asha to 600_00L))
        val pair = repo.groupDetail(solo).first().let { pairsWithYou(it.expenses, it.shares, me) }
        assertEquals(mapOf(asha to 600_00L), pair)
        repo.moveToLedger(solo, "Flat", pair.map { (id, a) -> MoveLine(id, a, a) })!!
        assertEquals(600_00L, ledger(asha).single().amountMinor)
    }
}
