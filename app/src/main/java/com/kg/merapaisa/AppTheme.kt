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

    /** Fill for rows, numpad keys and chips: a step away from the background, not white on white. */
    val fill: Color get() = card

    /** The same fill one step stronger, for selected and pressed states. */
    val fillStrong: Color get() = lerp(card, textPrimary, 0.10f)

    /** Hairline borders and dividers. */
    val outline: Color get() = lerp(card, textPrimary, 0.18f)
}

/**
 * Midnight, the app's own theme, and Catppuccin's four flavours.
 *
 * Catppuccin (github.com/catppuccin/palette, v1.8.0, MIT) is one palette at four depths, from
 * Latte by day to Mocha at night, so unlike the themes it replaced, its flavours are meant to look
 * related. The page and the sheets are each flavour's base, and rows, keys and menus sit on its
 * mantle. Catppuccin's own guide raises them on surface0 instead, but there avatars fall under
 * 4.5:1 in Frappé and Macchiato, and so do Frappé's secondary text and red. Where a flavour
 * departs from Catppuccin, the comment on it says what moved and why.
 *
 * Every theme has its own accent for actions and its own pair of inks for amounts, and the accent
 * is never an amount colour.
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
    // Catppuccin Latte, the light flavour. Subtext1 for secondary text, because subtext0 is 4.06:1
    // on mantle. Green and red are darkened with their hue kept: Catppuccin's green is 2.96:1 on
    // the page, and its red is 4.46:1 on mantle and sits close to Material Red 800. Of Latte's
    // colours only mauve and red carry a button label at 4.5:1, and red belongs to amounts.
    AppTheme(
        name = "Latte",
        background = Color(0xFFEFF1F5), surface = Color(0xFFEFF1F5), card = Color(0xFFE6E9EF),
        primary = Color(0xFF8839EF), secondary = Color(0xFF7C7F93),
        textPrimary = Color(0xFF4C4F69), textSecondary = Color(0xFF5C5F77),
        positive = Color(0xFF27611A), negative = Color(0xFFBE0E34)
    ),
    // Catppuccin Frappé, the softest of the dark flavours. Sapphire for the accent: mauve,
    // lavender and teal each sit on a stock swatch here, and blue is too close to Mocha's for the
    // two to be worth offering separately.
    AppTheme(
        name = "Frappé",
        background = Color(0xFF303446), surface = Color(0xFF303446), card = Color(0xFF292C3C),
        primary = Color(0xFF85C1DC), secondary = Color(0xFF949CBB),
        textPrimary = Color(0xFFC6D0F5), textSecondary = Color(0xFFA5ADCE),
        positive = Color(0xFFA6D189), negative = Color(0xFFE78284)
    ),
    // Catppuccin Macchiato. Mauve, Catppuccin's own signature, is far enough from the stock
    // purples at this depth to keep.
    AppTheme(
        name = "Macchiato",
        background = Color(0xFF24273A), surface = Color(0xFF24273A), card = Color(0xFF1E2030),
        primary = Color(0xFFC6A0F6), secondary = Color(0xFF939AB7),
        textPrimary = Color(0xFFCAD3F5), textSecondary = Color(0xFFA5ADCB),
        positive = Color(0xFFA6DA95), negative = Color(0xFFED8796)
    ),
    // Catppuccin Mocha, the darkest. Blue for the accent, because mauve sits on Material Purple
    // 200 here. Anyone on a retired dark theme lands on this one.
    AppTheme(
        name = "Mocha",
        background = Color(0xFF1E1E2E), surface = Color(0xFF1E1E2E), card = Color(0xFF181825),
        primary = Color(0xFF89B4FA), secondary = Color(0xFF9399B2),
        textPrimary = Color(0xFFCDD6F4), textSecondary = Color(0xFFA6ADC8),
        positive = Color(0xFFA6E3A1), negative = Color(0xFFF38BA8)
    )
)

/**
 * Themes that used to exist, each pointed straight at a survivor: the light ones at Latte and the
 * dark ones at Mocha, rather than silently resetting anyone to the default. The stored preference
 * is only rewritten when they next pick a theme themselves.
 *
 * One hop only. [getThemeByName] does not follow a chain, so every name here must point at a theme
 * in [themes]: Slate used to point at Ocean, which is itself retired now.
 */
private val RETIRED_THEMES = mapOf(
    "Paper" to "Latte",
    "Cream" to "Latte",
    "Amoled" to "Mocha",
    "Ocean" to "Mocha",
    "Sunset" to "Mocha",
    "Purple" to "Mocha",
    "Slate" to "Mocha",
    "Charcoal" to "Mocha",
    "Gold" to "Mocha",
    "Rose" to "Mocha",
    "Vibrant" to "Mocha",
    "Neon" to "Mocha"
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
