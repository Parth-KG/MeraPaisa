package com.kg.merapaisa.ui.format

import com.kg.merapaisa.data.currencyDecimals
import com.kg.merapaisa.data.currencySymbol
import com.kg.merapaisa.data.normaliseCurrency

/**
 * How an amount is broken up for the screen.
 *
 * Kept as parts rather than one string because the parts are set differently: the symbol is
 * smaller and quieter than the digits, and a column has to be able to reserve the decimal slot
 * even for a whole number so the decimal points line up down the page. A single string cannot
 * express either, which is why `formatMinor` is not used on screen.
 *
 * [sign] is the real minus, U+2212, not a hyphen. A hyphen is a word-joiner: it is drawn shorter
 * and higher than the minus in most faces, and next to a tabular digit it reads as a dash rather
 * than as a direction.
 */
data class AmountParts(
    /** The minus and a thin space on what you owe; empty otherwise. */
    val sign: String,
    /** "₹", "$", or the code itself when there is no symbol for it. */
    val symbol: String,
    /** Grouped digits: "12,34,567" for INR, "1,234,567" for everything else. */
    val integer: String,
    /** Two digits, or empty when the amount is whole or the currency has no minor unit. */
    val fraction: String
) {
    /** Everything joined, for a spoken description or a plain-text message. */
    val plain: String get() = sign + symbol + integer + if (fraction.isEmpty()) "" else ".$fraction"

    /** The digits alone, for laying out a column. */
    val digits: String get() = integer + if (fraction.isEmpty()) "" else ".$fraction"
}

/** How much of the sign to show. */
enum class SignStyle {
    /**
     * The minus on what you owe, and nothing on what you are owed or at zero. The default.
     *
     * There used to be a "+" on what you are owed, which read as arithmetic rather than as money,
     * and which nobody writes in an account book. The ink and the words beside a figure ("owes
     * you") carry the direction, and a figure with no minus is money owed to you.
     */
    Always,

    /** The same as [Always], kept so call sites that asked for it explicitly keep compiling. */
    NegativeOnly,

    /** No sign at all. Only where a word beside it already carries the direction. */
    None
}

/**
 * Splits an amount into the parts a screen needs.
 *
 * Grouping is done by hand rather than through java.text or android.icu on purpose. A desktop JVM
 * groups INR in threes, so a unit test would agree with itself and disagree with the phone, and
 * the lakh grouping is the whole point of getting this right for this app.
 *
 * [forceFraction] keeps the decimal slot occupied even when the amount is whole, so that a column
 * mixing 1,200 and 237.40 still lines its decimal points up. Off by default: a lone amount with
 * ".00" hanging off it is noise.
 */
fun amountParts(
    amountMinor: Long,
    currencyCode: String,
    signStyle: SignStyle = SignStyle.Always,
    forceFraction: Boolean = false
): AmountParts {
    val code = normaliseCurrency(currencyCode)
    val decimals = currencyDecimals(code)

    // A real minus (U+2212) and a thin space (U+2009) after it. Set tight against the rupee sign
    // the two read as one glyph; a full space pushed the minus away from its figure.
    val sign = when {
        signStyle == SignStyle.None -> ""
        amountMinor < 0 -> MINUS
        else -> ""
    }

    // Magnitude via unsigned division, because Long.MIN_VALUE has no positive counterpart and
    // negating it leaves it negative. Every other value divides identically either way.
    val magnitude = if (amountMinor < 0) -amountMinor else amountMinor

    val whole: Long
    val hundredths: Long
    if (decimals == 0) {
        // Still stored in hundredths, so round to the nearest whole major unit for display.
        whole = java.lang.Long.divideUnsigned(magnitude + 50, 100)
        hundredths = 0
    } else {
        whole = java.lang.Long.divideUnsigned(magnitude, 100)
        hundredths = java.lang.Long.remainderUnsigned(magnitude, 100)
    }

    val fraction = when {
        decimals == 0 -> ""
        hundredths != 0L -> hundredths.toString().padStart(2, '0')
        forceFraction -> "00"
        else -> ""
    }

    return AmountParts(
        sign = sign,
        symbol = currencySymbol(code),
        integer = group(whole.toString(), lakhs = code == "INR"),
        fraction = fraction
    )
}

/**
 * Indian grouping puts the last three digits together and then pairs off to the left, so ten
 * million reads as 1,00,00,000 rather than 10,000,000. Everything else groups in threes.
 */
private fun group(digits: String, lakhs: Boolean): String {
    if (digits.length <= 3) return digits
    if (!lakhs) {
        return buildString {
            val firstGroup = digits.length % 3
            if (firstGroup > 0) {
                append(digits, 0, firstGroup)
                if (digits.length > firstGroup) append(',')
            }
            var i = firstGroup
            while (i < digits.length) {
                append(digits, i, i + 3)
                i += 3
                if (i < digits.length) append(',')
            }
        }
    }
    val last3 = digits.substring(digits.length - 3)
    val rest = digits.substring(0, digits.length - 3)
    return buildString {
        val firstGroup = rest.length % 2
        if (firstGroup > 0) {
            append(rest, 0, firstGroup)
            append(',')
        }
        var i = firstGroup
        while (i < rest.length) {
            append(rest, i, i + 2)
            i += 2
            append(',')
        }
        append(last3)
    }
}

/**
 * An amount as one string, for the places a composable cannot go.
 *
 * A sentence with a figure in the middle of it, a Glance widget, a message the user is about to
 * send. Everywhere else on screen uses `AmountText`, which can set the symbol apart from the
 * digits and keep a column square, neither of which a string can express.
 *
 * Defaults to showing only the minus, because these nearly always sit next to words that already
 * say which way the money runs, and a leading "+" inside a sentence reads as an operator.
 */
fun amountString(
    amountMinor: Long,
    currencyCode: String,
    signStyle: SignStyle = SignStyle.NegativeOnly
): String = amountParts(amountMinor, currencyCode, signStyle).plain

/**
 * How an amount should be read aloud.
 *
 * TalkBack has no name for U+2212 and will either skip it or say "minus sign" mid-figure, so the
 * direction has to be carried by words instead. [name] is the person or member the amount belongs
 * to; leave it null for a total.
 */
fun amountSpoken(amountMinor: Long, currencyCode: String, name: String? = null): String {
    val parts = amountParts(amountMinor, currencyCode, SignStyle.None)
    val code = normaliseCurrency(currencyCode)
    val unit = SPOKEN_UNITS[code] ?: code
    val figure = "${parts.digits} $unit"
    return when {
        amountMinor == 0L && name != null -> "$name, even"
        amountMinor == 0L -> "even"
        amountMinor > 0 && name != null -> "$name owes you $figure"
        amountMinor > 0 -> "owed to you, $figure"
        name != null -> "you owe $name $figure"
        else -> "you owe $figure"
    }
}

/** Spoken names, because "₹" is read as "rupee sign" or skipped entirely. */
private val SPOKEN_UNITS = mapOf(
    "INR" to "rupees",
    "USD" to "dollars",
    "EUR" to "euros",
    "GBP" to "pounds",
    "JPY" to "yen"
)

/** The minus that leads what you owe: U+2212 and a thin space. */
const val MINUS = "\u2212\u2009"
