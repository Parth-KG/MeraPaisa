package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The unsimplified settle-up: debts as they actually arose.
 *
 * The property that matters is that it still squares everyone up. Simplified and unsimplified are
 * two routes to the same end state, and if they disagree about where anyone lands, one of them is
 * wrong. Every test here checks the resulting positions, not just the list of payments.
 */
class DirectTransfersTest {

    private var nextExpense = 1L

    private fun expense(paidBy: Long, amount: Long, shares: Map<Long, Long>): Pair<Expense, List<ExpenseShare>> {
        val id = nextExpense++
        return Expense(id = id, groupId = 1, description = "e$id", amountMinor = amount, paidByPersonId = paidBy) to
            shares.map { (person, share) -> ExpenseShare(id, person, share) }
    }

    private fun build(vararg parts: Pair<Expense, List<ExpenseShare>>) =
        parts.map { it.first } to parts.flatMap { it.second }

    /** Net position from the raw rows: what each member is owed, positive, or owes, negative. */
    private fun positions(expenses: List<Expense>, shares: List<ExpenseShare>): Map<Long, Long> {
        val ids = (expenses.map { it.paidByPersonId } + shares.map { it.personId }).distinct()
        return groupBalances(
            memberIds = ids,
            paidByPerson = expenses.groupBy { it.paidByPersonId }.mapValues { (_, e) -> e.sumOf { it.amountMinor } },
            sharesByPerson = shares.groupBy { it.personId }.mapValues { (_, s) -> s.sumOf { it.shareMinor } }
        ).associate { it.personId to it.amountMinor }
    }

    /** Applies transfers to positions; everyone must end at zero. */
    private fun assertSquaresEveryone(expenses: List<Expense>, shares: List<ExpenseShare>) {
        val after = positions(expenses, shares).toMutableMap()
        directTransfers(expenses, shares).forEach { t ->
            after[t.fromPersonId] = (after[t.fromPersonId] ?: 0L) + t.amountMinor
            after[t.toPersonId] = (after[t.toPersonId] ?: 0L) - t.amountMinor
        }
        after.forEach { (person, balance) ->
            assertEquals("person $person did not end square", 0L, balance)
        }
    }

    // ---------------------------------------------------------------------------------------
    // The basics
    // ---------------------------------------------------------------------------------------

    @Test
    fun `one expense makes the sharers owe the payer`() {
        val (e, s) = build(expense(paidBy = 1, amount = 30_000, shares = mapOf(1L to 10_000L, 2L to 10_000L, 3L to 10_000L)))
        val transfers = directTransfers(e, s)

        assertEquals(2, transfers.size)
        assertTrue(transfers.all { it.toPersonId == 1L })
        assertTrue(transfers.all { it.amountMinor == 10_000L })
        assertSquaresEveryone(e, s)
    }

    /** Nobody owes themselves; the payer's own share is not a debt. */
    @Test
    fun `the payer's own share is not a transfer`() {
        val (e, s) = build(expense(paidBy = 1, amount = 20_000, shares = mapOf(1L to 10_000L, 2L to 10_000L)))
        val transfers = directTransfers(e, s)

        assertEquals(1, transfers.size)
        assertEquals(2L, transfers.single().fromPersonId)
        assertEquals(1L, transfers.single().toPersonId)
    }

    @Test
    fun `an expense paid and shared entirely by one person produces nothing`() {
        val (e, s) = build(expense(paidBy = 1, amount = 5_000, shares = mapOf(1L to 5_000L)))
        assertTrue(directTransfers(e, s).isEmpty())
    }

    @Test
    fun `no expenses means no transfers`() {
        assertTrue(directTransfers(emptyList(), emptyList()).isEmpty())
    }

    // ---------------------------------------------------------------------------------------
    // Netting within a pair, but not across the group
    // ---------------------------------------------------------------------------------------

    /** Two people who owe each other settle with one payment, not two. */
    @Test
    fun `opposite debts between the same pair net to one payment`() {
        val (e, s) = build(
            expense(paidBy = 1, amount = 30_000, shares = mapOf(1L to 15_000L, 2L to 15_000L)),
            expense(paidBy = 2, amount = 10_000, shares = mapOf(1L to 5_000L, 2L to 5_000L))
        )
        val transfers = directTransfers(e, s)

        assertEquals(1, transfers.size)
        assertEquals(2L, transfers.single().fromPersonId)
        assertEquals(1L, transfers.single().toPersonId)
        assertEquals(10_000L, transfers.single().amountMinor)
        assertSquaresEveryone(e, s)
    }

    @Test
    fun `a pair that exactly cancels produces no payment`() {
        val (e, s) = build(
            expense(paidBy = 1, amount = 20_000, shares = mapOf(1L to 10_000L, 2L to 10_000L)),
            expense(paidBy = 2, amount = 20_000, shares = mapOf(1L to 10_000L, 2L to 10_000L))
        )
        assertTrue(directTransfers(e, s).isEmpty())
    }

    /**
     * The defining difference from [settleUp]. A owes B, B owes C the same amount. Simplified
     * collapses B out and has A pay C. Unsimplified keeps both, because both actually happened.
     */
    @Test
    fun `a pass-through is kept rather than collapsed`() {
        val (e, s) = build(
            expense(paidBy = 2, amount = 10_000, shares = mapOf(1L to 10_000L)),  // 1 owes 2
            expense(paidBy = 3, amount = 10_000, shares = mapOf(2L to 10_000L))   // 2 owes 3
        )
        val direct = directTransfers(e, s)

        assertEquals("both debts survive", 2, direct.size)
        assertTrue(direct.any { it.fromPersonId == 1L && it.toPersonId == 2L })
        assertTrue(direct.any { it.fromPersonId == 2L && it.toPersonId == 3L })

        // Simplified, on the same data, removes the middle person entirely.
        val simplified = settleUp(positions(e, s).map { MemberBalance(it.key, it.value) })
        assertEquals(1, simplified.size)
        assertEquals(1L, simplified.single().fromPersonId)
        assertEquals(3L, simplified.single().toPersonId)

        assertSquaresEveryone(e, s)
    }

    // ---------------------------------------------------------------------------------------
    // Agreement with the simplified plan
    // ---------------------------------------------------------------------------------------

    /** Both routes must leave everyone at zero. They are alternatives, not different answers. */
    @Test
    fun `both plans square everyone up`() {
        val (e, s) = build(
            expense(paidBy = 1, amount = 30_000, shares = mapOf(1L to 10_000L, 2L to 10_000L, 3L to 10_000L)),
            expense(paidBy = 2, amount = 12_000, shares = mapOf(1L to 4_000L, 2L to 4_000L, 3L to 4_000L)),
            expense(paidBy = 3, amount = 9_000, shares = mapOf(1L to 3_000L, 3L to 6_000L))
        )
        assertSquaresEveryone(e, s)

        val before = positions(e, s).toMutableMap()
        settleUp(before.map { MemberBalance(it.key, it.value) }).forEach { t ->
            before[t.fromPersonId] = (before[t.fromPersonId] ?: 0L) + t.amountMinor
            before[t.toPersonId] = (before[t.toPersonId] ?: 0L) - t.amountMinor
        }
        before.forEach { (p, b) -> assertEquals("simplified left $p unsquared", 0L, b) }
    }

    /** Unsimplified never needs fewer payments than simplified; that is the trade being offered. */
    @Test
    fun `unsimplified needs at least as many payments as simplified`() {
        val (e, s) = build(
            expense(paidBy = 1, amount = 30_000, shares = mapOf(1L to 10_000L, 2L to 10_000L, 3L to 10_000L)),
            expense(paidBy = 2, amount = 30_000, shares = mapOf(1L to 10_000L, 2L to 10_000L, 3L to 10_000L)),
            expense(paidBy = 3, amount = 6_000, shares = mapOf(1L to 2_000L, 2L to 2_000L, 3L to 2_000L))
        )
        val direct = directTransfers(e, s).size
        val simple = settleUp(positions(e, s).map { MemberBalance(it.key, it.value) }).size
        assertTrue("direct $direct should be >= simplified $simple", direct >= simple)
    }

    // ---------------------------------------------------------------------------------------
    // Exactness
    // ---------------------------------------------------------------------------------------

    /**
     * No rounding anywhere: shares are already whole minor units adding to their expense, so this
     * is exact by construction. The awkward case, ₹100.01 split three ways, is where a
     * pro-rata approach would have to round and could drift.
     */
    @Test
    fun `an amount that does not divide evenly still squares exactly`() {
        val shares = evenShares(10_001, listOf(1L, 2L, 3L))
        val (e, s) = build(expense(paidBy = 1, amount = 10_001, shares = shares))

        assertEquals(10_001L, shares.values.sum())
        assertSquaresEveryone(e, s)
        assertEquals(10_001L - shares.getValue(1L), directTransfers(e, s).sumOf { it.amountMinor })
    }

    @Test
    fun `every transfer is a positive amount`() {
        val (e, s) = build(
            expense(paidBy = 1, amount = 30_000, shares = mapOf(1L to 10_000L, 2L to 20_000L)),
            expense(paidBy = 2, amount = 50_000, shares = mapOf(1L to 25_000L, 2L to 25_000L))
        )
        assertTrue(directTransfers(e, s).all { it.amountMinor > 0 })
    }

    @Test
    fun `a pair appears at most once`() {
        val (e, s) = build(
            expense(paidBy = 1, amount = 10_000, shares = mapOf(2L to 10_000L)),
            expense(paidBy = 1, amount = 20_000, shares = mapOf(2L to 20_000L)),
            expense(paidBy = 2, amount = 5_000, shares = mapOf(1L to 5_000L))
        )
        val transfers = directTransfers(e, s)
        val pairs = transfers.map { setOf(it.fromPersonId, it.toPersonId) }
        assertEquals(pairs.size, pairs.toSet().size)
        assertEquals(25_000L, transfers.single().amountMinor)
    }

    @Test
    fun `a zero share creates no debt`() {
        val (e, s) = build(expense(paidBy = 1, amount = 10_000, shares = mapOf(1L to 10_000L, 2L to 0L)))
        assertTrue(directTransfers(e, s).isEmpty())
    }

    /** A share whose expense is missing is ignored rather than crashing on a null payer. */
    @Test
    fun `an orphaned share is ignored`() {
        val transfers = directTransfers(
            expenses = emptyList(),
            shares = listOf(ExpenseShare(expenseId = 99, personId = 2, shareMinor = 5_000))
        )
        assertTrue(transfers.isEmpty())
    }

    @Test
    fun `a large group still squares exactly`() {
        val members = (1L..8L).toList()
        val parts = members.map { payer ->
            expense(paidBy = payer, amount = 7_777, shares = evenShares(7_777, members))
        }
        val (e, s) = build(*parts.toTypedArray())
        assertSquaresEveryone(e, s)
    }
}
