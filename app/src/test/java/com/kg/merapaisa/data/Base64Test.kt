package com.kg.merapaisa.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/**
 * The codec update links have always used, now shared with the photos in a backup. Update links'
 * own tests cover it through the payload; these add the sizes a photo brings.
 */
class Base64Test {

    @Test
    fun `every short length survives a round trip`() {
        val random = Random(7)
        for (length in 0..64) {
            val bytes = random.nextBytes(length)
            assertArrayEquals("length $length", bytes, base64UrlDecode(base64UrlEncode(bytes)))
        }
    }

    @Test
    fun `a photo-sized file survives a round trip`() {
        // A 256px JPEG at quality 90 is about 15 to 40 KB; this is larger than any of them.
        val bytes = Random(11).nextBytes(60_000)
        assertArrayEquals(bytes, base64UrlDecode(base64UrlEncode(bytes)))
    }

    @Test
    fun `every byte value survives`() {
        val bytes = ByteArray(256) { it.toByte() }
        assertArrayEquals(bytes, base64UrlDecode(base64UrlEncode(bytes)))
    }

    @Test
    fun `a character outside the alphabet is refused`() {
        assertNull(base64UrlDecode("abc*"))
        assertNull(base64UrlDecode("ab cd"))
    }

    @Test
    fun `a length that cannot be whole bytes is refused`() {
        assertNull(base64UrlDecode("abcde"))
    }
}
