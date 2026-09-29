package com.kg.merapaisa.ui.format

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Text
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.amountBlankFractionSpan
import com.kg.merapaisa.ui.theme.amountSymbolSpan
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp

/**
 * Every amount the user sees.
 *
 * Nothing draws money any other way. `formatMinor` still exists for the text this app *sends*, a
 * share summary or a reminder, where a plain string is the whole point; on screen it cannot
 * express any of what follows.
 *
 * Four things happen here that a plain string cannot do:
 *
 *  - The direction is written, not only coloured. "+" when they owe you, a real U+2212 when you
 *    owe them, nothing at zero. Colour alone fails anyone who cannot separate the two inks.
 *  - The symbol is set smaller and quieter than the digits, on the same baseline, so reading a
 *    column means reading figures rather than a row of rupee signs.
 *  - Figures are tabular, so a column lines up, and [columnAligned] keeps the decimal slot open
 *    on whole amounts so the decimal points line up too.
 *  - TalkBack is given words instead of glyphs, since it has no name for U+2212 and will either
 *    skip it or read "minus sign" in the middle of a figure.
 */
@Composable
fun AmountText(
    amountMinor: Long,
    currencyCode: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MeraPaisaType.amount,
    signStyle: SignStyle = SignStyle.Always,
    /** Colour the figure by direction. Off where a row already carries the meaning some other way. */
    colourByDirection: Boolean = true,
    /** Keep the decimal slot open on whole amounts, so a column's decimal points line up. */
    columnAligned: Boolean = false,
    /** The person or member this belongs to, so TalkBack can say whose it is. */
    spokenOwner: String? = null,
    textAlign: TextAlign = TextAlign.End
) {
    val theme = LocalAppTheme.current
    val parts = amountParts(amountMinor, currencyCode, signStyle)

    val colour = when {
        !colourByDirection -> theme.textPrimary
        amountMinor > 0 -> theme.positive
        amountMinor < 0 -> theme.negative
        else -> theme.textSecondary
    }

    val text = buildAnnotatedString {
        append(parts.sign)
        withStyle(amountSymbolSpan(theme.textSecondary)) { append(parts.symbol) }
        append(parts.integer)
        when {
            parts.fraction.isNotEmpty() -> append(".${parts.fraction}")
            // Same width as a real fraction, drawn in nothing, so the column stays square.
            columnAligned && hasMinorUnit(currencyCode) ->
                withStyle(amountBlankFractionSpan()) { append(".00") }
        }
    }

    Text(
        text = text,
        style = style,
        color = colour,
        textAlign = textAlign,
        maxLines = 1,
        autoSize = shrinkToFit(style),
        modifier = modifier.clearAndSetSemantics {
            contentDescription = amountSpoken(amountMinor, currencyCode, spokenOwner)
        }
    )
}

/**
 * The same figure with no colour and no sign, for places where a sentence beside it already says
 * which way the money runs and a second signal would just be noise.
 */
@Composable
fun PlainAmountText(
    amountMinor: Long,
    currencyCode: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MeraPaisaType.amountSmall,
    colour: Color = LocalAppTheme.current.textPrimary,
    textAlign: TextAlign = TextAlign.End
) {
    val theme = LocalAppTheme.current
    val parts = amountParts(amountMinor, currencyCode, SignStyle.None)
    val text = buildAnnotatedString {
        withStyle(amountSymbolSpan(theme.textSecondary)) { append(parts.symbol) }
        append(parts.digits)
    }
    Text(
        text = text,
        style = style,
        color = colour,
        textAlign = textAlign,
        maxLines = 1,
        autoSize = shrinkToFit(style),
        modifier = modifier
    )
}

/**
 * Reserves the height of an amount without drawing one, so a row keeps its shape while a value is
 * still being worked out. Used instead of a spinner, which would be motion that explains nothing.
 */
@Composable
fun AmountPlaceholder(modifier: Modifier = Modifier, style: TextStyle = MeraPaisaType.amount) {
    Box(modifier) {
        Text(
            text = " ",
            style = style,
            color = Color.Transparent,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

private fun hasMinorUnit(currencyCode: String): Boolean =
    com.kg.merapaisa.data.currencyDecimals(com.kg.merapaisa.data.normaliseCurrency(currencyCode)) > 0

/**
 * The last line of defence against a clipped figure.
 *
 * Rows already give the figure its full width before the words beside it (see LabelAndAmount), so
 * this only acts when a figure is wider than the whole row: a lakh amount in the hero style at the
 * largest font size. Clipped, that read as a lone "+". Shrunk, it stays a number, and nothing
 * smaller than about half its size is ever drawn.
 */
private fun shrinkToFit(style: TextStyle) =
    TextAutoSize.StepBased(minFontSize = style.fontSize * 0.5f, maxFontSize = style.fontSize, stepSize = 1.sp)
