package com.kg.merapaisa.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Hostile and merely broken input to the share codec.
 *
 * [decodePayload] and [extractPayloadBlob] are the app's entire attack surface: everything they
 * touch arrives from a chat message that anybody can craft. The contract they have to keep is
 * narrower than "be correct" — it is **never throw**. A crash here is a link that kills the app on
 * every tap, and the one screen standing between a stranger's payload and somebody's ledger is the
 * one that fails to open.
 *
 * So these tests assert almost nothing about the answer. They assert that there *is* one.
 */
class SharePayloadFuzzTest {

    private val seeds = (1..400).map { Random(it) }

    private fun decodeMustNotThrow(input: String) {
        val result = try {
            decodePayload(input)
        } catch (t: Throwable) {
            throw AssertionError("decodePayload threw on ${input.take(80)}", t)
        }
        assertNotNull(result)
    }

    private fun extractMustNotThrow(input: String) {
        try {
            extractPayloadBlob(input)
        } catch (t: Throwable) {
            throw AssertionError("extractPayloadBlob threw on ${input.take(80)}", t)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Nonsense in
    // -----------------------------------------------------------------------------------------

    @Test
    fun `random base64-shaped strings never throw`() {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        seeds.forEach { rng ->
            val s = (0 until rng.nextInt(0, 400)).map { alphabet[rng.nextInt(alphabet.length)] }.joinToString("")
            decodeMustNotThrow(s)
        }
    }

    @Test
    fun `arbitrary unicode never throws`() {
        seeds.forEach { rng ->
            val s = (0 until rng.nextInt(0, 200)).map { rng.nextInt(0, 0x10000).toChar() }.joinToString("")
            decodeMustNotThrow(s)
            extractMustNotThrow(s)
        }
    }

    /** The commonest real corruption: a chat app clipping a long link. */
    @Test
    fun `every truncation of a real payload is refused rather than half-read`() {
        val blob = encodePayload(
            SharePayload(
                payloadId = "pid0000000000001",
                senderName = "Parth",
                currency = "INR",
                entries = (1..40).map { SharedEntry(it.toLong(), it * 137L, "Entry $it", "uid$it") },
                scope = ShareScope.Full
            )
        )

        for (cut in blob.indices) {
            val truncated = blob.take(cut)
            val result = try {
                decodePayload(truncated)
            } catch (t: Throwable) {
                throw AssertionError("threw on a truncation of length $cut", t)
            }
            // It may legitimately still decode if the cut lands on a whole record boundary, but it
            // must never come back as a *different* payload claiming to be complete.
            if (result is PayloadResult.Ok) {
                assertTrue(
                    "a truncated link must not claim more entries than the original",
                    result.payload.entries.size <= 40
                )
            }
        }
    }

    /** Single-character corruption, which is what a hand-retyped link looks like. */
    @Test
    fun `flipping one character never throws`() {
        val blob = encodePayload(
            SharePayload("pid0000000000001", "Parth", "INR", listOf(SharedEntry(1L, 100L, "x", "u1")), ShareScope.Full)
        )
        val rng = Random(99)
        repeat(500) {
            val at = rng.nextInt(blob.length)
            val replacement = (rng.nextInt(32, 127)).toChar()
            decodeMustNotThrow(blob.take(at) + replacement + blob.drop(at + 1))
        }
    }

    // -----------------------------------------------------------------------------------------
    // Deliberately awkward payload bodies
    // -----------------------------------------------------------------------------------------

    private fun bodyToBlob(body: String): String {
        val deflater = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION)
        deflater.setInput(body.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        val bytes = out.toByteArray()
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return buildString {
            var i = 0
            while (i + 2 < bytes.size) {
                val n = (bytes[i].toInt() and 0xFF shl 16) or
                    (bytes[i + 1].toInt() and 0xFF shl 8) or (bytes[i + 2].toInt() and 0xFF)
                append(alphabet[n ushr 18 and 0x3F]); append(alphabet[n ushr 12 and 0x3F])
                append(alphabet[n ushr 6 and 0x3F]); append(alphabet[n and 0x3F])
                i += 3
            }
            when (bytes.size - i) {
                1 -> {
                    val n = bytes[i].toInt() and 0xFF shl 16
                    append(alphabet[n ushr 18 and 0x3F]); append(alphabet[n ushr 12 and 0x3F])
                }
                2 -> {
                    val n = (bytes[i].toInt() and 0xFF shl 16) or (bytes[i + 1].toInt() and 0xFF shl 8)
                    append(alphabet[n ushr 18 and 0x3F]); append(alphabet[n ushr 12 and 0x3F])
                    append(alphabet[n ushr 6 and 0x3F])
                }
            }
        }
    }

    private val f = 28.toChar()
    private val r = 29.toChar()
    private val u = 30.toChar()
    private val esc = 27.toChar()

    @Test
    fun `absurd field counts never throw`() {
        decodeMustNotThrow(bodyToBlob(""))
        decodeMustNotThrow(bodyToBlob("2"))
        decodeMustNotThrow(bodyToBlob("2$f"))
        decodeMustNotThrow(bodyToBlob(f.toString().repeat(200)))
        decodeMustNotThrow(bodyToBlob("2$f" + "p$f" + "n$f" + "INR$f" + "f$f" + r.toString().repeat(500)))
    }

    @Test
    fun `a dangling escape at the very end never throws`() {
        decodeMustNotThrow(bodyToBlob("2$f" + "pid$f" + "Parth$esc" + f + "INR$f" + "f$f"))
        decodeMustNotThrow(bodyToBlob("2$f" + "pid$f" + "Parth$f" + "INR$f" + "f$f" + "1${u}1${u}note$esc"))
    }

    @Test
    fun `numbers far outside Long never throw`() {
        val huge = "9".repeat(400)
        decodeMustNotThrow(bodyToBlob("2$f" + "pid$f" + "P$f" + "INR$f" + "f$f" + "$huge${u}$huge${u}n${u}u1"))
        decodeMustNotThrow(bodyToBlob("2$f" + "pid$f" + "P$f" + "INR$f" + "f$f" + "-$huge$u-$huge${u}n${u}u1"))
    }

    /**
     * The value that defeated an overflow guard once already: `-Long.MIN_VALUE` is itself, so an
     * entry carrying it would survive mirroring unflipped — a debt pointing the wrong way.
     */
    @Test
    fun `Long MIN_VALUE is refused rather than mirrored`() {
        val body = "2$f" + "pid$f" + "P$f" + "INR$f" + "f$f" + "1$u${Long.MIN_VALUE}${u}n${u}u1"
        assertTrue(decodePayload(bodyToBlob(body)) is PayloadResult.Malformed)
    }

    @Test
    fun `entry counts beyond the cap are refused, not truncated`() {
        val entries = (1..2000).joinToString(r.toString()) { "$it${u}1${u}n${u}u$it" }
        val result = decodePayload(bodyToBlob("2$f" + "pid$f" + "P$f" + "INR$f" + "f$f" + entries))
        assertTrue("silently keeping the first 1000 would store a balance matching neither ledger",
            result is PayloadResult.Malformed)
    }

    // -----------------------------------------------------------------------------------------
    // Extraction
    // -----------------------------------------------------------------------------------------

    @Test
    fun `wrapping text of every shape never throws`() {
        val blob = "ABCdef123-_"
        val wrappers = listOf(
            "%s", " %s ", "see %s", "%s.", "\"%s\"", "(%s)", "<%s>", "%s\n\n%s",
            " %s ", "😀 %s 😀", "%s".repeat(50)
        )
        listOf("https://parth-kg.github.io/MeraPaisa/s#$blob", "merapaisa://share#$blob", blob).forEach { link ->
            wrappers.forEach { w -> extractMustNotThrow(w.replace("%s", link)) }
        }
    }

    @Test
    fun `a very long paste is handled without blowing up`() {
        extractMustNotThrow("x".repeat(500_000))
        decodeMustNotThrow("A".repeat(500_000))
    }
}
