package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TypedUpToTest {
    @Test
    fun `short text is kept as it is`() = assertEquals("Lunch", typedUpTo("Lunch", MAX_NOTE_CHARS))

    @Test
    fun `long text stops at the cap`() = assertEquals(MAX_NOTE_CHARS, typedUpTo("x".repeat(600), MAX_NOTE_CHARS).length)

    @Test
    fun `a character that takes two is never cut in half`() {
        val text = "a".repeat(MAX_NAME_CHARS - 1) + "😀" // the 100th and 101st are one character
        assertEquals("a".repeat(MAX_NAME_CHARS - 1), typedUpTo(text, MAX_NAME_CHARS))
    }

    @Test
    fun `a full Move a debt note still fits a link after its prefix`() {
        val note = "Moved from " + "N".repeat(MAX_NAME_CHARS) + ", ₹1,23,45,678.90 at ₹83.1234 = $1,485,234.56" + ": " + "x".repeat(MAX_MOVE_NOTE_CHARS)
        assert(note.length <= MAX_NOTE_CHARS) { "${note.length}" }
    }
}
