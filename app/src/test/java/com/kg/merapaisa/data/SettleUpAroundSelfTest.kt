package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The group plan keeps what is between you and each member direct, so each person's balance on the
 * main screen always matches what the plan says passes between the two of you.
 */
class SettleUpAroundSelfTest {

    private val you = 1L
    private val asha = 2L
    private val xyz = 3L

    private val expenses = listOf(
        Expense(id = 1, groupId = 1, description = "Hotel", amountMinor = 1_000_00, paidByPersonId = you),
        Expense(id = 2, groupId = 1, description = "Cab", amountMinor = 300_00, paidByPersonId = asha)
    )
    private val shares = listOf(
        ExpenseShare(1, you, 333_34), ExpenseShare(1, asha, 333_33), ExpenseShare(1, xyz, 333_33),
        ExpenseShare(2, asha, 150_00), ExpenseShare(2, xyz, 150_00)
    )
    private fun balances(e: List<Expense>, s: List<ExpenseShare>) = groupBalances(
        listOf(you, asha, xyz),
        e.groupBy { it.paidByPersonId }.mapValues { (_, l) -> l.sumOf { it.amountMinor } },
        s.groupBy { it.personId }.mapValues { (_, l) -> l.sumOf { it.shareMinor } }
    )

    @Test
    fun yourOwnDebtsStayDirectAndTheRestIsBetweenTheOthers() {
        val plan = settleUpAroundSelf(you, balances(expenses, shares), expenses, shares).toSet()
        assertEquals(
            setOf(
                Transfer(xyz, you, 333_33),
                Transfer(asha, you, 333_33),
                Transfer(xyz, asha, 150_00)
            ),
            plan
        )
    }

    @Test
    fun theOldRoutedPlanStillSettlesToNothing() {
        // What the fewest-payments plan used to record: xyz pays you for Asha's 150 as well.
        val paid = expenses + listOf(
            Expense(id = 3, groupId = 1, description = "Settlement", amountMinor = 483_33, paidByPersonId = xyz, isSettlement = true),
            Expense(id = 4, groupId = 1, description = "Settlement", amountMinor = 183_33, paidByPersonId = asha, isSettlement = true)
        )
        val paidShares = shares + listOf(ExpenseShare(3, you, 483_33), ExpenseShare(4, you, 183_33))
        assertTrue(settleUpAroundSelf(you, balances(paid, paidShares), paid, paidShares).isEmpty())
    }
}
