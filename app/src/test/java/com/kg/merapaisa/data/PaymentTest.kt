package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When Pay is offered, and what a payment that went through records. */
class PaymentTest {

    @Test
    fun `Pay is offered only to someone in rupees, with a UPI ID, whom you owe`() {
        assertTrue(canPayByUpi("INR", "bilal@ybl", -40_00))
        assertFalse("they owe you", canPayByUpi("INR", "bilal@ybl", 40_00))
        assertFalse("even", canPayByUpi("INR", "bilal@ybl", 0))
        assertFalse("no UPI ID", canPayByUpi("INR", null, -40_00))
        assertFalse("not a UPI ID", canPayByUpi("INR", "bilal", -40_00))
        assertFalse("UPI is rupees only", canPayByUpi("USD", "bilal@ybl", -40_00))
        assertTrue("an old rupee symbol still counts as rupees", canPayByUpi("₹", "bilal@ybl", -40_00))
    }

    @Test
    fun `paying what you owe, or part of it, records what was sent`() {
        assertEquals(340_00L, amountToRecord(340_00, 340_00, recordOnlyWhatYouOwed = false))
        assertEquals(100_00L, amountToRecord(340_00, 100_00, recordOnlyWhatYouOwed = true))
        assertEquals(0L, overpaidBy(340_00, 340_00))
        assertEquals(0L, overpaidBy(340_00, 100_00))
    }

    @Test
    fun `paying more records all of it, or only what you owed`() {
        assertEquals(200_00L, overpaidBy(340_00, 540_00))
        assertEquals(540_00L, amountToRecord(340_00, 540_00, recordOnlyWhatYouOwed = false))
        assertEquals(340_00L, amountToRecord(340_00, 540_00, recordOnlyWhatYouOwed = true))
    }

    @Test
    fun `Show QR is offered for someone who owes you rupees, once your ID is set`() {
        assertTrue(canShowPayQr("INR", 500_00, "me@okaxis"))
        assertFalse("you owe them", canShowPayQr("INR", -500_00, "me@okaxis"))
        assertFalse("even", canShowPayQr("INR", 0, "me@okaxis"))
        assertFalse("no UPI ID of your own", canShowPayQr("INR", 500_00, null))
        assertFalse("UPI is rupees only", canShowPayQr("USD", 500_00, "me@okaxis"))
    }
}
