package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The arithmetic behind "Adjust the shares", on Balances and in groups. */
class SharesTest {

    @Test
    fun `an equal split adds back up, the leftover to the first`() {
        assertEquals(mapOf(1L to 334L, 2L to 333L, 3L to 333L), equalSplit(1_000, listOf(1, 2, 3)))
        assertEquals(emptyMap<Long, Long>(), equalSplit(1_000, emptyList()))
        assertEquals(1_000_01L, equalSplit(1_000_01, listOf(5, 6, 7, 8)).values.sum())
    }

    @Test
    fun `a typed share holds, and the unlocked rows take up the difference`() {
        val start = equalSplit(900_00, listOf(1, 2, 3))
        val after = redistribute(start, locked = setOf(1), changedId = 1, newValue = 500_00, total = 900_00)
        assertEquals(500_00L, after[1])
        assertEquals(200_00L, after[2])
        assertEquals(200_00L, after[3])
        assertEquals(900_00L, after.values.sum())
    }

    @Test
    fun `a locked row stays where it was`() {
        val start = mapOf(1L to 300_00L, 2L to 300_00L, 3L to 300_00L)
        val after = redistribute(start, locked = setOf(2, 3), changedId = 3, newValue = 100_00, total = 900_00)
        assertEquals(300_00L, after[2])
        assertEquals(100_00L, after[3])
        assertEquals(500_00L, after[1])
    }

    @Test
    fun `leftover minor units go to the first unlocked row, so the shares still add up`() {
        val after = redistribute(mapOf(1L to 0L, 2L to 0L, 3L to 0L), locked = setOf(1), changedId = 1, newValue = 1, total = 100)
        assertEquals(100L, after.values.sum())
        assertEquals(setOf(50L, 49L), setOf(after[2], after[3]))
    }

    @Test
    fun `no share goes below zero, and with every row locked nothing else moves`() {
        val over = redistribute(mapOf(1L to 50L, 2L to 50L), locked = setOf(1), changedId = 1, newValue = 150, total = 100)
        assertEquals(0L, over[2])
        assertTrue("the shares don't add up, which the screen says", over.values.sum() != 100L)
        val allLocked = redistribute(mapOf(1L to 50L, 2L to 50L), locked = setOf(1, 2), changedId = 1, newValue = 70, total = 100)
        assertEquals(mapOf(1L to 70L, 2L to 50L), allLocked)
    }
}
