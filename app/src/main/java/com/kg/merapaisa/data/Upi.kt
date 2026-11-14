package com.kg.merapaisa.data

/*
 * UPI IDs and `upi://pay` links (v4.0.0). Pure functions, so every rule here runs under JVM tests.
 *
 * Nothing in this file pays or records anything. A link built here is handed to a UPI app, which
 * shows the payee and the amount and then waits for the payer's own PIN.
 */

/** The longest UPI ID kept. NPCI allows 255; real ones are far shorter. */
const val UPI_ID_MAX = 255

/** The longest payee name kept from a pasted link. */
const val UPI_NAME_MAX = 60

/** The longest text read as a pasted link at all. Anything longer is not a UPI link. */
private const val PASTE_MAX = 2_048

private val UPI_ID_SHAPE = Regex("""[A-Za-z0-9._-]{1,200}@[A-Za-z](?:[A-Za-z0-9]|\.(?=[A-Za-z0-9])){1,53}""")

/**
 * Whether [id] has the `name@handle` shape of a UPI ID, after trimming.
 *
 * It catches typos (a missing `@`, a space, a stray character), not a wrong but valid ID: only the
 * payer's UPI app knows whose an ID is, which is why the Pay sheet says to check the name it shows
 * before entering a PIN.
 */
fun isUpiId(id: String): Boolean {
    val trimmed = id.trim()
    return trimmed.length <= UPI_ID_MAX && UPI_ID_SHAPE.matches(trimmed)
}

/** A UPI ID as it is stored: trimmed, with nothing else changed. */
fun normaliseUpiId(id: String): String = id.trim()

/** What a pasted link or ID gives: the ID, and the name the link carried, if any. */
data class PastedUpi(val id: String, val name: String?)

/**
 * Reads what someone pasted into a UPI ID field: either an ID on its own, or a `upi://pay` link
 * (from a QR, a message or another app).
 *
 * A pasted link is hostile input. Only `pa` (the ID) and `pn` (the name) are read, each capped in
 * length; its amount, note, currency and anything else are ignored, so a link cannot set what you
 * pay. Null when the text is neither an ID nor a link with a valid `pa`.
 */
fun readPastedUpi(text: String): PastedUpi? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > PASTE_MAX) return null
    if (!trimmed.startsWith("upi://", ignoreCase = true)) {
        return if (isUpiId(trimmed)) PastedUpi(normaliseUpiId(trimmed), null) else null
    }
    val query = trimmed.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return null
    val params = HashMap<String, String>()
    for (pair in query.split('&')) {
        val key = pair.substringBefore('=').lowercase()
        if (key != "pa" && key != "pn") continue
        if (key in params) continue // the first one counts; a second cannot override it
        val value = percentDecode(pair.substringAfter('=', missingDelimiterValue = "")) ?: return null
        params[key] = value
    }
    val id = params["pa"]?.trim()?.takeIf { isUpiId(it) } ?: return null
    val name = params["pn"]
        ?.replace(Regex("""[\p{Cntrl}]"""), " ")
        ?.trim()
        ?.take(UPI_NAME_MAX)
        ?.takeIf { it.isNotBlank() }
    return PastedUpi(normaliseUpiId(id), name)
}

/**
 * A `upi://pay` link for paying [payeeId] [amountMinor] paise.
 *
 * `pa` is the ID, `pn` the payee's name when known, `am` the amount in rupees with two decimals,
 * `cu` always INR (UPI is rupees only), and `tn` the note only when one was typed: no default text
 * and no app name, since it lands in the payee's UPI history and bank statement (Parth, 14 Nov
 * 2026). Every value is percent-encoded, so a name or note cannot add parameters of its own.
 */
fun upiPayLink(payeeId: String, payeeName: String?, amountMinor: Long, note: String? = null): String {
    require(isUpiId(payeeId)) { "not a UPI ID" }
    require(amountMinor > 0) { "a payment needs a positive amount" }
    val rupees = amountMinor / 100
    val paise = amountMinor % 100
    return buildString {
        append("upi://pay?pa=").append(percentEncode(normaliseUpiId(payeeId), keep = "@"))
        payeeName?.trim()?.take(UPI_NAME_MAX)?.takeIf { it.isNotEmpty() }?.let {
            append("&pn=").append(percentEncode(it))
        }
        append("&am=").append(rupees).append('.').append(paise.toString().padStart(2, '0'))
        append("&cu=INR")
        note?.trim()?.takeIf { it.isNotEmpty() }?.let { append("&tn=").append(percentEncode(it.take(80))) }
    }
}

/** RFC 3986 unreserved characters pass; everything else, as UTF-8 bytes, becomes `%XX`. */
private fun percentEncode(value: String, keep: String = ""): String = buildString {
    for (byte in value.toByteArray(Charsets.UTF_8)) {
        val c = (byte.toInt() and 0xFF).toChar()
        if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~" || c in keep) {
            append(c)
        } else {
            append('%').append("0123456789ABCDEF"[byte.toInt() shr 4 and 0xF]).append("0123456789ABCDEF"[byte.toInt() and 0xF])
        }
    }
}

/** `%XX` and `+` decoded as UTF-8; null on a broken escape or bytes that are not UTF-8. */
private fun percentDecode(value: String): String? {
    val bytes = java.io.ByteArrayOutputStream()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '%' -> {
                if (i + 2 >= value.length) return null
                val b = value.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                bytes.write(b)
                i += 3
            }
            c == '+' -> { bytes.write(' '.code); i++ }
            else -> { bytes.write(c.toString().toByteArray(Charsets.UTF_8)); i++ }
        }
    }
    val decoder = Charsets.UTF_8.newDecoder()
    return try {
        decoder.decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        null
    }
}
