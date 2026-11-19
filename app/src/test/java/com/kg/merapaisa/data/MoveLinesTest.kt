package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a move to your ledger takes (v3.7.1): Settle up's payments that involve you, so the move
 * writes what the group screen shows, with the switch either way.
 */
class MoveLinesTest {

    private val me = 1L
    private val asha = 2L
    private val ravi = 3L
    private val bilal = 4L

    private var nextId = 1L
    private val expenses = mutableListOf<Expense>()
    private val shares = mutableListOf<ExpenseShare>()

    private fun spend(payer: Long, amount: Long, vararg people: Long) {
        val id = nextId++
        expenses += Expense(id = id, groupId = 1, description = "x", amountMinor = amount, paidByPersonId = payer)
        evenShares(amount, people.toList()).forEach { (p, s) -> shares += ExpenseShare(id, p, s) }
    }

    private fun pay(from: Long, to: Long, amount: Long) {
        val id = nextId++
        expenses += Expense(id = id, groupId = 1, description = "Settlement", amountMinor = amount, paidByPersonId = from, isSettlement = true)
        shares += ExpenseShare(id, to, amount)
    }

    /** Writes the move's cancelling payments, as `GroupDao.moveToLedger` does. */
    private fun move(lines: Map<Long, Long>) = lines.forEach { (person, amount) ->
        val (row, share) = cancellingPayment(1, me, MoveLine(person, amount, amount), 0)
        val id = nextId++
        expenses += row.copy(id = id)
        shares += ExpenseShare(id, share.first, share.second)
    }

    private fun plan(members: List<Long>, simplify: Boolean) = settlePlan(members, expenses, shares, simplify)
    private fun lines(members: List<Long>, simplify: Boolean) = moveLinesFor(plan(members, simplify).transfers, me)

    @Test
    fun `run 6, fewest payments on - Asha owes you 30 and nothing else moves`() {
        val members = listOf(me, asha, ravi)
        spend(me, 200_00, me, asha)
        spend(ravi, 200_00, ravi, me)
        pay(asha, ravi, 100_00) // "Asha pays Ravi 100", from the plan
        spend(me, 60_00, me, asha)
        val lines = lines(members, simplify = true)
        assertEquals(mapOf(asha to 30_00L), lines)
        move(lines)
        // Everyone even afterwards: no debt between Asha and Ravi came back.
        assertTrue(plan(members, simplify = true).balances.all { it.amountMinor == 0L })
        assertTrue(plan(members, simplify = true).transfers.isEmpty())
    }

    @Test
    fun `run 4, fewest payments on - only what Settle up shows`() {
        val members = listOf(me, asha, bilal)
        spend(asha, 200_00, bilal)            // Asha paid for Bilal
        spend(me, 200_00, asha)               // you paid for Asha
        pay(bilal, me, 200_00)                // the plan said Bilal pays you
        assertTrue(lines(members, simplify = true).isEmpty())
        spend(me, 50_00, me, asha)            // a new expense of yours
        val lines = lines(members, simplify = true)
        assertEquals(mapOf(asha to 25_00L), lines)
        move(lines)
        assertTrue(plan(members, simplify = true).transfers.isEmpty())
    }

    @Test
    fun `on and off each give their own Settle up's lines`() {
        val members = listOf(me, asha, ravi, bilal)
        spend(me, 900_00, me, asha, bilal)
        spend(asha, 300_00, me, asha, ravi)
        spend(bilal, 400_00, bilal, ravi)
        for (simplify in listOf(true, false)) {
            val transfers = plan(members, simplify).transfers
            val expected = transfers.filter { it.fromPersonId == me || it.toPersonId == me }
                .associate { if (it.toPersonId == me) it.fromPersonId to it.amountMinor else it.toPersonId to -it.amountMinor }
            assertEquals("simplify=$simplify", expected, lines(members, simplify))
        }
        // Off is pair by pair, as v3.7.0 moved.
        assertEquals(pairsWithYou(expenses, shares, me), lines(members, simplify = false))
    }

    @Test
    fun `even overall but not pair by pair, off - there is still something to move`() {
        val members = listOf(me, asha, ravi)
        spend(me, 200_00, me, asha)
        spend(ravi, 200_00, ravi, me)
        assertEquals(0L, plan(members, false).balances.single { it.personId == me }.amountMinor)
        assertEquals(mapOf(asha to 100_00L, ravi to -100_00L), lines(members, simplify = false))
        // On, the plan routes Asha straight to Ravi and nothing involves you.
        assertTrue(lines(members, simplify = true).isEmpty())
    }

    @Test
    fun `after a move there is nothing left of yours, and a second move takes only what is new`() {
        val members = listOf(me, asha, ravi)
        spend(me, 300_00, me, asha, ravi)
        move(lines(members, simplify = true))
        assertTrue(lines(members, simplify = true).isEmpty())
        spend(ravi, 90_00, me, ravi, asha)
        assertEquals(mapOf(ravi to -30_00L), lines(members, simplify = true))
    }
}
