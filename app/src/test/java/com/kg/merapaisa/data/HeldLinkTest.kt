package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** An incoming link is capped before the app holds it for the lock (v3.7.1). */
class HeldLinkTest {

    @Test
    fun `a link of any size the app accepts is held as it is`() {
        val link = buildShareLink("A".repeat(MAX_ENCODED_CHARS))
        assertEquals(link, heldLink(link))
    }

    @Test
    fun `a link far past the cap is held short, and reads as damaged`() {
        val huge = buildShareLink("A".repeat(400_000))
        val held = heldLink(huge)
        assertTrue(held.length < 100)
        val blob = extractPayloadBlob(held)!!
        assertEquals(PayloadResult.Malformed, decodePayload(blob))
    }
}
