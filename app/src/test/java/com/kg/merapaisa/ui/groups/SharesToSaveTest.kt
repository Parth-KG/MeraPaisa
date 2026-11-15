package com.kg.merapaisa.ui.groups

import org.junit.Assert.assertEquals
import org.junit.Test

/** An edit keeps the stored shares unless the amount or the people sharing change (v3.7.0). */
class SharesToSaveTest {

    private val draft = ExpenseDraft("Dinner", 1_000_00, 1, mapOf(1L to 700_00L, 2L to 300_00L))

    @Test
    fun `an untouched edit keeps the stored shares, not equal ones`() {
        assertEquals(draft.shares, sharesToSave(1_000_00, setOf(1, 2), draft))
    }

    @Test
    fun `a new amount or new people split equally again`() {
        assertEquals(mapOf(1L to 600_00L, 2L to 600_00L), sharesToSave(1_200_00, setOf(1, 2), draft))
        assertEquals(mapOf(1L to 333_34L, 2L to 333_33L, 3L to 333_33L), sharesToSave(1_000_00, setOf(1, 2, 3), draft))
    }

    @Test
    fun `adding an expense splits equally, the remainder to the earliest`() {
        assertEquals(mapOf(1L to 50L, 2L to 50L), sharesToSave(100, setOf(2, 1), null))
        assertEquals(1_000_01L, sharesToSave(1_000_01, setOf(3, 1, 2), null).values.sum())
    }
}
