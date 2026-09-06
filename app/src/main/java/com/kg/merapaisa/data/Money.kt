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
fun formatMinor(amountMinor: Long, currencyCode: String): String {
    val symbol = currencySymbol(currencyCode)
    val sign = if (amountMinor < 0) "-" else ""
    val magnitude = amountMinor.absoluteValue

    val body = if (currencyDecimals(currencyCode) == 0) {
        // Still stored in hundredths, so round to the nearest whole major unit for display.
        ((magnitude + 50) / 100).toString()
    } else {
        // Trailing zeros carry no information: 237.40 reads as 237.4, and 237.00 as 237.
        val paise = magnitude % 100
        val fraction = paise.toString().padStart(2, '0').trimEnd('0')
        if (fraction.isEmpty()) {
            (magnitude / 100).toString()
        } else {
            "${magnitude / 100}.$fraction"
        }
    }
    return "$sign$symbol$body"
}

/** Digits only, no symbol — for text fields the user types back into. */
fun formatMinorPlain(amountMinor: Long, currencyCode: String): String {
    val magnitude = amountMinor.absoluteValue
    val sign = if (amountMinor < 0) "-" else ""
    val body = if (currencyDecimals(currencyCode) == 0) {
        ((magnitude + 50) / 100).toString()
    } else {
        "${magnitude / 100}.${(magnitude % 100).toString().padStart(2, '0')}"
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

/** True when the entry is a usable, non-zero amount the +/- buttons can act on. */
fun isUsableAmount(text: String): Boolean {
    val minor = parseAmountToMinor(text)
    return minor != null && minor != 0L
}

/**
 * Like [formatMinor] but always carries an explicit sign, so which way a debt runs does not
 * depend on telling green from red.
 */
fun formatSignedAmount(amountMinor: Long, currencyCode: String): String =
    if (amountMinor > 0) "+${formatMinor(amountMinor, currencyCode)}" else formatMinor(amountMinor, currencyCode)
