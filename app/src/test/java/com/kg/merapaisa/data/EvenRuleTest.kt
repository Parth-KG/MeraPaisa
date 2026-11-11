package com.kg.merapaisa.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who sits on Even and who on Active. The balance decides alone: the stored settled flag is still
 * written for backups and older versions, but must never move anyone between the tabs.
 */
class EvenRuleTest {

    private fun person(balanceMinor: Long, settledFlag: Boolean = false) = PersonWithBalance(
        person = Person(id = 1, name = "Asha", currency = "INR", isSettled = settledFlag),
        balanceMinor = balanceMinor
    )

    @Test
    fun `someone just added, with nothing recorded, is even`() {
        assertTrue(person(0).isEven)
    }

    @Test
    fun `a balance either way is active`() {
        assertFalse(person(500_00).isEven)
        assertFalse(person(-40_00).isEven)
        assertFalse(person(1).isEven)
        assertFalse(person(-1).isEven)
    }

    @Test
    fun `the settled flag does not keep someone with a balance off Active`() {
        assertFalse(person(1_200_00, settledFlag = true).isEven)
    }

    @Test
    fun `the settled flag is not needed to be even`() {
        assertTrue(person(0, settledFlag = false).isEven)
        assertTrue(person(0, settledFlag = true).isEven)
    }

    @Test
    fun `the extremes of a balance are active`() {
        assertFalse(person(Long.MAX_VALUE).isEven)
        assertFalse(person(Long.MIN_VALUE).isEven)
    }

    @Test
    fun `an amount for someone even takes them off Even`() {
        assertFalse(person(0).isEvenAfter(1_200_00))
        assertFalse(person(0).isEvenAfter(-40_00))
    }

    @Test
    fun `a payment that squares a balance leaves them even`() {
        assertTrue(person(1_200_00).isEvenAfter(-1_200_00))
        assertTrue(person(-40_00).isEvenAfter(40_00))
    }

    @Test
    fun `a payment that does not square a balance leaves them active`() {
        assertFalse(person(1_200_00).isEvenAfter(-1_000_00))
        assertFalse(person(1_200_00).isEvenAfter(1))
    }

    @Test
    fun `a sum past what a Long holds is not even`() {
        assertFalse(person(Long.MAX_VALUE).isEvenAfter(1))
        assertFalse(person(Long.MIN_VALUE).isEvenAfter(-1))
    }
}
