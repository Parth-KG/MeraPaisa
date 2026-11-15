package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your position pair by pair (v3.7.0), the amounts a move to your ledger takes. */
class PairsWithYouTest {

    private val me = 1L
    private val asha = 2L
    private val bilal = 3L
    private val chai = 4L

    private var nextId = 1L
    private val expenses = mutableListOf<Expense>()
    private val shares = mutableListOf<ExpenseShare>()

    private fun spend(payer: Long, amount: Long, vararg people: Long, settlement: Boolean = false) {
        val id = nextId++
        expenses += Expense(id = id, groupId = 1, description = "x", amountMinor = amount, paidByPersonId = payer, isSettlement = settlement)
        evenShares(amount, people.toList()).forEach { (p, s) -> shares += ExpenseShare(id, p, s) }
    }

    private fun balances() = groupBalances(
        listOf(me, asha, bilal, chai),
        expenses.groupBy { it.paidByPersonId }.mapValues { (_, e) -> e.sumOf { it.amountMinor } },
        shares.groupBy { it.personId }.mapValues { (_, s) -> s.sumOf { it.shareMinor } }
    )

    private fun trip() {
        spend(me, 900_00, me, asha, bilal)       // they each owe me 300
        spend(asha, 600_00, me, asha, chai)      // I owe Asha 200
        spend(bilal, 400_00, bilal, chai)        // nothing to do with me
        spend(asha, 50_00, asha, me, settlement = false)
    }

    @Test
    fun `each pair nets what you paid for them against what they paid for you`() {
        trip()
        val pairs = pairsWithYou(expenses, shares, me)
        assertEquals(mapOf(asha to 300_00L - 200_00L - 25_00L, bilal to 300_00L), pairs)
        assertTrue("Chai shared nothing with me directly, so no pair", chai !in pairs)
    }

    @Test
    fun `the pairs add up to your own position`() {
        trip()
        val mine = balances().single { it.personId == me }.amountMinor
        assertEquals(mine, pairsWithYou(expenses, shares, me).values.sum())
    }

    @Test
    fun `the pairs are the as-it-happened plan kept to you, whatever the switch says`() {
        trip()
        val fromPlan = directTransfers(expenses, shares).mapNotNull { t ->
            when (me) {
                t.toPersonId -> t.fromPersonId to t.amountMinor
                t.fromPersonId -> t.toPersonId to -t.amountMinor
                else -> null
            }
        }.toMap()
        assertEquals(fromPlan, pairsWithYou(expenses, shares, me))
    }

    @Test
    fun `payments between you two count, and an even pair is left out`() {
        spend(me, 200_00, me, asha)              // Asha owes me 100
        spend(asha, 100_00, me, settlement = true) // she paid me back
        assertEquals(emptyMap<Long, Long>(), pairsWithYou(expenses, shares, me))
    }

    @Test
    fun `cancelling every pair takes you to zero and moves the others by their pair alone`() {
        trip()
        val before = balances().associate { it.personId to it.amountMinor }
        val pairs = pairsWithYou(expenses, shares, me)
        // What a move writes: one payment per pair that cancels it.
        pairs.forEach { (other, owed) ->
            if (owed > 0) spend(other, owed, me, settlement = true) else spend(me, -owed, other, settlement = true)
        }
        val after = balances().associate { it.personId to it.amountMinor }
        assertEquals(0L, after[me])
        assertEquals(before[asha]!! + pairs[asha]!!, after[asha])
        assertEquals(before[bilal]!! + pairs[bilal]!!, after[bilal])
        assertEquals("Chai had no pair with me, so nothing of theirs moved", before[chai], after[chai])
        assertEquals(emptyMap<Long, Long>(), pairsWithYou(expenses, shares, me))
    }

    @Test
    fun `a moved row is told apart only by its description on a payment`() {
        assertTrue(Expense(groupId = 1, description = MOVED_TO_LEDGER, amountMinor = 1, paidByPersonId = 2, isSettlement = true).isMovedToLedger)
        assertTrue(!Expense(groupId = 1, description = MOVED_TO_LEDGER, amountMinor = 1, paidByPersonId = 2).isMovedToLedger)
        assertTrue(!Expense(groupId = 1, description = "Settlement", amountMinor = 1, paidByPersonId = 2, isSettlement = true).isMovedToLedger)
    }
}
