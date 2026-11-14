package com.kg.merapaisa.ui.dialogs

import com.kg.merapaisa.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test

/** How a history entry is named, including the mark on a payment made by UPI. */
class EntryTitleTest {

    private fun entry(amount: Long, note: String = "", upi: Boolean = false) =
        Transaction(personId = 1, amountMinor = amount, note = note, paidByUpi = upi)

    @Test
    fun `an entry with a note is called by its note`() {
        assertEquals("Dinner", entryTitle(entry(500_00, "Dinner")))
        assertEquals("12 Nov", entryDetail(entry(500_00, "Dinner"), "12 Nov"))
    }

    @Test
    fun `an entry with no note is named for the button that made it`() {
        assertEquals("You paid for them", entryTitle(entry(500_00)))
        assertEquals("They paid for you", entryTitle(entry(-500_00)))
    }

    @Test
    fun `a UPI payment says so, with or without a note`() {
        assertEquals("You paid by UPI", entryTitle(entry(40_00, upi = true)))
        assertEquals("They paid by UPI", entryTitle(entry(-500_00, upi = true)))
        assertEquals("12 Nov", entryDetail(entry(40_00, upi = true), "12 Nov"))
        assertEquals("Cab home", entryTitle(entry(40_00, "Cab home", upi = true)))
        assertEquals("12 Nov, by UPI", entryDetail(entry(40_00, "Cab home", upi = true), "12 Nov"))
    }
}
