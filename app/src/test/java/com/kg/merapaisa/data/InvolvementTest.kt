package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Who can leave a group (v3.7.0): only someone nothing in it involves. */
class InvolvementTest {

    private val hotel = Expense(id = 1, groupId = 1, description = "Hotel", amountMinor = 900_00, paidByPersonId = 1)
    private val payment = Expense(id = 2, groupId = 1, description = "Settlement", amountMinor = 100_00, paidByPersonId = 4, isSettlement = true)
    private val shares = listOf(
        ExpenseShare(1, 1, 300_00), ExpenseShare(1, 2, 300_00), ExpenseShare(1, 4, 300_00),
        ExpenseShare(2, 1, 100_00)
    )

    @Test
    fun `paying comes first, then holding a share, else nothing`() {
        assertEquals(Involvement.Paid, involvementOf(1, listOf(hotel, payment), shares))
        assertEquals(Involvement.Share, involvementOf(2, listOf(hotel, payment), shares))
        assertEquals(Involvement.None, involvementOf(3, listOf(hotel, payment), shares))
    }

    @Test
    fun `payments count as much as expenses`() {
        assertEquals(Involvement.Paid, involvementOf(4, listOf(payment), emptyList()))
        assertEquals(Involvement.Share, involvementOf(1, listOf(payment), listOf(ExpenseShare(2, 1, 100_00))))
    }
}
