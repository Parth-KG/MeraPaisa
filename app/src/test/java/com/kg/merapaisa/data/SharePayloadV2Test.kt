package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version 2 of the share format: a uid on every entry, and a scope on the payload.
 *
 * The compatibility direction that matters is **backwards**. Links live in chat threads for
 * months, and a friend who has not updated is an ordinary situation rather than an error, so a
 * version 1 link has to keep importing exactly as it always did. The other direction is already
 * handled: an older build reading this format sees a version it does not know and refuses,
 * which is what [PayloadResult.TooNew] is for.
 */
class SharePayloadV2Test {

    private fun payload(
        entries: List<SharedEntry>,
        scope: ShareScope = ShareScope.Full
    ) = SharePayload(
        payloadId = "pid0000000000001",
        senderName = "Parth",
        currency = "INR",
        entries = entries,
        scope = scope
    )

    private fun decoded(p: SharePayload): SharePayload =
        (decodePayload(encodePayload(p)) as PayloadResult.Ok).payload

    // -----------------------------------------------------------------------------------------
    // Round trip
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a uid survives the round trip`() {
        val out = decoded(payload(listOf(SharedEntry(1_000L, 34_000L, "Dinner", "abc123"))))
        assertEquals("abc123", out.entries.single().uid)
    }

    @Test
    fun `the scope survives the round trip`() {
        assertEquals(ShareScope.Full, decoded(payload(emptyList(), ShareScope.Full)).scope)
        assertEquals(ShareScope.Incremental, decoded(payload(emptyList(), ShareScope.Incremental)).scope)
    }

    @Test
    fun `this build writes version 2`() {
        assertEquals(2, decoded(payload(listOf(SharedEntry(1L, 1L, "x", "u1")))).formatVersion)
    }

    /** Notes are free text and may contain the framing characters; a uid must survive that too. */
    @Test
    fun `framing characters in a note do not disturb the uid beside it`() {
        val nasty = "a" + FIELD_CH + "b" + RECORD_CH + "c" + UNIT_CH + "d" + ESCAPE_CH + "e"
        val out = decoded(payload(listOf(SharedEntry(1L, 1L, nasty, "uid-1"))))

        assertEquals(nasty, out.entries.single().note)
        assertEquals("uid-1", out.entries.single().uid)
    }

    @Test
    fun `many entries keep their own uids in order`() {
        val entries = (1..50).map { SharedEntry(it.toLong(), it * 100L, "n$it", "uid$it") }
        val out = decoded(payload(entries))

        assertEquals(entries.map { it.uid }, out.entries.map { it.uid })
    }

    // -----------------------------------------------------------------------------------------
    // Reconcilability
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a version 2 payload with uids can be reconciled`() {
        assertTrue(decoded(payload(listOf(SharedEntry(1L, 1L, "x", "u1")))).canReconcile)
    }

    /**
     * One entry without a uid disables the comparison for the whole link, rather than reconciling
     * the rest and quietly appending that one. A partial reconcile would be the worst of both:
     * it looks like it checked everything, and it did not.
     */
    @Test
    fun `one missing uid makes the whole payload uncomparable`() {
        val out = decoded(
            payload(
                listOf(
                    SharedEntry(1L, 1L, "x", "u1"),
                    SharedEntry(2L, 2L, "y", "")
                )
            )
        )

        assertFalse(out.canReconcile)
    }

    @Test
    fun `two entries claiming the same uid are refused`() {
        val blob = encodePayload(
            payload(
                listOf(
                    SharedEntry(1L, 1L, "x", "same"),
                    SharedEntry(2L, 2L, "y", "same")
                )
            )
        )

        assertTrue(decodePayload(blob) is PayloadResult.Malformed)
    }

    @Test
    fun `an absurdly long uid is refused`() {
        val blob = encodePayload(payload(listOf(SharedEntry(1L, 1L, "x", "u".repeat(65)))))
        assertTrue(decodePayload(blob) is PayloadResult.Malformed)
    }

    // -----------------------------------------------------------------------------------------
    // Version 1 links still work
    // -----------------------------------------------------------------------------------------

    /**
     * Built by hand in the old five-field shape, because this build can no longer write one. If
     * this test ever needs changing to keep passing, a link somebody was sent last month has
     * stopped working.
     */
    private fun v1Blob(entries: List<Triple<Long, Long, String>>): String {
        val body = "1" + FIELD_CH + "pid0000000000001" + FIELD_CH + "Parth" + FIELD_CH + "INR" + FIELD_CH +
            entries.joinToString(RECORD_CH.toString()) { (ts, amt, note) ->
                "$ts" + UNIT_CH + "$amt" + UNIT_CH + note
            }
        return base64UrlEncodeForTest(deflateForTest(body.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `a version 1 link still imports`() {
        val result = decodePayload(v1Blob(listOf(Triple(1_000L, 34_000L, "Dinner"))))

        assertTrue("an old link must not become unreadable", result is PayloadResult.Ok)
        val p = (result as PayloadResult.Ok).payload
        assertEquals(1, p.formatVersion)
        assertEquals(34_000L, p.entries.single().amountMinor)
        assertEquals("Dinner", p.entries.single().note)
    }

    @Test
    fun `a version 1 link carries no uids and cannot be reconciled`() {
        val p = (decodePayload(v1Blob(listOf(Triple(1L, 1L, "x")))) as PayloadResult.Ok).payload

        assertEquals("", p.entries.single().uid)
        assertFalse(p.canReconcile)
    }

    /**
     * And it is read as incremental, which is the reading that can never delete anything. An old
     * link has no way to say "this is everything I have", so it must not be taken to have said it.
     */
    @Test
    fun `a version 1 link is never treated as a full snapshot`() {
        val p = (decodePayload(v1Blob(listOf(Triple(1L, 1L, "x")))) as PayloadResult.Ok).payload
        assertEquals(ShareScope.Incremental, p.scope)
    }

    @Test
    fun `a version 1 link with several entries keeps all of them`() {
        val p = (
            decodePayload(
                v1Blob(listOf(Triple(1L, 100L, "a"), Triple(2L, -200L, "b"), Triple(3L, 300L, "c")))
            ) as PayloadResult.Ok
            ).payload

        assertEquals(listOf(100L, -200L, 300L), p.entries.map { it.amountMinor })
    }

    @Test
    fun `a future version is still refused`() {
        val body = "99" + FIELD_CH + "pid" + FIELD_CH + "Parth" + FIELD_CH + "INR" + FIELD_CH + "f" + FIELD_CH
        val blob = base64UrlEncodeForTest(deflateForTest(body.toByteArray(Charsets.UTF_8)))

        assertEquals(99, (decodePayload(blob) as PayloadResult.TooNew).version)
    }

    /** A v2 body missing the scope field is damaged, not something to interpret generously. */
    @Test
    fun `a version 2 body in the version 1 shape is refused`() {
        val body = "2" + FIELD_CH + "pid" + FIELD_CH + "Parth" + FIELD_CH + "INR" + FIELD_CH +
            "1" + UNIT_CH + "100" + UNIT_CH + "note"
        val blob = base64UrlEncodeForTest(deflateForTest(body.toByteArray(Charsets.UTF_8)))

        assertTrue(decodePayload(blob) is PayloadResult.Malformed)
    }

    @Test
    fun `mirroring keeps the uid and flips only the sign`() {
        val p = payload(listOf(SharedEntry(1L, 34_000L, "Dinner", "uid-9")))
        val mirrored = p.mirrored().single()

        assertEquals(-34_000L, mirrored.amountMinor)
        assertEquals("the uid is what lets both phones name the same debt", "uid-9", mirrored.uid)
    }
}

// The codec's framing characters and helpers are private, and these tests need to build a v1 body
// that the encoder can no longer produce. Duplicated deliberately rather than widening the codec's
// API for a test's benefit.

private val FIELD_CH = 28.toChar()
private val RECORD_CH = 29.toChar()
private val UNIT_CH = 30.toChar()
private val ESCAPE_CH = 27.toChar()

private fun deflateForTest(input: ByteArray): ByteArray {
    val deflater = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION)
    deflater.setInput(input)
    deflater.finish()
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(4096)
    while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
    deflater.end()
    return out.toByteArray()
}

private const val TEST_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

private fun base64UrlEncodeForTest(bytes: ByteArray): String = buildString {
    var i = 0
    while (i + 2 < bytes.size) {
        val n = (bytes[i].toInt() and 0xFF shl 16) or
            (bytes[i + 1].toInt() and 0xFF shl 8) or (bytes[i + 2].toInt() and 0xFF)
        append(TEST_ALPHABET[n ushr 18 and 0x3F]); append(TEST_ALPHABET[n ushr 12 and 0x3F])
        append(TEST_ALPHABET[n ushr 6 and 0x3F]); append(TEST_ALPHABET[n and 0x3F])
        i += 3
    }
    when (bytes.size - i) {
        1 -> {
            val n = bytes[i].toInt() and 0xFF shl 16
            append(TEST_ALPHABET[n ushr 18 and 0x3F]); append(TEST_ALPHABET[n ushr 12 and 0x3F])
        }
        2 -> {
            val n = (bytes[i].toInt() and 0xFF shl 16) or (bytes[i + 1].toInt() and 0xFF shl 8)
            append(TEST_ALPHABET[n ushr 18 and 0x3F]); append(TEST_ALPHABET[n ushr 12 and 0x3F])
            append(TEST_ALPHABET[n ushr 6 and 0x3F])
        }
    }
}
