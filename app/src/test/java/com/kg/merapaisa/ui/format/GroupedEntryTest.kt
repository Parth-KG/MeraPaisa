package com.kg.merapaisa.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

/** A keypad's figure while typing: grouped, with the decimals exactly as far as typed. */
class GroupedEntryTest {

    @Test
    fun `nothing typed reads zero`() = assertEquals("0", groupedEntry("", "INR"))

    @Test
    fun `the whole part groups as it grows`() {
        assertEquals("1,234", groupedEntry("1234", "USD"))
        assertEquals("1,23,456", groupedEntry("123456", "INR"))
    }

    @Test
    fun `decimals stay exactly as typed, so no key press makes the figure jump`() {
        assertEquals("1,234.", groupedEntry("1234.", "INR"))
        assertEquals("1,234.0", groupedEntry("1234.0", "INR"))
        assertEquals("1,234.05", groupedEntry("1234.05", "INR"))
        assertEquals("0.5", groupedEntry("0.5", "INR"))
        assertEquals("0.", groupedEntry(".", "INR"))
    }
}
