package com.kg.merapaisa.data

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * A two-sided ledger over share links.
 *
 * You record that someone owes you ₹340. They have to record the same debt with the sign the
 * other way round, or the two ledgers disagree and one of you is wrong. This file is the wire
 * format that carries your entries to their phone so their app can write the mirror.
 *
 * There is no server and no account. The whole payload rides inside the link, which means:
 *
 *  - **It is unauthenticated.** Anyone can craft one. Nothing here proves the sender is who the
 *    payload says, and nothing here should imply otherwise — the import screen has to say so.
 *  - **Everything [decodePayload] touches is hostile input.** The size caps below are not
 *    tidiness; they are what stops a crafted link from exhausting memory on the importing phone.
 *
 * Deliberately hand-rolled rather than `org.json` + `android.util.Base64`: both are Android
 * framework stubs that throw under plain JVM unit tests, and `java.util.Base64` needs API 26
 * against this app's minSdk of 24. Everything here is pure JDK, so the codec is testable
 * without a device, which for a money format is the point.
 */

/** One entry on its way to the other phone. Amounts are minor units, as everywhere else. */
data class SharedEntry(
    val timestamp: Long,
    val amountMinor: Long,
    val note: String
)

/**
 * What one link carries: who sent it, in what currency, and the entries themselves.
 *
 * [payloadId] is minted once when the link is built and travels with it, so re-sending the
 * same link cannot double-apply. See the `applied_payloads` table.
 */
data class SharePayload(
    val payloadId: String,
    val senderName: String,
    val currency: String,
    val entries: List<SharedEntry>
) {
    /** What the entries come to. Positive means the sender says they are owed this much. */
    val netMinor: Long get() = entries.sumOf { it.amountMinor }
}

/**
 * The outcome of reading a link. A sealed result rather than an exception or a null, because
 * the import screen has to tell the user *which* way it failed — "this link is damaged" and
 * "this link is from a newer version of the app" need different sentences.
 */
sealed interface PayloadResult {
    data class Ok(val payload: SharePayload) : PayloadResult

    /** Not a Mera Paisa payload, or damaged in transit. */
    data object Malformed : PayloadResult

    /** A future format. The app must refuse rather than guess at fields it does not know. */
    data class TooNew(val version: Int) : PayloadResult
}

/** The format this build writes. Bump only when the field layout changes incompatibly. */
const val SHARE_FORMAT_VERSION = 1

/**
 * Caps on what [decodePayload] will accept. A link is untrusted input arriving from a chat app,
 * so each of these is a refusal rather than a truncation — quietly dropping half a payload
 * would write a balance that matches neither ledger.
 */
private const val MAX_ENCODED_CHARS = 64 * 1024
private const val MAX_INFLATED_BYTES = 256 * 1024
private const val MAX_ENTRIES = 1000
private const val MAX_NOTE_CHARS = 500
private const val MAX_NAME_CHARS = 100

/**
 * The largest single amount a payload may carry: nine whole digits and two decimals, which is
 * exactly what `Money.parseAmountToMinor` will accept from a numpad. An entry the app could not
 * have produced locally is refused rather than stored.
 *
 * This bound is also what makes the arithmetic downstream safe. It rules out `Long.MIN_VALUE`,
 * which is the one value `mirrored()` cannot flip — `-Long.MIN_VALUE` is `Long.MIN_VALUE`, so a
 * crafted link carrying it would import a debt pointing the wrong way. And at 1000 entries the
 * worst-case sum is about 1e14, far short of overflowing a Long.
 */
private const val MAX_ENTRY_MINOR = 99_999_999_999L

/**
 * Builds the shareable blob: escaped fields, deflated, then Base64URL.
 *
 * Deflate earns its place here — entry notes repeat heavily ("Dinner", "Auto") and a link a
 * chat app might wrap or truncate is worth keeping short.
 */
fun encodePayload(payload: SharePayload): String {
    val body = buildString {
        append(SHARE_FORMAT_VERSION)
        append(FIELD)
        append(esc(payload.payloadId))
        append(FIELD)
        append(esc(payload.senderName))
        append(FIELD)
        append(esc(payload.currency))
        append(FIELD)
        payload.entries.forEachIndexed { i, e ->
            if (i > 0) append(RECORD)
            append(e.timestamp)
            append(UNIT)
            append(e.amountMinor)
            append(UNIT)
            append(esc(e.note))
        }
    }
    return base64UrlEncode(deflate(body.toByteArray(Charsets.UTF_8)))
}

/**
 * Reads a blob back. Returns a [PayloadResult] for every failure mode rather than throwing,
 * since "the user pasted something that is not a link" is an ordinary path here, not a bug.
 */
fun decodePayload(encoded: String): PayloadResult {
    val trimmed = encoded.trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_ENCODED_CHARS) return PayloadResult.Malformed

    val raw = base64UrlDecode(trimmed) ?: return PayloadResult.Malformed
    val body = inflate(raw)?.toString(Charsets.UTF_8) ?: return PayloadResult.Malformed

    // Five fields exactly: version, id, name, currency, entries. The entries field is last and
    // may legitimately be empty, so the limit keeps it rather than dropping a trailing empty.
    val fields = body.split(FIELD, limit = 5)
    if (fields.size < 5) return PayloadResult.Malformed

    // Version is read before anything else is trusted: a newer format may have moved the rest.
    val version = fields[0].toIntOrNull() ?: return PayloadResult.Malformed
    if (version > SHARE_FORMAT_VERSION) return PayloadResult.TooNew(version)
    if (version < 1) return PayloadResult.Malformed

    val payloadId = unesc(fields[1]) ?: return PayloadResult.Malformed
    val senderName = unesc(fields[2]) ?: return PayloadResult.Malformed
    val currency = unesc(fields[3]) ?: return PayloadResult.Malformed
    if (payloadId.isEmpty() || payloadId.length > MAX_NAME_CHARS) return PayloadResult.Malformed
    if (senderName.isEmpty() || senderName.length > MAX_NAME_CHARS) return PayloadResult.Malformed
    if (currency.length != 3 || !currency.all { it in 'A'..'Z' }) return PayloadResult.Malformed

    val entriesField = fields[4]
    val entries = if (entriesField.isEmpty()) emptyList() else {
        val records = entriesField.split(RECORD)
        if (records.size > MAX_ENTRIES) return PayloadResult.Malformed
        records.map { record ->
            val parts = record.split(UNIT, limit = 3)
            if (parts.size < 3) return PayloadResult.Malformed
            val timestamp = parts[0].toLongOrNull() ?: return PayloadResult.Malformed
            val amountMinor = parts[1].toLongOrNull() ?: return PayloadResult.Malformed
            val note = unesc(parts[2]) ?: return PayloadResult.Malformed
            if (note.length > MAX_NOTE_CHARS) return PayloadResult.Malformed
            if (timestamp < 0) return PayloadResult.Malformed
            // Bounds the amount before anything mirrors or sums it. See MAX_ENTRY_MINOR.
            if (amountMinor < -MAX_ENTRY_MINOR || amountMinor > MAX_ENTRY_MINOR) {
                return PayloadResult.Malformed
            }
            SharedEntry(timestamp, amountMinor, note)
        }
    }

    // A sum that overflows Long is not a balance anyone can hold; refuse rather than wrap.
    var net = 0L
    for (e in entries) {
        val sum = net + e.amountMinor
        if ((net xor sum) and (e.amountMinor xor sum) < 0) return PayloadResult.Malformed
        net = sum
    }

    return PayloadResult.Ok(SharePayload(payloadId, senderName, currency, entries))
}

/**
 * Turns a payload into its mirror: the same entries with every sign flipped.
 *
 * This is the whole point of the feature and the one line most worth reading twice. What the
 * sender recorded as "owed to me" has to land on the other phone as "I owe" — importing the
 * amounts as sent would give two ledgers that agree on the number and disagree on who pays.
 *
 * Safe to negate because [decodePayload] bounds every amount to `MAX_ENTRY_MINOR` first. That
 * matters more than it looks: `-Long.MIN_VALUE` is still `Long.MIN_VALUE`, so without that bound
 * a crafted link could carry one entry this function would hand back unflipped — a debt pointing
 * the wrong way, written by the mirroring step itself.
 */
fun SharePayload.mirrored(): List<SharedEntry> =
    entries.map { it.copy(amountMinor = -it.amountMinor) }

// ---------------------------------------------------------------------------------------------
// Field framing
//
// Control characters rather than punctuation, so ordinary notes ("Dinner, drinks; cab") need no
// escaping in practice and the compressed payload stays short. Escaping still exists because a
// note is free text and may contain anything at all, including these.
// ---------------------------------------------------------------------------------------------

private const val FIELD = '\u001C'  // file separator, between the five top-level fields
private const val RECORD = '\u001D'  // group separator, between entries
private const val UNIT = '\u001E'  // record separator, between an entry's three parts
private const val ESCAPE = '\u001B'  // escape, so a note may contain any of the above

private fun esc(s: String): String = buildString(s.length) {
    for (c in s) when (c) {
        ESCAPE -> append(ESCAPE).append('0')
        FIELD -> append(ESCAPE).append('1')
        RECORD -> append(ESCAPE).append('2')
        UNIT -> append(ESCAPE).append('3')
        else -> append(c)
    }
}

/** Null on a dangling or unknown escape — that is a damaged payload, not a recoverable one. */
private fun unesc(s: String): String? = buildString(s.length) {
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c != ESCAPE) { append(c); i++; continue }
        if (i + 1 >= s.length) return null
        when (s[i + 1]) {
            '0' -> append(ESCAPE)
            '1' -> append(FIELD)
            '2' -> append(RECORD)
            '3' -> append(UNIT)
            else -> return null
        }
        i += 2
    }
}

// ---------------------------------------------------------------------------------------------
// Deflate
// ---------------------------------------------------------------------------------------------

private fun deflate(input: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    try {
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream(input.size / 2 + 32)
        val buffer = ByteArray(4096)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    } finally {
        deflater.end()
    }
}

/**
 * Null if the bytes are not valid deflate, or if they expand past [MAX_INFLATED_BYTES].
 *
 * The cap is the one that matters: a few hundred bytes of crafted link can otherwise inflate to
 * gigabytes, and this runs on someone's phone because they tapped a message in a chat.
 */
private fun inflate(input: ByteArray): ByteArray? {
    val inflater = Inflater()
    try {
        inflater.setInput(input)
        val out = ByteArrayOutputStream(input.size * 3 + 32)
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val n = try {
                inflater.inflate(buffer)
            } catch (e: DataFormatException) {
                return null
            }
            // Nothing came out and it is not finished: truncated input, not a pause. Without
            // this the loop would spin forever on a clipped link.
            if (n == 0 && !inflater.finished()) return null
            if (out.size() + n > MAX_INFLATED_BYTES) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    } finally {
        inflater.end()
    }
}

// ---------------------------------------------------------------------------------------------
// Base64URL, unpadded
//
// Hand-rolled for the reasons in the file header: java.util.Base64 is API 26 against a minSdk
// of 24, and android.util.Base64 cannot run in a JVM unit test.
// ---------------------------------------------------------------------------------------------

private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

private fun base64UrlEncode(bytes: ByteArray): String = buildString((bytes.size + 2) / 3 * 4) {
    var i = 0
    while (i + 2 < bytes.size) {
        val n = (bytes[i].toInt() and 0xFF shl 16) or
            (bytes[i + 1].toInt() and 0xFF shl 8) or
            (bytes[i + 2].toInt() and 0xFF)
        append(ALPHABET[n ushr 18 and 0x3F])
        append(ALPHABET[n ushr 12 and 0x3F])
        append(ALPHABET[n ushr 6 and 0x3F])
        append(ALPHABET[n and 0x3F])
        i += 3
    }
    when (bytes.size - i) {
        1 -> {
            val n = bytes[i].toInt() and 0xFF shl 16
            append(ALPHABET[n ushr 18 and 0x3F])
            append(ALPHABET[n ushr 12 and 0x3F])
        }
        2 -> {
            val n = (bytes[i].toInt() and 0xFF shl 16) or (bytes[i + 1].toInt() and 0xFF shl 8)
            append(ALPHABET[n ushr 18 and 0x3F])
            append(ALPHABET[n ushr 12 and 0x3F])
            append(ALPHABET[n ushr 6 and 0x3F])
        }
    }
}

/**
 * Null on any character outside the alphabet, or on a length that cannot be whole bytes.
 *
 * Standard Base64 `+` and `/` are accepted too: a chat app, or a user retyping a link, may hand
 * back the non-URL variant, and refusing it would look like a broken link rather than a
 * transport quirk. Trailing `=` padding is tolerated and ignored for the same reason.
 */
private fun base64UrlDecode(s: String): ByteArray? {
    val clean = s.trimEnd('=')
    // Four Base64 characters carry three bytes; a remainder of one cannot be any whole byte.
    if (clean.length % 4 == 1) return null

    val out = ByteArrayOutputStream(clean.length / 4 * 3 + 3)
    var buffer = 0
    var bits = 0
    for (c in clean) {
        val v = when (c) {
            '+' -> 62
            '/' -> 63
            else -> ALPHABET.indexOf(c)
        }
        if (v < 0) return null
        buffer = (buffer shl 6) or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out.write(buffer ushr bits and 0xFF)
        }
    }
    // Leftover bits must be zero padding rather than discarded data.
    if (bits > 0 && (buffer and ((1 shl bits) - 1)) != 0) return null
    return out.toByteArray()
}
