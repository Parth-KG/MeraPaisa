package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your UPI ID's shape check, the links your QR codes carry, and when a QR is offered. */
class UpiTest {

    @Test
    fun `real-looking IDs pass`() {
        for (id in listOf("asha@okaxis", "9876543210@ybl", "first.last-1_x@oksbi", "  parth@paytm  ", "a@upi")) {
            assertTrue(id, isUpiId(id))
        }
    }

    @Test
    fun `typos are caught`() {
        for (id in listOf("", "asha", "asha@", "@okaxis", "asha okaxis", "asha@ok axis", "asha@@okaxis",
            "asha@1bank", "asha#x@okaxis", "a@b", "asha@okaxis.", "x".repeat(300) + "@okaxis")) {
            assertFalse(id, isUpiId(id))
        }
    }

    @Test
    fun `the pay link carries the amount in rupees with two decimals, and no note`() {
        assertEquals("upi://pay?pa=parth@okaxis&pn=Parth&am=40.00&cu=INR", upiPayLink("parth@okaxis", "Parth", 40_00))
        assertEquals("upi://pay?pa=parth@okaxis&am=1234.05&cu=INR", upiPayLink("parth@okaxis", null, 1_234_05))
        assertEquals("upi://pay?pa=parth@okaxis&am=0.01&cu=INR", upiPayLink(" parth@okaxis ", " ", 1))
    }

    @Test
    fun `a name cannot add parameters of its own`() {
        val link = upiPayLink("parth@okaxis", "A&am=1#x&pa=thief@ybl", 40_00)
        assertEquals("upi://pay?pa=parth@okaxis&pn=A%26am%3D1%23x%26pa%3Dthief%40ybl&am=40.00&cu=INR", link)
    }

    @Test
    fun `a pay link refuses a bad ID or a non-positive amount`() {
        assertTrue(runCatching { upiPayLink("nope", null, 100) }.isFailure)
        assertTrue(runCatching { upiPayLink("parth@okaxis", null, 0) }.isFailure)
        assertTrue(runCatching { upiPayLink("parth@okaxis", null, -100) }.isFailure)
    }

    @Test
    fun `a QR is offered for someone who owes you rupees, once your ID is set`() {
        assertTrue(canShowPayQr("INR", 500_00, "me@okaxis"))
        assertTrue("an old rupee symbol still counts as rupees", canShowPayQr("₹", 500_00, "me@okaxis"))
        assertFalse("you owe them", canShowPayQr("INR", -500_00, "me@okaxis"))
        assertFalse("even", canShowPayQr("INR", 0, "me@okaxis"))
        assertFalse("no UPI ID of your own", canShowPayQr("INR", 500_00, null))
        assertFalse("not a UPI ID", canShowPayQr("INR", 500_00, "me"))
        assertFalse("UPI is rupees only", canShowPayQr("USD", 500_00, "me@okaxis"))
    }

    @Test
    fun `Show QR is offered for rupees owed to you, with or without your UPI ID`() {
        assertTrue(canOfferPayQr("INR", 500_00))
        assertTrue(canOfferPayQr("₹", 1))
        assertFalse(canOfferPayQr("USD", 500_00))
        assertFalse(canOfferPayQr("INR", 0))
        assertFalse(canOfferPayQr("INR", -500_00))
        // The QR itself still needs the ID.
        assertFalse(canShowPayQr("INR", 500_00, null))
        assertTrue(canShowPayQr("INR", 500_00, "parth@okaxis"))
    }
}
