package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settle up's rows, worked out in one place (v3.7.1). */
class SettlePlanTest {

    private val me = 1L
    private val asha = 2L
    private val ravi = 3L
    private val members = listOf(me, asha, ravi)

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

    @Test
    fun `on, it is the fewest payments`() {
        spend(me, 200_00, me, asha)
        spend(ravi, 200_00, ravi, me)
        spend(asha, 90_00, asha, ravi, me)
        val plan = settlePlan(members, expenses, shares, simplify = true)
        assertEquals(settleUp(plan.balances), plan.transfers)
    }

    @Test
    fun `off, it is the debts as they arose`() {
        spend(me, 200_00, me, asha)
        spend(ravi, 200_00, ravi, me)
        val plan = settlePlan(members, expenses, shares, simplify = false)
        assertEquals(directTransfers(expenses, shares), plan.transfers)
    }

    @Test
    fun `everyone even shows nothing, whichever way it is set`() {
        spend(me, 200_00, me, asha)
        spend(ravi, 200_00, ravi, me)
        pay(asha, ravi, 100_00)
        assertTrue(settlePlan(members, expenses, shares, simplify = true).transfers.isEmpty())
        // Pair by pair there is still a cycle (Asha owes you, you owe Ravi, Ravi owes Asha), but
        // nobody owes anything overall, so there is nothing to pay.
        assertTrue(settlePlan(members, expenses, shares, simplify = false).transfers.isEmpty())
    }

    @Test
    fun `balances list every member, at zero too`() {
        spend(me, 100_00, me, asha)
        val plan = settlePlan(members, expenses, shares, simplify = true)
        assertEquals(listOf(me to 50_00L, asha to -50_00L, ravi to 0L), plan.balances.map { it.personId to it.amountMinor })
    }
}
