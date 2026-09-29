package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {

    @Test
    fun twoLettersUppercased() = assertEquals("AS", initialsOf(" asha "))

    @Test
    fun anEmojiIsNeverCutInHalf() = assertEquals("A🙂", initialsOf("A🙂 Kumar"))

    @Test
    fun aOneLetterNameStaysOneLetter() = assertEquals("B", initialsOf("b"))

    @Test
    fun anEmojiFieldKeepsOneCharacter() {
        assertEquals("A", firstCharacter("ABC"))
        assertEquals("\uD83D\uDE42", firstCharacter("\uD83D\uDE42x"))
        assertEquals("", firstCharacter("  "))
    }
}
