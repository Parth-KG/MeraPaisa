package com.kg.merapaisa.data

/*
 * Your UPI ID and the `upi://pay` links your QR codes carry (v3.6.0). Pure functions, so every rule
 * here runs under JVM tests.
 *
 * Nothing in this file pays or records anything, and nothing in the app opens a UPI app. A link
 * built here is only ever drawn as a QR, which a friend scans inside their own UPI app: the one way
 * a UPI app accepted a payment on the phone (15 Nov 2026). Links opened from another app, or tapped
 * in a chat, were refused.
 */

/** The longest UPI ID kept. NPCI allows 255; real ones are far shorter. */
const val UPI_ID_MAX = 255

/** The longest payee name a link carries. */
const val UPI_NAME_MAX = 60

private val UPI_ID_SHAPE = Regex("""[A-Za-z0-9._-]{1,200}@[A-Za-z](?:[A-Za-z0-9]|\.(?=[A-Za-z0-9])){1,53}""")

/**
 * Whether [id] has the `name@handle` shape of a UPI ID, after trimming.
 *
 * It catches typos (a missing `@`, a space, a stray character), not a wrong but valid ID: only the
 * payer's UPI app knows whose an ID is.
 */
fun isUpiId(id: String): Boolean {
    val trimmed = id.trim()
    return trimmed.length <= UPI_ID_MAX && UPI_ID_SHAPE.matches(trimmed)
}

/** A UPI ID as it is stored: trimmed, with nothing else changed. */
fun normaliseUpiId(id: String): String = id.trim()

/**
 * A `upi://pay` link for paying [payeeId] [amountMinor] paise: what Show QR and a reminder's QR
 * carry.
 *
 * `pa` is the ID, `pn` the payee's name when known, `am` the amount in rupees with two decimals and
 * `cu` always INR (UPI is rupees only). No `tn`: no default text and no app name, since a note lands
 * in the payer's UPI history and bank statement (Parth, 14 Nov 2026). Every value is
 * percent-encoded, so a name cannot add parameters of its own.
 */
fun upiPayLink(payeeId: String, payeeName: String?, amountMinor: Long): String {
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
    }
}

/**
 * Whether "Show QR to pay you", and a reminder's QR, are offered: for someone who owes you, kept in
 * rupees, once your own UPI ID is set.
 */
fun canShowPayQr(currency: String, balanceMinor: Long, myUpiId: String?): Boolean =
    canOfferPayQr(currency, balanceMinor) && myUpiId != null && isUpiId(myUpiId)

/**
 * Whether "Show QR to pay you" is in someone's menu (v3.7.1): for anyone who owes you rupees, your
 * UPI ID set or not. Without one, it asks for it first, then shows the QR, so the feature can be
 * found without going to Settings.
 */
fun canOfferPayQr(currency: String, balanceMinor: Long): Boolean =
    normaliseCurrency(currency) == "INR" && balanceMinor > 0

/**
 * A `upi://pay` link to [payeeId] with no amount: what your own QR carries, so whoever scans it
 * types the amount in their own UPI app.
 */
fun upiReceiveLink(payeeId: String, payeeName: String?): String {
    require(isUpiId(payeeId)) { "not a UPI ID" }
    return buildString {
        append("upi://pay?pa=").append(percentEncode(normaliseUpiId(payeeId), keep = "@"))
        payeeName?.trim()?.take(UPI_NAME_MAX)?.takeIf { it.isNotEmpty() }?.let {
            append("&pn=").append(percentEncode(it))
        }
        append("&cu=INR")
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
