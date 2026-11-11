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
}
