package com.kg.merapaisa.ui

import com.kg.merapaisa.SecurityStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning the app lock on or off asks the phone first, and the lock does not trip over its own
 * question.
 */
class AppLockTest {

    // -----------------------------------------------------------------------------------------
    // The switch
    // -----------------------------------------------------------------------------------------

    private class Prompt(private val confirm: Boolean) {
        val asked = mutableListOf<Pair<String, String>>()
        fun ask(title: String, subtitle: String, onConfirmed: () -> Unit) {
            asked += title to subtitle
            if (confirm) onConfirmed()
        }
    }

    @Test
    fun `turning the lock on writes it once the phone says it is you`() {
        val prompt = Prompt(confirm = true)
        val stored = mutableListOf<Boolean>()
        changeAppLock(true, prompt::ask) { stored += it }

        assertEquals(listOf(LOCK_ON_PROMPT), prompt.asked)
        assertEquals(listOf(true), stored)
    }

    @Test
    fun `turning the lock off writes it once the phone says it is you`() {
        val prompt = Prompt(confirm = true)
        val stored = mutableListOf<Boolean>()
        changeAppLock(false, prompt::ask) { stored += it }

        assertEquals(listOf(LOCK_OFF_PROMPT), prompt.asked)
        assertEquals(listOf(false), stored)
    }

    @Test
    fun `a cancelled prompt writes nothing, either way`() {
        listOf(true, false).forEach { enabled ->
            val prompt = Prompt(confirm = false)
            val stored = mutableListOf<Boolean>()
            changeAppLock(enabled, prompt::ask) { stored += it }

            assertEquals(1, prompt.asked.size)
            assertTrue("turning it ${if (enabled) "on" else "off"} wrote $stored", stored.isEmpty())
        }
    }

    // -----------------------------------------------------------------------------------------
    // The lock around its own prompt
    // -----------------------------------------------------------------------------------------

    private var clock = 1_000_000L
    private fun unlockedLock() = AppLockViewModel { clock }.apply { onUnlocked() }
    private val longerThanTheGrace = SecurityStore.GRACE_MILLIS + 5_000

    @Test
    fun `away longer than the grace, the ledger locks`() {
        val lock = unlockedLock()
        lock.onStopped()
        clock += longerThanTheGrace
        lock.onStarted()
        assertTrue(lock.locked)
    }

    @Test
    fun `a slow PIN that confirms leaves the ledger open and asks once`() {
        val lock = unlockedLock()
        lock.onAskStarted()
        lock.onStopped()
        clock += longerThanTheGrace
        lock.onStarted()
        assertFalse("locked under its own prompt", lock.locked)
        lock.onAskEnded(confirmed = true)
        assertFalse(lock.locked)
    }

    @Test
    fun `a slow PIN that is cancelled locks, since nothing was proven`() {
        val lock = unlockedLock()
        lock.onAskStarted()
        lock.onStopped()
        clock += longerThanTheGrace
        lock.onStarted()
        lock.onAskEnded(confirmed = false)
        assertTrue(lock.locked)
    }

    @Test
    fun `a quick cancel leaves the ledger open`() {
        val lock = unlockedLock()
        lock.onAskStarted()
        lock.onStopped()
        clock += 3_000
        lock.onStarted()
        lock.onAskEnded(confirmed = false)
        assertFalse(lock.locked)
    }

    @Test
    fun `a prompt whose answer never comes holds off one return only`() {
        val lock = unlockedLock()
        lock.onAskStarted()
        lock.onStopped()
        clock += longerThanTheGrace
        lock.onStarted()
        // No answer. The next time away locks as usual.
        lock.onStopped()
        clock += longerThanTheGrace
        lock.onStarted()
        assertTrue(lock.locked)
    }
}
