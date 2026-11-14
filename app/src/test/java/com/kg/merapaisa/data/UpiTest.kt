package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** UPI IDs and links: the shape check, a pasted link read as hostile input, and the pay link. */
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
    fun `an ID pasted on its own is kept, trimmed`() {
        assertEquals(PastedUpi("asha@okaxis", null), readPastedUpi("  asha@okaxis \n"))
    }

    @Test
    fun `a pasted link gives only its ID and name`() {
        val link = "upi://pay?pa=asha@okaxis&pn=Asha%20Rao&am=99999.00&cu=INR&tn=send%20all"
        assertEquals(PastedUpi("asha@okaxis", "Asha Rao"), readPastedUpi(link))
    }

    @Test
    fun `parameter names are read in any case and order`() {
        assertEquals(PastedUpi("asha@okaxis", "Asha"), readPastedUpi("UPI://pay?am=1&PN=Asha&PA=asha%40okaxis"))
    }

    @Test
    fun `a second pa cannot override the first`() {
        assertEquals("asha@okaxis", readPastedUpi("upi://pay?pa=asha@okaxis&pa=thief@ybl")?.id)
    }

    @Test
    fun `a link with no valid ID is refused`() {
        assertNull(readPastedUpi("upi://pay?pn=Asha&am=10"))
        assertNull(readPastedUpi("upi://pay?pa=not-an-id&pn=Asha"))
        assertNull(readPastedUpi("upi://pay"))
        assertNull(readPastedUpi("https://example.com/?pa=asha@okaxis"))
        assertNull(readPastedUpi("upi://pay?pa=asha%ZZokaxis"))
        assertNull(readPastedUpi("upi://pay?pa=asha%4"))
    }

    @Test
    fun `a huge paste is not read at all`() {
        assertNull(readPastedUpi("upi://pay?pa=asha@okaxis&pn=" + "A".repeat(3_000)))
    }

    @Test
    fun `a long name is capped and control characters flattened`() {
        val pasted = readPastedUpi("upi://pay?pa=asha@okaxis&pn=" + "B".repeat(100) + "%0Aline")!!
        assertEquals(UPI_NAME_MAX, pasted.name!!.length)
        val flattened = readPastedUpi("upi://pay?pa=asha@okaxis&pn=Asha%0ARao")!!
        assertEquals("Asha Rao", flattened.name)
    }

    @Test
    fun `a unicode name survives`() {
        assertEquals("आशा", readPastedUpi("upi://pay?pa=asha@okaxis&pn=%E0%A4%86%E0%A4%B6%E0%A4%BE")?.name)
    }

    @Test
    fun `the pay link carries the amount in rupees with two decimals`() {
        assertEquals("upi://pay?pa=bilal@ybl&pn=Bilal&am=40.00&cu=INR", upiPayLink("bilal@ybl", "Bilal", 40_00))
        assertEquals("upi://pay?pa=bilal@ybl&am=1234.05&cu=INR", upiPayLink("bilal@ybl", null, 1_234_05))
        assertEquals("upi://pay?pa=bilal@ybl&am=0.01&cu=INR", upiPayLink("bilal@ybl", " ", 1))
    }

    @Test
    fun `a note is added only when one was typed`() {
        assertEquals("upi://pay?pa=bilal@ybl&am=40.00&cu=INR&tn=Cab%20home", upiPayLink("bilal@ybl", null, 40_00, "Cab home"))
        assertEquals("upi://pay?pa=bilal@ybl&am=40.00&cu=INR", upiPayLink("bilal@ybl", null, 40_00, "  "))
    }

    @Test
    fun `a name or note cannot add parameters of its own`() {
        val link = upiPayLink("bilal@ybl", "A&am=1#x", 40_00, "x&pa=thief@ybl")
        assertEquals("upi://pay?pa=bilal@ybl&pn=A%26am%3D1%23x&am=40.00&cu=INR&tn=x%26pa%3Dthief%40ybl", link)
        // And reading the link back finds the real ID.
        assertEquals("bilal@ybl", readPastedUpi(link)?.id)
        assertEquals("A&am=1#x", readPastedUpi(link)?.name)
    }

    @Test
    fun `a pay link refuses a bad ID or a non-positive amount`() {
        assertTrue(runCatching { upiPayLink("nope", null, 100) }.isFailure)
        assertTrue(runCatching { upiPayLink("bilal@ybl", null, 0) }.isFailure)
        assertTrue(runCatching { upiPayLink("bilal@ybl", null, -100) }.isFailure)
    }
}
