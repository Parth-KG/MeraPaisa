package com.kg.merapaisa.ui.dialogs

import com.kg.merapaisa.ui.AVATAR_HUES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The avatar colour a new person gets when nobody picks one. It used to be the first swatch for
 * everyone, so a ledger filled with identical indigo circles. Found on a phone.
 */
class HueForNameTest {

    @Test
    fun `the same name always lands on the same hue`() {
        assertEquals(hueForName("Asha"), hueForName("Asha"))
        assertEquals(hueForName("Asha"), hueForName("  asha "))
    }

    @Test
    fun `every hue is one of the eight`() {
        listOf("", "Asha", "Bilal", "Chaitanya Venkataraman", "Diego", "Jo").forEach {
            assertTrue(hueForName(it) in AVATAR_HUES)
        }
    }

    @Test
    fun `a handful of names do not all share one hue`() {
        val hues = listOf("Asha", "Bilal", "Chaitanya", "Diego", "Emi", "Farid", "Gita", "Jo").map(::hueForName)
        assertTrue("got ${hues.toSet().size} distinct hues", hues.toSet().size >= 4)
    }
}
