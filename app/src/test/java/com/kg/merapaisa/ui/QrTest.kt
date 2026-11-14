package com.kg.merapaisa.ui

import com.kg.merapaisa.data.upiPayLink
import io.nayuki.qrcodegen.QrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The vendored encoder works as Nayuki's own examples say, and a UPI link fits a small code. */
class QrTest {

    @Test
    fun `the encoder gives the code its own examples give`() {
        // Nayuki's demo: "Hello, world!" at low error correction is a version 1 code, 21 modules.
        val qr = QrCode.encodeText("Hello, world!", QrCode.Ecc.LOW)
        assertEquals(1, qr.version)
        assertEquals(21, qr.size)
        // The three finder patterns sit in their corners.
        assertTrue(qr.getModule(0, 0) && qr.getModule(20, 0) && qr.getModule(0, 20))
    }

    @Test
    fun `a UPI pay link makes a code small enough to scan from a screen`() {
        val link = upiPayLink("first.last-1_x@oksbi", "Chaitanya Venkataraman", 12_34_567_50)
        val qr = qrFor(link)
        assertTrue("version ${qr.version} is too dense to scan comfortably", qr.version <= 8)
    }

    @Test
    fun `the same text always gives the same code`() {
        val a = qrFor("upi://pay?pa=a@okaxis&am=1.00&cu=INR")
        val b = qrFor("upi://pay?pa=a@okaxis&am=1.00&cu=INR")
        for (y in 0 until a.size) for (x in 0 until a.size) assertEquals(a.getModule(x, y), b.getModule(x, y))
    }
}
