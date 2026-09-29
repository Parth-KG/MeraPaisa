package com.kg.merapaisa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.R

/**
 * Two faces, eight styles, and one rule: amounts are set differently from everything else.
 *
 * Anek Latin is by Ek Type, a Mumbai foundry, and it draws a proper ₹ rather than an R with a bar
 * bolted on. It sets the amounts and the screen titles. Figtree does everything else, because a
 * ledger should be quiet everywhere the numbers are not.
 *
 * Static instances, not variable fonts: API 24 and 25 ignore variation axes, so a variable file
 * would render at its default weight on those phones and every amount would silently stop being
 * bold. `scripts/build-fonts.sh` rebuilds them.
 */

/** Normal width, for screen titles, where the condensed cut looks squeezed at large sizes. */
private val Anek = FontFamily(
    Font(R.font.anek_latin_semibold, FontWeight.SemiBold)
)

/**
 * Semicondensed, for amounts only.
 *
 * A lakh figure with paise is fifteen characters. At full width it either shrinks or wraps, and
 * both are worse than setting it slightly narrower at full weight.
 */
private val AnekAmount = FontFamily(
    Font(R.font.anek_latin_semicondensed_semibold, FontWeight.SemiBold),
    Font(R.font.anek_latin_semicondensed_bold, FontWeight.Bold)
)

private val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold)
)

/**
 * Tabular figures, so digits sit in a fixed-width cell and a column of amounts lines up.
 *
 * `fontFeatureSettings` replaces rather than merges, so every feature an amount needs has to be in
 * this one string. Adding a second call elsewhere silently drops this one.
 */
private const val AMOUNT_FEATURES = "tnum"

/**
 * The app's styles. Screens name one of these; nothing sets a size itself.
 */
object MeraPaisaType {

    /** The net position, and nothing else. The largest thing on any screen. */
    val amountHero = TextStyle(
        fontFamily = AnekAmount,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = AMOUNT_FEATURES
    )

    /** An amount in a row: a person's balance, a group position, an entry. */
    val amount = TextStyle(
        fontFamily = AnekAmount,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = AMOUNT_FEATURES
    )

    /** A secondary amount: a share, a running balance, an amount inside a sentence. */
    val amountSmall = TextStyle(
        fontFamily = AnekAmount,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = AMOUNT_FEATURES
    )

    /** The title of a screen or a sheet. */
    val screenTitle = TextStyle(
        fontFamily = Anek,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp
    )

    /** A heading inside a screen. Sentence case, never an ALL-CAPS eyebrow. */
    val sectionTitle = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )

    /** Ordinary text: a person's name, a note, a sentence in a dialog. */
    val body = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )

    /** The same size, carrying weight: the name in a row that also shows a balance. */
    val bodyStrong = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )

    /** Supporting text under a name, a date, a currency code. */
    val label = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )

    /** A button, a tab, anything the user taps that is made of words. */
    val action = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    )
}

/**
 * Initials inside an avatar, sized to the avatar rather than to the reader's font scale.
 *
 * An avatar is a fixed-size picture, not reading text: the name beside it is the thing that grows
 * with the system font size. Sized in sp the usual way, the initials doubled at font scale 2 and
 * spilled out of a 40dp circle. Converting from dp keeps them inside it at any setting.
 */
@Composable
fun avatarInitialsStyle(avatarSizeDp: Int): TextStyle {
    val size = with(LocalDensity.current) { (avatarSizeDp * 0.36f).dp.toSp() }
    return TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.SemiBold,
        fontSize = size,
        lineHeight = size,
        letterSpacing = 0.sp
    )
}

/** An emoji chosen as a person's picture, held to the avatar the same way. */
@Composable
fun avatarEmojiStyle(avatarSizeDp: Int): TextStyle {
    val size = with(LocalDensity.current) { (avatarSizeDp * 0.45f).dp.toSp() }
    return TextStyle(fontSize = size, lineHeight = size)
}

/**
 * The currency symbol, set against the digits it belongs to.
 *
 * Smaller and quieter, because "₹" is a unit rather than part of the figure, and reading a column
 * of amounts should mean reading the digits. It keeps the same baseline: raising it into a
 * superscript is a price-tag mannerism and makes a ledger look like a shop window.
 *
 * Built here rather than at the call site so that the one relative size lives with the rest of the
 * type scale.
 */
fun amountSymbolSpan(colour: Color) = SpanStyle(fontSize = 0.7.em, color = colour)

/**
 * The slot a whole amount leaves open in a column, so the decimal points below it still line up.
 *
 * Drawn rather than padded: with tabular figures ".00" is exactly as wide as any other fraction,
 * so rendering it transparent reserves precisely the right space without measuring anything.
 */
fun amountBlankFractionSpan() = SpanStyle(color = Color.Transparent)

/**
 * The same styles handed to Material, so an unstyled component picks up the app's type instead of
 * falling back to the platform sans. Nothing in the app reads these slots directly.
 */
val Typography = Typography(
    displaySmall = MeraPaisaType.amountHero,
    headlineSmall = MeraPaisaType.screenTitle,
    titleLarge = MeraPaisaType.screenTitle,
    titleMedium = MeraPaisaType.sectionTitle,
    titleSmall = MeraPaisaType.sectionTitle,
    bodyLarge = MeraPaisaType.body,
    bodyMedium = MeraPaisaType.body,
    bodySmall = MeraPaisaType.label,
    labelLarge = MeraPaisaType.action,
    labelMedium = MeraPaisaType.label,
    labelSmall = MeraPaisaType.label
)
