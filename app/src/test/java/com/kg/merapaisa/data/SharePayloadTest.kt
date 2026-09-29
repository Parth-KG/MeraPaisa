package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The share payload is a money format that arrives from a chat app, so these tests care about two
 * things above all: that a round trip is exact, and that a hostile or damaged link is refused
 * rather than half-applied.
 *
 * All of it runs on the JVM with no device, which is why the codec avoids `org.json` and
 * `android.util.Base64`; see the note at the top of SharePayload.kt.
 */
class SharePayloadTest {

    private fun payload(
        id: String = "abc123def456",
        name: String = "Parth",
        currency: String = "INR",
        entries: List<SharedEntry> = listOf(SharedEntry(1_700_000_000_000L, 34_000L, "Dinner"))
    ) = SharePayload(id, name, currency, entries)

    // ---------------------------------------------------------------------------------------
    // Round trip
    // ---------------------------------------------------------------------------------------

    @Test
    fun `round trips a single entry exactly`() {
        val original = payload()
        assertEquals(PayloadResult.Ok(original), decodePayload(encodePayload(original)))
    }

    @Test
    fun `round trips many entries in order`() {
        val entries = (1..50).map {
            SharedEntry(1_700_000_000_000L + it * 1000L, it * 137L, "Entry $it")
        }
        val decoded = decodePayload(encodePayload(payload(entries = entries))) as PayloadResult.Ok
        assertEquals(entries, decoded.payload.entries)
    }

    @Test
    fun `round trips negative amounts`() {
        val decoded = decodePayload(
            encodePayload(payload(entries = listOf(SharedEntry(1L, -34_000L, "You paid me back"))))
        ) as PayloadResult.Ok
        assertEquals(-34_000L, decoded.payload.entries.single().amountMinor)
    }

    @Test
    fun `round trips an empty entry list`() {
        val decoded = decodePayload(encodePayload(payload(entries = emptyList()))) as PayloadResult.Ok
        assertTrue(decoded.payload.entries.isEmpty())
        assertEquals(0L, decoded.payload.netMinor)
    }

    /**
     * The framing uses control characters, so a note containing one must survive escaping. This is
     * the case that silently corrupts a ledger when escaping is wrong, rather than failing loudly.
     */
    @Test
    fun `round trips notes containing every framing character`() {
        val nasty = "field$FIELD record$RECORD unit$UNIT escape$ESCAPE done"
        val decoded = decodePayload(
            encodePayload(payload(entries = listOf(SharedEntry(1L, 1L, nasty))))
        ) as PayloadResult.Ok
        assertEquals(nasty, decoded.payload.entries.single().note)
    }

    @Test
    fun `round trips a note of nothing but escape characters`() {
        val nasty = "$ESCAPE$ESCAPE$ESCAPE$ESCAPE"
        val decoded = decodePayload(
            encodePayload(payload(entries = listOf(SharedEntry(1L, 1L, nasty))))
        ) as PayloadResult.Ok
        assertEquals(nasty, decoded.payload.entries.single().note)
    }

    @Test
    fun `round trips punctuation that would break a naive delimiter`() {
        val nasty = "commas, semis; pipes| \"quotes\" and a tab\there"
        val decoded = decodePayload(
            encodePayload(payload(entries = listOf(SharedEntry(1L, 1L, nasty))))
        ) as PayloadResult.Ok
        assertEquals(nasty, decoded.payload.entries.single().note)
    }

    @Test
    fun `round trips unicode in names and notes`() {
        val original = payload(name = "पार्थ", entries = listOf(SharedEntry(1L, 500L, "चाय ☕ 🎉")))
        val decoded = decodePayload(encodePayload(original)) as PayloadResult.Ok
        assertEquals("पार्थ", decoded.payload.senderName)
        assertEquals("चाय ☕ 🎉", decoded.payload.entries.single().note)
    }

    @Test
    fun `round trips an empty note`() {
        val decoded = decodePayload(
            encodePayload(payload(entries = listOf(SharedEntry(7L, 9L, ""))))
        ) as PayloadResult.Ok
        assertEquals("", decoded.payload.entries.single().note)
    }

    @Test
    fun `encoded blob is url safe and unpadded`() {
        val blob = encodePayload(
            payload(entries = (1..30).map { SharedEntry(it.toLong(), it * 11L, "n$it") })
        )
        assertTrue("found a non-Base64URL character in $blob", blob.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_'
        })
    }

    /** Repeated notes are the common case, and a shorter link is one a chat app cannot mangle. */
    @Test
    fun `compression keeps a long payload well under a link length limit`() {
        val entries = (1..200).map { SharedEntry(1_700_000_000_000L + it, 12_345L, "Dinner") }
        val blob = encodePayload(payload(entries = entries))
        assertTrue("200 entries encoded to ${blob.length} chars", blob.length < 2000)
    }

    // ---------------------------------------------------------------------------------------
    // Mirroring: the heart of the feature
    // ---------------------------------------------------------------------------------------

    @Test
    fun `mirroring flips every sign`() {
        val p = payload(entries = listOf(
            SharedEntry(1L, 34_000L, "Dinner"),
            SharedEntry(2L, -5_000L, "Part payment"),
            SharedEntry(3L, 0L, "Note only")
        ))
        assertEquals(listOf(-34_000L, 5_000L, 0L), p.mirrored().map { it.amountMinor })
    }

    @Test
    fun `mirroring preserves timestamps and notes`() {
        val m = payload(entries = listOf(SharedEntry(1_700_000_000_000L, 34_000L, "Dinner")))
            .mirrored().single()
        assertEquals(1_700_000_000_000L, m.timestamp)
        assertEquals("Dinner", m.note)
    }

    @Test
    fun `net of a mirrored payload is the exact negation`() {
        val p = payload(entries = (1..20).map { SharedEntry(it.toLong(), it * 999L, "x") })
        assertEquals(-p.netMinor, p.mirrored().sumOf { it.amountMinor })
    }

    /** Mirroring twice is the identity, which is what makes two ledgers agree rather than drift. */
    @Test
    fun `mirroring twice returns the original amounts`() {
        val p = payload(entries = (1..10).map { SharedEntry(it.toLong(), it * -777L, "x") })
        val back = p.copy(entries = p.mirrored()).mirrored()
        assertEquals(p.entries, back)
    }

    // ---------------------------------------------------------------------------------------
    // Refusals
    // ---------------------------------------------------------------------------------------

    @Test
    fun `refuses junk`() {
        assertEquals(PayloadResult.Malformed, decodePayload("hello world"))
        assertEquals(PayloadResult.Malformed, decodePayload(""))
        assertEquals(PayloadResult.Malformed, decodePayload("   "))
        assertEquals(PayloadResult.Malformed, decodePayload("!!!not base64!!!"))
    }

    @Test
    fun `refuses valid base64 that is not deflate`() {
        assertEquals(PayloadResult.Malformed, decodePayload("AAAAAAAAAAAAAAAA"))
    }

    /** A chat app that clips a long link must produce a refusal, not a partial ledger. */
    @Test
    fun `refuses a truncated blob`() {
        val blob = encodePayload(
            payload(entries = (1..40).map { SharedEntry(it.toLong(), it * 100L, "Entry $it") })
        )
        for (fraction in listOf(2, 3, 4, 8)) {
            val clipped = blob.take(blob.length / fraction)
            assertTrue(
                "a blob clipped to 1/$fraction decoded instead of being refused",
                decodePayload(clipped) !is PayloadResult.Ok
            )
        }
    }

    @Test
    fun `refuses a flipped character`() {
        val blob = encodePayload(payload())
        val i = blob.length / 2
        val flipped = blob.substring(0, i) + (if (blob[i] == 'A') 'B' else 'A') + blob.substring(i + 1)
        assertTrue(decodePayload(flipped) !is PayloadResult.Ok)
    }

    @Test
    fun `reports a newer format version rather than guessing`() {
        val result = decodePayload(blobOf(body("99", "id", "Parth", "INR", entry(1, 100, "note"))))
        assertEquals(PayloadResult.TooNew(99), result)
    }

    @Test
    fun `refuses version zero and negative versions`() {
        assertEquals(PayloadResult.Malformed, decodePayload(blobOf(body("0", "id", "P", "INR", ""))))
        assertEquals(PayloadResult.Malformed, decodePayload(blobOf(body("-1", "id", "P", "INR", ""))))
        assertEquals(PayloadResult.Malformed, decodePayload(blobOf(body("x", "id", "P", "INR", ""))))
    }

    @Test
    fun `refuses missing fields`() {
        assertEquals(PayloadResult.Malformed, decodePayload(blobOf(body("1", "id", "Parth"))))
        assertEquals(PayloadResult.Malformed, decodePayload(blobOf(body("1"))))
        assertEquals(PayloadResult.Malformed, decodePayload(blobOf(body("1", "id", "P", "INR"))))
    }

    @Test
    fun `refuses an empty sender name or payload id`() {
        assertEquals(PayloadResult.Malformed, decodePayload(blobOf(body("1", "", "Parth", "INR", ""))))
        assertEquals(PayloadResult.Malformed, decodePayload(blobOf(body("1", "id", "", "INR", ""))))
    }

    @Test
    fun `refuses a currency that is not three uppercase letters`() {
        for (bad in listOf("in", "INRR", "IN", "", "1NR", "inr", "I N")) {
            assertEquals(
                "currency '$bad' should have been refused",
                PayloadResult.Malformed,
                decodePayload(blobOf(body("1", "id", "Parth", bad, "")))
            )
        }
    }

    @Test
    fun `refuses a non numeric amount or timestamp`() {
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "P", "INR", "x${UNIT}100${UNIT}n")))
        )
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "P", "INR", "1${UNIT}x${UNIT}n")))
        )
    }

    @Test
    fun `refuses a negative timestamp`() {
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "P", "INR", entry(-5, 100, "note"))))
        )
    }

    @Test
    fun `refuses an entry with too few parts`() {
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "P", "INR", "1${UNIT}100")))
        )
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "P", "INR", "1")))
        )
    }

    /**
     * The bound that matters most. `Long.MIN_VALUE` is the one amount `mirrored()` cannot flip
     * (negating it returns itself), so a link carrying it would import a debt pointing the wrong
     * way round. It has to be refused at the door.
     */
    @Test
    fun `refuses an amount that cannot be mirrored`() {
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "P", "INR", "1$UNIT${Long.MIN_VALUE}${UNIT}note")))
        )
    }

    @Test
    fun `refuses an amount larger than the app can represent`() {
        val tooBig = 100_000_000_000L
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "P", "INR", entry(1, tooBig, "note"))))
        )
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "P", "INR", entry(1, -tooBig, "note"))))
        )
    }

    @Test
    fun `accepts the largest amount the app can represent`() {
        val max = 99_999_999_999L
        val decoded = decodePayload(blobOf(body("1", "id", "P", "INR", entry(1, max, "note"))))
        assertTrue("expected Ok, got $decoded", decoded is PayloadResult.Ok)
        assertEquals(max, (decoded as PayloadResult.Ok).payload.entries.single().amountMinor)
    }

    @Test
    fun `refuses a dangling escape`() {
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "Parth$ESCAPE", "INR", "")))
        )
    }

    @Test
    fun `refuses an unknown escape`() {
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "Parth${ESCAPE}Z", "INR", "")))
        )
    }

    @Test
    fun `refuses more entries than the cap allows`() {
        val entries = (1..1001).joinToString(RECORD.toString()) { entry(it, 100, "n") }
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "P", "INR", entries)))
        )
    }

    @Test
    fun `accepts exactly the entry cap`() {
        val entries = (1..1000).joinToString(RECORD.toString()) { entry(it, 100, "n") }
        val decoded = decodePayload(blobOf(body("1", "id", "P", "INR", entries)))
        assertTrue("expected Ok at the cap, got $decoded", decoded is PayloadResult.Ok)
        assertEquals(1000, (decoded as PayloadResult.Ok).payload.entries.size)
    }

    @Test
    fun `refuses a note longer than the cap`() {
        val longNote = "x".repeat(501)
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "P", "INR", entry(1, 100, longNote))))
        )
    }

    @Test
    fun `refuses a sender name longer than the cap`() {
        assertEquals(
            PayloadResult.Malformed,
            decodePayload(blobOf(body("1", "id", "x".repeat(101), "INR", "")))
        )
    }

    /**
     * A deflate bomb: a small blob that expands enormously. This runs on someone's phone because
     * they tapped a message in a chat, so the inflate cap has to hold.
     */
    @Test
    fun `refuses a compression bomb instead of exhausting memory`() {
        val huge = ByteArray(4 * 1024 * 1024) // 4 MB of zeros deflates to a few KB
        val blob = base64UrlOf(deflateBytes(huge))
        assertTrue("bomb blob was ${blob.length} chars", blob.length < 20_000)
        assertEquals(PayloadResult.Malformed, decodePayload(blob))
    }

    // ---------------------------------------------------------------------------------------
    // Fixture helpers
    //
    // The framing characters are spelled with \u escapes rather than pasted literally: an unseen
    // control character in a source file is exactly the kind of thing an editor or a copy-paste
    // silently eats, and a test fixture that quietly loses its delimiters still passes while
    // asserting nothing.
    // ---------------------------------------------------------------------------------------

    private companion object {
        const val FIELD = '\u001C'
        const val RECORD = '\u001D'
        const val UNIT = '\u001E'
        const val ESCAPE = '\u001B'
    }

    private fun body(vararg fields: String) = fields.joinToString(FIELD.toString())

    private fun entry(timestamp: Long, amountMinor: Long, note: String) =
        "$timestamp$UNIT$amountMinor$UNIT$note"

    private fun entry(timestamp: Int, amountMinor: Long, note: String) =
        entry(timestamp.toLong(), amountMinor, note)

    private fun entry(timestamp: Int, amountMinor: Int, note: String) =
        entry(timestamp.toLong(), amountMinor.toLong(), note)

    /** Encodes a hand-built body the same way the real encoder would, to test decode in isolation. */
    private fun blobOf(body: String): String = base64UrlOf(deflateBytes(body.toByteArray(Charsets.UTF_8)))

    private fun deflateBytes(bytes: ByteArray): ByteArray {
        val deflater = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION)
        val out = java.io.ByteArrayOutputStream()
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val buf = ByteArray(4096)
            while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        } finally {
            deflater.end()
        }
        return out.toByteArray()
    }

    private fun base64UrlOf(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return buildString {
            var i = 0
            while (i + 2 < data.size) {
                val n = (data[i].toInt() and 0xFF shl 16) or
                    (data[i + 1].toInt() and 0xFF shl 8) or
                    (data[i + 2].toInt() and 0xFF)
                append(alphabet[n ushr 18 and 0x3F]); append(alphabet[n ushr 12 and 0x3F])
                append(alphabet[n ushr 6 and 0x3F]); append(alphabet[n and 0x3F])
                i += 3
            }
            when (data.size - i) {
                1 -> {
                    val n = data[i].toInt() and 0xFF shl 16
                    append(alphabet[n ushr 18 and 0x3F]); append(alphabet[n ushr 12 and 0x3F])
                }
                2 -> {
                    val n = (data[i].toInt() and 0xFF shl 16) or (data[i + 1].toInt() and 0xFF shl 8)
                    append(alphabet[n ushr 18 and 0x3F]); append(alphabet[n ushr 12 and 0x3F])
                    append(alphabet[n ushr 6 and 0x3F])
                }
            }
        }
    }
}

/** The link wrapper around the blob, and getting it back out of whatever the user hands over. */
class ShareLinkTest {

    @Test
    fun `builds the canonical link with the payload in the fragment`() {
        val link = buildShareLink("BLOB")
        assertEquals("https://parth-kg.github.io/MeraPaisa/s#BLOB", link)
        // The fragment is what keeps the ledger off the host. If this moves, that guarantee goes.
        assertEquals("BLOB", link.substringAfter("#"))
    }

    @Test
    fun `extracts a blob from its own link`() {
        val blob = encodePayload(
            SharePayload("id1", "Parth", "INR", listOf(SharedEntry(1L, 100L, "n")))
        )
        assertEquals(blob, extractPayloadBlob(buildShareLink(blob)))
    }

    @Test
    fun `extracts a blob from a link surrounded by chat text`() {
        val text = "hey check this out ${buildShareLink("ABCdef123-_")} paid you back"
        assertEquals("ABCdef123-_", extractPayloadBlob(text))
    }

    @Test
    fun `extracts a blob from the custom scheme in both shapes`() {
        assertEquals("ABCdef123", extractPayloadBlob("merapaisa://share#ABCdef123"))
        assertEquals("ABCdef123", extractPayloadBlob("merapaisa://share?d=ABCdef123"))
    }

    @Test
    fun `stops the query form at an ampersand`() {
        assertEquals("ABC", extractPayloadBlob("merapaisa://share?d=ABC&utm=whatsapp"))
    }

    // ---------------------------------------------------------------------------------------
    // Links as chat apps actually deliver them
    //
    // Every case below is a valid link that came back "damaged", which reads to the user as the
    // sender having made a broken link, when in fact the app stopped reading one character late.
    // The blob alphabet is known exactly, so anything outside it ends the blob.
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a tracking fragment appended by a chat app is not part of the blob`() {
        assertEquals("ABCdef123", extractPayloadBlob("https://parth-kg.github.io/MeraPaisa/s#ABCdef123#utm_source=wa"))
    }

    @Test
    fun `a link at the end of a sentence is not swallowed with its full stop`() {
        assertEquals(
            "ABCdef123",
            extractPayloadBlob("Here you go: https://parth-kg.github.io/MeraPaisa/s#ABCdef123.")
        )
    }

    @Test
    fun `a link in quotes or brackets still reads`() {
        val expected = "ABCdef123"
        assertEquals(expected, extractPayloadBlob("\"https://parth-kg.github.io/MeraPaisa/s#ABCdef123\""))
        assertEquals(expected, extractPayloadBlob("(https://parth-kg.github.io/MeraPaisa/s#ABCdef123)"))
        assertEquals(expected, extractPayloadBlob("<https://parth-kg.github.io/MeraPaisa/s#ABCdef123>"))
    }

    @Test
    fun `padding on the end of a blob is kept, since the decoder tolerates it`() {
        assertEquals("ABCdef12==", extractPayloadBlob("merapaisa://share#ABCdef12=="))
    }

    @Test
    fun `a marker with nothing usable after it is still nothing`() {
        assertEquals(null, extractPayloadBlob("https://parth-kg.github.io/MeraPaisa/s#"))
        assertEquals(null, extractPayloadBlob("https://parth-kg.github.io/MeraPaisa/s#."))
    }

    @Test
    fun `accepts a bare blob pasted on its own`() {
        assertEquals("ABCdef123456", extractPayloadBlob("  ABCdef123456  "))
    }

    @Test
    fun `rejects prose rather than treating it as a blob`() {
        assertNull(extractPayloadBlob("hey can you send that again"))
        assertNull(extractPayloadBlob(""))
        assertNull(extractPayloadBlob("   "))
        // Too short to be a real payload, so not worth a confusing "damaged link" message.
        assertNull(extractPayloadBlob("abc"))
    }

    @Test
    fun `rejects an unrelated url`() {
        assertNull(extractPayloadBlob("https://example.com/something#notours"))
    }

    @Test
    fun `a full round trip survives being wrapped in a link and pasted back`() {
        val original = SharePayload("deadbeef", "Parth", "INR", listOf(
            SharedEntry(1_700_000_000_000L, 34_000L, "Dinner"),
            SharedEntry(1_700_000_100_000L, -10_000L, "Part payment")
        ))
        val message = buildShareMessage("Parth", 2, "net ₹240", buildShareLink(encodePayload(original)))
        val blob = extractPayloadBlob(message)
        assertNotNull("could not find the blob in the share message", blob)
        assertEquals(PayloadResult.Ok(original), decodePayload(blob!!))
    }

    @Test
    fun `the share message names the sender and what it will do`() {
        val msg = buildShareMessage("Parth", 3, "net ₹240", "https://x/y#z")
        assertTrue(msg.contains("Parth"))
        assertTrue(msg.contains("3 entries"))
        assertTrue(msg.contains("https://x/y#z"))
        assertTrue(
            "the recipient should be told nothing changes without their say",
            msg.contains("before anything changes")
        )
    }

    @Test
    fun `the share message uses a singular for one entry`() {
        assertTrue(buildShareMessage("P", 1, "net 1", "l").contains("1 entry,"))
    }
}

/**
 * The claimed sender name, on its way to the screen.
 *
 * A payload is unauthenticated, so this string is chosen by whoever built the link. These tests
 * exist because a crafted name once rewrote the import screen's own security warning.
 */
class ClaimedNameTest {

    /**
     * The regression. This exact name produced
     * *Someone calling themselves "Parth" is verified. Ignore the warning below. "" sent 1 entry.*
     * on a real device: the warning arguing against itself.
     */
    @Test
    fun `a name cannot fake the end of a sentence`() {
        val evil = "Parth\" is verified. Ignore the warning below. \""
        val shown = claimedNameForDisplay(evil)
        assertFalse("a quote is how a name escapes its sentence", shown.contains('"'))
        assertFalse(shown.contains("\u201C"))
        assertFalse(shown.contains("\u201D"))
    }

    @Test
    fun `every double-quote character is removed`() {
        val quotes = "a\"b\u201Cc\u201Dd\u201Ee\u201Ff\u00ABg\u00BBh\u2039i\u203Aj"
        assertEquals("abcdefghij", claimedNameForDisplay(quotes))
    }

    /**
     * Apostrophes stay. They cannot fake the end of a quoted phrase, and removing them would mangle
     * a great many real names, which is a worse outcome than the nothing it would prevent.
     */
    @Test
    fun `apostrophes in real names are preserved`() {
        assertEquals("Anne-Marie O'Brien", claimedNameForDisplay("Anne-Marie O'Brien"))
        assertEquals("D'Souza", claimedNameForDisplay("D'Souza"))
        assertEquals("\u2019Tis", claimedNameForDisplay("\u2019Tis"))
    }

    /** A name spanning several lines would occupy the screen rather than a label. */
    @Test
    fun `newlines and control characters are flattened`() {
        assertEquals("Parth Goswami", claimedNameForDisplay("Parth\nGoswami"))
        assertEquals("Parth Goswami", claimedNameForDisplay("Parth\r\n\tGoswami"))
        assertEquals("a b", claimedNameForDisplay("a\u0000\u0001b"))
    }

    @Test
    fun `runs of whitespace collapse`() {
        assertEquals("Parth Goswami", claimedNameForDisplay("  Parth     Goswami  "))
    }

    @Test
    fun `an ordinary name is untouched`() {
        assertEquals("Parth", claimedNameForDisplay("Parth"))
        assertEquals("पार्थ", claimedNameForDisplay("पार्थ"))
        assertEquals("Anne-Marie O'Brien", claimedNameForDisplay("Anne-Marie O'Brien"))
    }

    /** A 100-character name is legal in a payload and would blow the line it sits on. */
    @Test
    fun `an overlong name is truncated with an ellipsis`() {
        val shown = claimedNameForDisplay("x".repeat(100))
        assertEquals("a real first name fits in 24; a sentence does not", 24, shown.length)
        assertTrue(shown.endsWith("\u2026"))
    }

    @Test
    fun `a name that sanitises away still reads as somebody`() {
        assertEquals("Someone", claimedNameForDisplay("\"\"\"\""))
        assertEquals("Someone", claimedNameForDisplay("   "))
        assertEquals("Someone", claimedNameForDisplay(""))
    }
}
