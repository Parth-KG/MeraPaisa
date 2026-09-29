package com.kg.merapaisa.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class EntrySpokenTest {

    @Test
    fun anEntryIsReadByWhichSideItCountsFor() {
        assertEquals("in your favour, 1,250.50 rupees", entrySpoken(1_250_50, "INR"))
        assertEquals("in their favour, 1,000 rupees", entrySpoken(-1_000_00, "INR"))
    }

    @Test
    fun aFigureBeingTypedIsNeverEven() {
        assertEquals("0 rupees", amountSpokenFigure(0, "INR"))
        assertEquals("333.33 rupees", amountSpokenFigure(333_33, "INR"))
    }
}
