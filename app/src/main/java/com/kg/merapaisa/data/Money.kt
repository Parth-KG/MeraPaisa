package com.kg.merapaisa.data

import kotlin.math.absoluteValue

/**
 * Money is stored as a [Long] count of minor units — hundredths of the major unit — for
 * every currency, including the zero-decimal ones. Zero-decimal display is handled here,
 * at the formatting layer, so the stored representation stays uniform.
 */

/** ISO 4217 codes this app offers, in display order. */
val SUPPORTED_CURRENCIES = listOf("INR", "USD", "EUR", "GBP", "JPY")

private val CURRENCY_SYMBOLS = mapOf(
    "INR" to "₹",
    "USD" to "$",
    "EUR" to "€",
    "GBP" to "£",
    "JPY" to "¥"
)

/** Symbols that older versions stored in place of a code, kept for reading legacy state. */
private val LEGACY_SYMBOL_TO_CODE = mapOf(
    "₹" to "INR",
    "$" to "USD",
    "€" to "EUR",
    "£" to "GBP",
    "¥" to "JPY"
)

/** Currencies with no minor unit in circulation, shown without decimals. */
private val ZERO_DECIMAL_CURRENCIES = setOf("JPY")

fun currencySymbol(code: String): String = CURRENCY_SYMBOLS[code] ?: code

fun currencyDecimals(code: String): Int = if (code in ZERO_DECIMAL_CURRENCIES) 0 else 2

/** Normalises a legacy symbol to its ISO code; passes codes through untouched. */
fun normaliseCurrency(value: String): String = LEGACY_SYMBOL_TO_CODE[value] ?: value

/**
 * Parses user-entered decimal text into minor units, or null if it is not a usable amount.
 * Parsing works on the digits directly rather than through [Double] so that a half at the
 * third decimal rounds predictably away from zero instead of following binary float error.
 */
fun parseAmountToMinor(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    val negative = trimmed.startsWith("-")
    val unsigned = if (negative) trimmed.substring(1) else trimmed
    if (unsigned.isEmpty()) return null

    val dot = unsigned.indexOf('.')
    if (dot != unsigned.lastIndexOf('.')) return null

    val whole = if (dot < 0) unsigned else unsigned.substring(0, dot)
    val fraction = if (dot < 0) "" else unsigned.substring(dot + 1)

    // A lone "." carries no digits and is not an amount.
    if (whole.isEmpty() && fraction.isEmpty()) return null
    if (!whole.all { it.isDigit() } || !fraction.all { it.isDigit() }) return null
    // Guard against input long enough to overflow the multiply below.
    if (whole.length > 15) return null

    val wholeValue = if (whole.isEmpty()) 0L else whole.toLong()
    val hundredths = (fraction + "00").substring(0, 2).toLong()
    var minor = wholeValue * 100 + hundredths

    // Round half away from zero on the first discarded digit.
    val next = fraction.getOrNull(2)
    if (next != null && next.digitToInt() >= 5) minor += 1

    return if (negative) -minor else minor
}

/**
 * Renders minor units with the currency's symbol, using its real number of decimals.
 *
 * A round amount is shown without them: most entries are whole rupees, and a column of
 * ".00" is noise that makes the amounts that *do* have paise harder to pick out.
 */
/**
 * The magnitude of an amount, as bits, safe for the one value that has no positive counterpart.
 *
 * `Long.MIN_VALUE.absoluteValue` is still `Long.MIN_VALUE`, so dividing it by 100 gives a negative
 * body behind a minus sign: `-₹-92233720368547758.-8`. Negating it here leaves the same bit
 * pattern, which read as unsigned is exactly 2^63 — the true magnitude — so unsigned division
 * renders it correctly rather than approximately. Every other value divides identically either way.
 *
 * It cannot arrive from the keypad or a share link, both of which are bounded well below this. A
 * restored backup is read straight from a file, and a figure the app cannot render is a worse
 * answer than one it can.
 */
private inline fun magnitudeOf(amountMinor: Long): Long = if (amountMinor < 0) -amountMinor else amountMinor

private inline fun wholeUnits(magnitude: Long): Long = java.lang.Long.divideUnsigned(magnitude, 100)

private inline fun paiseOf(magnitude: Long): Long = java.lang.Long.remainderUnsigned(magnitude, 100)

private inline fun roundedWholeUnits(magnitude: Long): Long =
    java.lang.Long.divideUnsigned(magnitude + 50, 100)

fun formatMinor(amountMinor: Long, currencyCode: String): String {
    val symbol = currencySymbol(currencyCode)
    val sign = if (amountMinor < 0) "-" else ""
    val magnitude = magnitudeOf(amountMinor)

    val body = if (currencyDecimals(currencyCode) == 0) {
        // Still stored in hundredths, so round to the nearest whole major unit for display.
        roundedWholeUnits(magnitude).toString()
    } else {
        // Paise keep both digits, 237.40 and never 237.4, which reads as a different number. Only
        // a whole ".00" says nothing, so 237.00 is 237.
        val fraction = paiseOf(magnitude).toString().padStart(2, '0').let { if (it == "00") "" else it }
        if (fraction.isEmpty()) {
            wholeUnits(magnitude).toString()
        } else {
            "${wholeUnits(magnitude)}.$fraction"
        }
    }
    return "$sign$symbol$body"
}

/**
 * Digits only, no symbol — for text fields the user types back into.
 *
 * [trimZeros] drops decimals that carry no information, so an editable field agrees with the
 * amounts [formatMinor] shows everywhere else. The CSV leaves it off: a column that is always
 * two decimals wide is worth more to a spreadsheet than it is to a reader.
 */
fun formatMinorPlain(amountMinor: Long, currencyCode: String, trimZeros: Boolean = false): String {
    val magnitude = magnitudeOf(amountMinor)
    val sign = if (amountMinor < 0) "-" else ""
    val body = if (currencyDecimals(currencyCode) == 0) {
        roundedWholeUnits(magnitude).toString()
    } else {
        val paise = paiseOf(magnitude).toString().padStart(2, '0')
        val fraction = if (trimZeros) paise.trimEnd('0') else paise
        if (fraction.isEmpty()) wholeUnits(magnitude).toString()
        else "${wholeUnits(magnitude)}.$fraction"
    }
    return "$sign$body"
}

/** Most digits allowed before the decimal point, which also bounds what [parseAmountToMinor] sees. */
private const val MAX_WHOLE_DIGITS = 9

/**
 * Applies one numpad key to the current entry. Enforces a single decimal point, at most two
 * decimal places and one leading zero, so the field cannot reach a state like "00000000" or
 * "." that no longer parses.
 */
fun appendAmountKey(current: String, key: String): String = when {
    key == "⌫" -> current.dropLast(1)

    key == "." -> when {
        current.contains('.') -> current
        current.isEmpty() -> "0."
        else -> "$current."
    }

    key.length == 1 && key[0].isDigit() -> {
        val dot = current.indexOf('.')
        when {
            // A lone leading zero is replaced rather than extended.
            current == "0" -> key
            dot < 0 && current.length >= MAX_WHOLE_DIGITS -> current
            dot >= 0 && current.length - dot > 2 -> current
            else -> current + key
        }
    }

    else -> current
}

/**
 * Whether [text] may stand in a typed amount field for [currencyCode]: digits, at most
 * [MAX_WHOLE_DIGITS] of them before the point, and only as many after it as the currency has.
 * The keypads enforce the same through [appendAmountKey]; a text field needs it said. Without it,
 * "12.5" yen was accepted, stored as 1250 hundredths and shown as ¥13.
 */
fun isTypableAmount(text: String, currencyCode: String, allowNegative: Boolean = false): Boolean {
    val body = if (allowNegative) text.removePrefix("-").removePrefix("\u2212") else text
    val whole = body.substringBefore('.')
    if (whole.length > MAX_WHOLE_DIGITS || !whole.all { it.isDigit() }) return false
    if ('.' !in body) return true
    val fraction = body.substringAfter('.')
    return currencyDecimals(currencyCode) > 0 && fraction.length <= 2 && fraction.all { it.isDigit() }
}

/** True when the entry is a usable, non-zero amount the +/- buttons can act on. */
fun isUsableAmount(text: String): Boolean {
    val minor = parseAmountToMinor(text)
    return minor != null && minor != 0L
}

/**
 * The figure for a line of the shared summary.
 *
 * It used to carry a "+" on what they owe you, because plain text has no colour to read. It follows
 * the screen now: what you owe carries the minus, what they owe you carries nothing, and the
 * headline above the lines says which way the whole balance runs.
 */
fun formatSignedAmount(amountMinor: Long, currencyCode: String): String =
    formatMinor(amountMinor, currencyCode)

/**
 * Converts a whole list of amounts at one rate, keeping the parts consistent with the total.
 *
 * Changing someone's currency rewrites every entry in their history, because a log that mixes
 * rupees and dollars with a correcting adjustment at the bottom is not a history anyone can read.
 * That creates a problem the naive version gets wrong: rounding each entry independently gives a
 * set of parts that need not add up to the rounded total. Convert 33.33 three times at 2.0 and you
 * get 66.66 three times — 199.98 — while the balance of 99.99 converts to 199.98. Those agree here,
 * but at other rates they do not, and the entries would then contradict the balance derived from
 * them.
 *
 * So the total is converted once and treated as authoritative, and any residue is absorbed into the
 * largest-magnitude entry — the one where a paise is least visible. Same principle as [evenShares]
 * in SettleUp.kt, which gives leftover minor units to the earliest members rather than losing them.
 *
 * An empty list converts to an empty list. A list summing to zero stays summing to zero, so a
 * settled person stays settled.
 */
fun convertAll(amounts: List<Long>, rate: Double): List<Long> {
    if (amounts.isEmpty()) return emptyList()

    val converted = amounts.map { Math.round(it * rate) }.toMutableList()
    val target = Math.round(amounts.sum() * rate)
    val residue = target - converted.sum()

    if (residue != 0L) {
        // Index of the largest magnitude; ties go to the earliest entry so the result is stable.
        var pick = 0
        for (i in converted.indices) {
            if (Math.abs(converted[i]) > Math.abs(converted[pick])) pick = i
        }
        converted[pick] += residue
    }
    return converted
}
