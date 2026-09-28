package com.kg.merapaisa

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * The selected palette. Material components read the [ColorScheme] built from it, so this local
 * only has to carry what M3 has no slot for: the semantic positive/negative pair.
 */
val LocalAppTheme = staticCompositionLocalOf { themes[0] }

data class AppTheme(
    val name: String,
    val background: Color,
    val surface: Color,
    val card: Color,
    val primary: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val positive: Color,
    val negative: Color
) {
    val isDark: Boolean get() = background.luminance() < 0.4f

    /** Fill for rows, numpad keys and chips — a step away from the background, not white-on-white. */
    val fill: Color get() = card

    /** The same fill one step stronger, for selected and pressed states. */
    val fillStrong: Color get() = lerp(card, textPrimary, 0.10f)

    /** Hairline borders and dividers. */
    val outline: Color get() = lerp(card, textPrimary, 0.18f)
}

/**
 * Six worlds, not one palette rotated six times.
 *
 * What was here before was a single neutral ramp recoloured: Ocean, Sunset and Purple had
 * identical saturation and value and differed only in hue, and Sunset's background was Ocean's
 * with the RGB bytes reversed. Ten of the twelve accents were stock swatches, straight off the
 * Material 2014 and Flat UI sheets, and in every theme the accent and the "owed to you" ink were
 * the same colour, so a button and a credit balance could not be told apart.
 *
 * Each theme now has its own neutral ramp in its own hue and temperature, its own accent for
 * actions, and its own pair of inks for amounts. The accent is never an amount colour.
 *
 * Contrast is not guesswork: ThemeInkContrastTest holds every one of these to 4.5:1 for text and
 * for both amount inks, on background, surface and card, and checks the label on an accent button.
 * Do not adjust a value here by eye.
 */
val themes = listOf(
    // A desk lamp at night. Deep blue-grey, lifted off black so surfaces can step up, warm
    // off-white text, and a marigold accent that stays rare enough to mean something.
    AppTheme(
        name = "Midnight",
        background = Color(0xFF171C24), surface = Color(0xFF1E242E), card = Color(0xFF242B36),
        primary = Color(0xFFE8A33D), secondary = Color(0xFFB9A98C),
        textPrimary = Color(0xFFF2EFE9), textSecondary = Color(0xFFA2AAB6),
        positive = Color(0xFF6FBF8B), negative = Color(0xFFE8736A)
    ),
    // True black with a monochrome ramp, for OLED. The accent is the off-white itself, so a
    // button is a light slab with black type on it. Only the amount inks carry any colour, and
    // they are muted: nothing here is allowed to glow.
    AppTheme(
        name = "Amoled",
        background = Color(0xFF000000), surface = Color(0xFF0B0B0B), card = Color(0xFF141414),
        primary = Color(0xFFEDEDED), secondary = Color(0xFF8A8A8A),
        textPrimary = Color(0xFFEDEDED), textSecondary = Color(0xFF9A9A9A),
        positive = Color(0xFF7FB693), negative = Color(0xFFD98A84)
    ),
    // Monsoon over the Arabian Sea: a green-grey slate ramp under a sea-glass accent. The accent
    // leans blue so it stays clearly apart from the green used for money owed to you.
    AppTheme(
        name = "Ocean",
        background = Color(0xFF12201F), surface = Color(0xFF172827), card = Color(0xFF1D302E),
        primary = Color(0xFF5AB9D4), secondary = Color(0xFF7FA0A8),
        textPrimary = Color(0xFFE8EFEC), textSecondary = Color(0xFF93A8A3),
        positive = Color(0xFF5FB88A), negative = Color(0xFFD98266)
    ),
    // Dusk rather than espresso: a dusky plum-brown ramp, a dusk-rose accent, and a cool blue
    // standing in for red on amounts you owe, because a warm red on this ground reads as part of
    // the background rather than as a warning.
    AppTheme(
        name = "Sunset",
        background = Color(0xFF241A20), surface = Color(0xFF2E2229), card = Color(0xFF382A31),
        primary = Color(0xFFE0899A), secondary = Color(0xFFB79AA6),
        textPrimary = Color(0xFFF2E8EA), textSecondary = Color(0xFFB49AA3),
        positive = Color(0xFFA8C08A), negative = Color(0xFF7FA6D9)
    ),
    // Jamun, not lavender: a deep aubergine ramp under the magenta of the fruit's skin.
    AppTheme(
        name = "Purple",
        background = Color(0xFF1B1220), surface = Color(0xFF241829), card = Color(0xFF2C1E33),
        primary = Color(0xFFCE77BE), secondary = Color(0xFF9B7FA8),
        textPrimary = Color(0xFFEFE6F0), textSecondary = Color(0xFFAE9CB5),
        positive = Color(0xFF7FC49A), negative = Color(0xFFE07A6E)
    ),
    // A fresh page in a register: cool white paper rather than cream, blue-black ink, a deep
    // ink-blue accent, and the two inks a ledger is actually kept in, bottle green and register
    // red. The flagship, and the only light theme.
    AppTheme(
        name = "Paper",
        background = Color(0xFFF4F6F8), surface = Color(0xFFFFFFFF), card = Color(0xFFE7ECF1),
        primary = Color(0xFF1B3A6B), secondary = Color(0xFF43607F),
        textPrimary = Color(0xFF141920), textSecondary = Color(0xFF59636E),
        positive = Color(0xFF1B5E3F), negative = Color(0xFF9E2A2B)
    )
)

/**
 * Themes that used to exist, pointed at whichever survivor they were closest to. Someone who
 * chose Slate gets Ocean rather than being silently reset to the default, and their stored
 * preference is only rewritten when they next pick a theme themselves.
 */
private val RETIRED_THEMES = mapOf(
    "Slate" to "Ocean",       // near-identical: perceptual distance of 7
    "Charcoal" to "Sunset",
    "Gold" to "Sunset",
    "Cream" to "Paper",
    "Rose" to "Purple",
    "Vibrant" to "Purple",
    "Neon" to "Amoled"
)

fun getThemeByName(name: String): AppTheme {
    val resolved = RETIRED_THEMES[name] ?: name
    return themes.find { it.name == resolved } ?: themes[0]
}

/**
 * Every Material component pulls its colours from here, so the app and the M3 defaults finally
 * agree and per-component `colors = ...` overrides are no longer needed to undo a purple scheme.
 */
fun AppTheme.toColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = primary,
        onPrimary = background,
        primaryContainer = fillStrong,
        onPrimaryContainer = textPrimary,
        inversePrimary = background,
        secondary = secondary,
        onSecondary = background,
        secondaryContainer = lerp(card, primary, 0.22f),
        onSecondaryContainer = primary,
        tertiary = primary,
        onTertiary = background,
        tertiaryContainer = fillStrong,
        onTertiaryContainer = textPrimary,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = card,
        onSurfaceVariant = textSecondary,
        surfaceTint = primary,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        error = negative,
        onError = background,
        errorContainer = lerp(card, negative, 0.25f),
        onErrorContainer = negative,
        outline = outline,
        outlineVariant = lerp(card, textPrimary, 0.10f),
        scrim = Color.Black,
        surfaceBright = fillStrong,
        surfaceDim = background,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = fillStrong,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = background,
        primaryContainer = fillStrong,
        onPrimaryContainer = textPrimary,
        inversePrimary = background,
        secondary = secondary,
        onSecondary = background,
        secondaryContainer = lerp(card, primary, 0.22f),
        onSecondaryContainer = primary,
        tertiary = primary,
        onTertiary = background,
        tertiaryContainer = fillStrong,
        onTertiaryContainer = textPrimary,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = card,
        onSurfaceVariant = textSecondary,
        surfaceTint = primary,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        error = negative,
        onError = background,
        errorContainer = lerp(card, negative, 0.25f),
        onErrorContainer = negative,
        outline = outline,
        outlineVariant = lerp(card, textPrimary, 0.10f),
        scrim = Color.Black,
        surfaceBright = background,
        surfaceDim = fillStrong,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = fillStrong,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background
    )
}
