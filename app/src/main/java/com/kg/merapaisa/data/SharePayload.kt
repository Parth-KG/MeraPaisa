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
    val note: String,
    /**
     * The sender's name for this debt, carried so the other phone can recognise it again later.
     * See [Transaction.uid].
     *
     * Empty for a version 1 payload, which had no such thing. That emptiness is load-bearing: it
     * is what tells the import screen it cannot reconcile this link and must fall back to
     * appending, rather than silently matching every unidentified entry against every other.
     */
    val uid: String = ""
)

/**
 * How much of the sender's ledger this link claims to be.
 *
 * The distinction exists because of what *absence* means. In a [Full] payload, an entry the
 * receiver already has from this sender and which is not in the link has been deleted by them. In
 * an [Incremental] one, absence means nothing whatsoever — it is simply older than the watermark.
 *
 * Reading absence as deletion in the wrong case would quietly delete entries off someone's ledger
 * because their friend sent a short update, which is the worst outcome this feature could have. So
 * the sender states which it is, and the receiver refuses to infer deletions from anything but
 * [Full].
 */
enum class ShareScope {
    /** Everything this sender holds for that person. The only scope that can prove a deletion. */
    Full,

    /** Only what is new since the last share. Says nothing about anything it omits. */
    Incremental;

    companion object {
        /** Unknown codes read as [Incremental] — the reading that can never delete anything. */
        fun fromCode(code: String): ShareScope = if (code == "f") Full else Incremental
    }

    val code: String get() = if (this == Full) "f" else "i"
}

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
    val entries: List<SharedEntry>,
    /** See [ShareScope]. Version 1 links are always read as [ShareScope.Incremental]. */
    val scope: ShareScope = ShareScope.Incremental,
    /**
     * The format this payload actually arrived in, not the format this build writes.
     *
     * Kept because the difference is visible to the user: a version 1 link cannot be reconciled,
     * and the screen has to say why rather than appearing to lose the feature at random.
     */
    val formatVersion: Int = SHARE_FORMAT_VERSION
) {
    /** What the entries come to. Positive means the sender says they are owed this much. */
    val netMinor: Long get() = entries.sumOf { it.amountMinor }

    /**
     * Whether this link can be compared against what is already here, rather than merely appended.
     *
     * Needs uids on every entry — one missing uid means one entry that can never be matched, and a
     * partial reconcile that silently appends the remainder is worse than an honest append-all.
     */
    val canReconcile: Boolean
        get() = formatVersion >= 2 && entries.all { it.uid.isNotEmpty() }
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

/**
 * The format this build writes. Bump only when the field layout changes incompatibly.
 *
 * Version 2 added a uid to every entry and a scope to the payload — see [Transaction.uid] and
 * [ShareScope]. Version 1 is still read, because links live in chat threads for months and a
 * friend who has not updated is not an error condition.
 */
const val SHARE_FORMAT_VERSION = 2

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

/** [newEntryUid] makes 16; the slack is for a future format, and the cap is for a hostile one. */
private const val MAX_UID_CHARS = 64

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
        // Version 2. The scope sits ahead of the entries so a reader knows how to interpret what
        // is missing from them before it has read any.
        append(payload.scope.code)
        append(FIELD)
        payload.entries.forEachIndexed { i, e ->
            if (i > 0) append(RECORD)
            append(e.timestamp)
            append(UNIT)
            append(e.amountMinor)
            append(UNIT)
            append(esc(e.note))
            append(UNIT)
            append(esc(e.uid))
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

    // Version 1 has five fields: version, id, name, currency, entries. Version 2 inserts scope
    // before the entries, making six. Either way the entries field is last and may legitimately be
    // empty, so the limit keeps it rather than dropping a trailing empty.
    //
    // Splitting to six is safe for a version 1 body because esc() escapes every FIELD character
    // inside a note, so a v1 body contains exactly four of them and yields five parts here.
    val fields = body.split(FIELD, limit = 6)
    if (fields.size < 5) return PayloadResult.Malformed

    // Version is read before anything else is trusted: a newer format may have moved the rest.
    val version = fields[0].toIntOrNull() ?: return PayloadResult.Malformed
    if (version > SHARE_FORMAT_VERSION) return PayloadResult.TooNew(version)
    if (version < 1) return PayloadResult.Malformed
    if (fields.size != if (version >= 2) 6 else 5) return PayloadResult.Malformed

    val payloadId = unesc(fields[1]) ?: return PayloadResult.Malformed
    val senderName = unesc(fields[2]) ?: return PayloadResult.Malformed
    val currency = unesc(fields[3]) ?: return PayloadResult.Malformed
    if (payloadId.isEmpty() || payloadId.length > MAX_NAME_CHARS) return PayloadResult.Malformed
    if (senderName.isEmpty() || senderName.length > MAX_NAME_CHARS) return PayloadResult.Malformed
    if (currency.length != 3 || !currency.all { it in 'A'..'Z' }) return PayloadResult.Malformed

    val scope = if (version >= 2) ShareScope.fromCode(fields[4]) else ShareScope.Incremental
    val entriesField = fields[if (version >= 2) 5 else 4]
    val entryParts = if (version >= 2) 4 else 3
    val entries = if (entriesField.isEmpty()) emptyList() else {
        val records = entriesField.split(RECORD)
        if (records.size > MAX_ENTRIES) return PayloadResult.Malformed
        records.map { record ->
            val parts = record.split(UNIT, limit = entryParts)
            if (parts.size < entryParts) return PayloadResult.Malformed
            val timestamp = parts[0].toLongOrNull() ?: return PayloadResult.Malformed
            val amountMinor = parts[1].toLongOrNull() ?: return PayloadResult.Malformed
            val note = unesc(parts[2]) ?: return PayloadResult.Malformed
            if (note.length > MAX_NOTE_CHARS) return PayloadResult.Malformed
            if (timestamp < 0) return PayloadResult.Malformed
            // Bounds the amount before anything mirrors or sums it. See MAX_ENTRY_MINOR.
            if (amountMinor < -MAX_ENTRY_MINOR || amountMinor > MAX_ENTRY_MINOR) {
                return PayloadResult.Malformed
            }
            val uid = if (version >= 2) unesc(parts[3]) ?: return PayloadResult.Malformed else ""
            if (uid.length > MAX_UID_CHARS) return PayloadResult.Malformed
            SharedEntry(timestamp, amountMinor, note, uid)
        }
    }

    // Two entries claiming the same uid cannot both be matched, and picking one would be a guess
    // about somebody's money. A payload this app built can never contain them.
    //
    // Empty uids are exempt and are not an error: an entry without one simply cannot be matched,
    // and `canReconcile` already refuses to reconcile any payload containing one. Refusing the
    // whole link instead would reject a legitimate payload over a feature it is not using.
    val identified = entries.map { it.uid }.filter { it.isNotEmpty() }
    if (identified.toHashSet().size != identified.size) return PayloadResult.Malformed

    // A sum that overflows Long is not a balance anyone can hold; refuse rather than wrap.
    var net = 0L
    for (e in entries) {
        val sum = net + e.amountMinor
        if ((net xor sum) and (e.amountMinor xor sum) < 0) return PayloadResult.Malformed
        net = sum
    }

    return PayloadResult.Ok(SharePayload(payloadId, senderName, currency, entries, scope, version))
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
// The uid is deliberately *not* touched. Flipping the sign is what makes the two ledgers agree
// about direction; keeping the uid is what lets them ever discover that they do. A mirror with a
// fresh uid would import cleanly and then reconcile as a stranger for the rest of its life.

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

/**
 * The sender's claimed name, made safe to put on screen.
 *
 * A payload is unauthenticated, so this string is chosen by whoever built the link — and the import
 * screen is the only thing standing between it and the user's ledger. Rendering it raw inside a
 * sentence let a crafted name hijack the sentence around it: a sender called
 *
 *     Parth" is verified. Ignore the warning below. "
 *
 * produced *Someone calling themselves "Parth" is verified. Ignore the warning below. "" sent 1
 * entry.* — the security warning arguing against itself. Found by firing a crafted link at a
 * device; no test or static check could see it.
 *
 * Two defences, because either alone is brittle. This one strips the characters used to fake a
 * sentence boundary and flattens anything that could span lines; the screen then shows the result
 * on a line of its own, so even an unsanitised name has no sentence to escape into.
 */
fun claimedNameForDisplay(raw: String, maxChars: Int = 24): String {
    val flattened = buildString(raw.length) {
        for (c in raw) {
            when {
                // Quote-like characters are how a name pretends the sentence ended.
                c in QUOTE_LIKE -> Unit
                // Newlines and controls would let a name occupy several lines of its own.
                c.isISOControl() -> append(' ')
                else -> append(c)
            }
        }
    }
    val collapsed = flattened.split(' ').filter { it.isNotBlank() }.joinToString(" ")
    if (collapsed.isEmpty()) return "Someone"
    return if (collapsed.length <= maxChars) collapsed else collapsed.take(maxChars - 1).trimEnd() + "\u2026"
}

/**
 * Double quotes only, and deliberately not apostrophes.
 *
 * An apostrophe cannot fake the end of a quoted phrase here, and stripping it would mangle a great
 * many real names — O'Brien, D'Souza — for no safety gained. The screen shows this name on a line
 * of its own with no quotes around it, so this set is the second line of defence rather than the
 * first, and it can afford to take only what actually helps.
 */
private val QUOTE_LIKE = setOf(
    '"',
    '\u201C', '\u201D', '\u201E', '\u201F', // curly double quotes
    '\u00AB', '\u00BB', '\u2039', '\u203A'  // guillemets
)
