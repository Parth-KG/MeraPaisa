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
    val negative: Color,
    /** Keys, the amount field, chips, and a highlighted or pressed row. */
    val highlight: Color,
    /** The hairline between rows, and the other quiet lines: a foot's edge, the widget's rules. */
    val divider: Color,
    /**
     * The edge of an outlined button or field, of a swatch, the sheet's handle, and a progress
     * track: firmer than a divider, because it marks something you can press or have to read.
     */
    val border: Color,
    /** Text and icons on the accent: filled buttons and a switch's thumb. */
    val onAccent: Color
) {
    val isDark: Boolean get() = background.luminance() < 0.4f

    /** Fill for rows, numpad keys and chips: a step away from the background, not white on white. */
    val fill: Color get() = card
}

/**
 * Ten themes, dark then light, chosen by a vote for v3.2.0 and named for things at home: Diya,
 * Jamun, Monsoon, Kaapi and Kamal, then Tulsi, Khadi, Gulab, Kansa and Neel.
 *
 * Every theme sets all its colours, the four that used to be worked out included: a highlight
 * for keys and highlighted rows that stands clear of the page and the card, a quiet divider (1.3
 * against the page, the weight Neel was specified with), a border at 3:1 on the page, the sheet and
 * the card, the least the edge of a field, a button or a switch should have, and the text that sits
 * on its accent. Borders were 2.3 to start with, Neel's weight, and every theme's review found its
 * fields and off switches faint, so each was darkened (light) or lightened (dark) at its own hue.
 *
 * Friends found some themes hard to read, so amounts and grey text are held to 7:1 against both
 * the page and the highlight, well past the 4.5:1 everything else meets. Where a colour fell short
 * it was lightened (dark themes) or darkened (light themes) at the same hue until it got there;
 * colours that already passed were left alone. ThemeInkContrastTest holds all of this, and the
 * accent is never an amount colour. Do not adjust a value here by eye.
 */
val themes = listOf(
    // A desk lamp at night, and the app's own: deep blue-grey, warm off-white text, a marigold
    // accent. Called Midnight until v3.2.0. The default, so it stays first.
    AppTheme(
        name = "Diya",
        background = Color(0xFF171C24), surface = Color(0xFF1E242E), card = Color(0xFF242B36),
        primary = Color(0xFFE8A33D), secondary = Color(0xFFB9A98C),
        textPrimary = Color(0xFFF2EFE9), textSecondary = Color(0xFFC5CEDA),
        positive = Color(0xFF8EDFA9), negative = Color(0xFFFFBDB6),
        highlight = Color(0xFF353C46), divider = Color(0xFF2C323A),
        border = Color(0xFF6C747D), onAccent = Color(0xFF171C24)
    ),
    // Butter-yellow text on plum-black under an orchid accent, from Pastelón de Amarillos
    // (Richard Martinez, MIT), the orchid lifted a step so text in it clears 5:1 on its sheets.
    // Called Pastelón Dark in the vote.
    AppTheme(
        name = "Jamun",
        background = Color(0xFF180D18), surface = Color(0xFF2A1424), card = Color(0xFF2A1424),
        primary = Color(0xFFC471BD), secondary = Color(0xFFA0747C),
        textPrimary = Color(0xFFFFE0A3), textSecondary = Color(0xFFD8AF9D),
        positive = Color(0xFF5CCC91), negative = Color(0xFFFE9DA0),
        highlight = Color(0xFF3C2530), divider = Color(0xFF35252D),
        border = Color(0xFF796160), onAccent = Color(0xFF180D18)
    ),
    // Catppuccin Mocha (github.com/catppuccin/palette, MIT): a periwinkle accent on a deep
    // grey-violet page.
    AppTheme(
        name = "Monsoon",
        background = Color(0xFF1E1E2E), surface = Color(0xFF1E1E2E), card = Color(0xFF181825),
        primary = Color(0xFF89B4FA), secondary = Color(0xFF9399B2),
        textPrimary = Color(0xFFCDD6F4), textSecondary = Color(0xFFB6BED9),
        positive = Color(0xFFA6E3A1), negative = Color(0xFFFEA5BD),
        highlight = Color(0xFF2F3040), divider = Color(0xFF313344),
        border = Color(0xFF65697D), onAccent = Color(0xFF181825)
    ),
    // Filter coffee: a dark-roast page, cream text and a turquoise accent. Drawn for the app.
    // Called Espresso in the vote.
    AppTheme(
        name = "Kaapi",
        // The sheet a step lighter than it was (v3.3.0, Parth's pick): at #23180F an open sheet
        // stood 1.10:1 off the dimmed page and its edge was hard to find.
        background = Color(0xFF1F150D), surface = Color(0xFF312318), card = Color(0xFF2C1E14),
        primary = Color(0xFF33DBD6), secondary = Color(0xFF8FB3B0),
        textPrimary = Color(0xFFF6EBDD), textSecondary = Color(0xFFCDBDAC),
        positive = Color(0xFFA3D17C), negative = Color(0xFFFEA9AD),
        highlight = Color(0xFF3E2F25), divider = Color(0xFF372C22),
        border = Color(0xFF7A6D60), onAccent = Color(0xFF1F150D)
    ),
    // A pine-green page under a lotus-pink accent. The page comes from a Pinterest palette, the
    // rest was drawn for the app. Its keys and keypad sit darker than the page, as its card does.
    // Called Pine & Rose Quartz in the vote.
    AppTheme(
        name = "Kamal",
        background = Color(0xFF2B4D3A), surface = Color(0xFF2B4D3A), card = Color(0xFF113321),
        primary = Color(0xFFF7A1C4), secondary = Color(0xFFB79DA0),
        textPrimary = Color(0xFFE6EDE8), textSecondary = Color(0xFFCFE3D6),
        positive = Color(0xFF9EF0B9), negative = Color(0xFFFED5CF),
        highlight = Color(0xFF1E3D2C), divider = Color(0xFF3F5E4C),
        border = Color(0xFF7C9886), onAccent = Color(0xFF113321)
    ),
    // Pale green account-book paper, ink-black text and sepia-ink buttons. Drawn for the app.
    // Called Ledger in the vote.
    AppTheme(
        name = "Tulsi",
        background = Color(0xFFE6EEDF), surface = Color(0xFFE6EEDF), card = Color(0xFFDCE6D3),
        primary = Color(0xFF4A3220), secondary = Color(0xFF5E6B66),
        textPrimary = Color(0xFF1F2A24), textSecondary = Color(0xFF313F37),
        positive = Color(0xFF024620), negative = Color(0xFF7D0120),
        highlight = Color(0xFFC7D1BF), divider = Color(0xFFC8D2C4),
        border = Color(0xFF788579), onAccent = Color(0xFFFFFFFF)
    ),
    // Ink on warm, undyed paper with a deep teal accent: Flexoki's light scheme (Steph Ango, MIT),
    // using its darker published steps where a test asks. Called Flexoki Light in the vote. The
    // owed-to-you green is the app's own (v3.3.0, Parth's pick): Flexoki's olive, darkened to
    // 7:1 on the keys, read as near-black, so it is a leaf green at the same lightness.
    AppTheme(
        name = "Khadi",
        background = Color(0xFFFFFCF0), surface = Color(0xFFFFFCF0), card = Color(0xFFF2F0E5),
        primary = Color(0xFF1C6C66), secondary = Color(0xFF6F6E69),
        textPrimary = Color(0xFF100F0F), textSecondary = Color(0xFF42413E),
        positive = Color(0xFF074C00), negative = Color(0xFF821412),
        highlight = Color(0xFFD8D6CC), divider = Color(0xFFE0DED4),
        border = Color(0xFF8C8A83), onAccent = Color(0xFFFFFFFF)
    ),
    // A cherry-blossom page with grape-plum buttons, from the Japanese traditional colours.
    // Called Sakura in the vote.
    AppTheme(
        name = "Gulab",
        background = Color(0xFFFEDFE1), surface = Color(0xFFFEDFE1), card = Color(0xFFF8DADC),
        primary = Color(0xFF6D2E5B), secondary = Color(0xFF72636E),
        textPrimary = Color(0xFF3F2B36), textSecondary = Color(0xFF433843),
        positive = Color(0xFF014528), negative = Color(0xFF7C0513),
        highlight = Color(0xFFE4C7CA), divider = Color(0xFFDFC4C7),
        border = Color(0xFF907B83), onAccent = Color(0xFFFFFFFF)
    ),
    // Bell metal on lilac: a near-white lavender page with bronze buttons. Called Lavender Bronze
    // in the vote.
    AppTheme(
        name = "Kansa",
        background = Color(0xFFF8F7FF), surface = Color(0xFFF8F7FF), card = Color(0xFFE7E2F4),
        primary = Color(0xFF78552B), secondary = Color(0xFF947A6D),
        textPrimary = Color(0xFF211D2E), textSecondary = Color(0xFF41365D),
        positive = Color(0xFF024629), negative = Color(0xFF7D0714),
        highlight = Color(0xFFD1CCDE), divider = Color(0xFFDBD8E6),
        border = Color(0xFF848097), onAccent = Color(0xFFFFFFFF)
    ),
    // Navy on cool white, new in v3.2.0, with the dividers and white button text it was specified
    // with. Its border is the specified #9AA6BA darkened at the same hue to 3:1.
    AppTheme(
        name = "Neel",
        background = Color(0xFFF5F7FA), surface = Color(0xFFF5F7FA), card = Color(0xFFF1F3F7),
        primary = Color(0xFF1B2C55), secondary = Color(0xFF5E6A84),
        textPrimary = Color(0xFF0E1A33), textSecondary = Color(0xFF3F4B63),
        positive = Color(0xFF0A5631), negative = Color(0xFF931B15),
        highlight = Color(0xFFE2E7EF), divider = Color(0xFFD3DAE5),
        border = Color(0xFF828DA1), onAccent = Color(0xFFFFFFFF)
    )
)

/**
 * Names the app no longer has, each pointed straight at a theme that exists, so nobody is reset to
 * the default. The stored preference is a name, and it is only rewritten when they next pick a
 * theme themselves; until then it resolves here every time it is read, which also covers a
 * preference brought back by Android's own backup.
 *
 * One hop only. [getThemeByName] does not follow a chain, so every name here must point at a theme
 * in [themes].
 */
private val RETIRED_THEMES = mapOf(
    // Renamed in v3.2.0: the same theme under its new name.
    "Midnight" to "Diya",
    "Pastelón Dark" to "Jamun",
    "Mocha" to "Monsoon",
    "Espresso" to "Kaapi",
    "Pine & Rose Quartz" to "Kamal",
    "Ledger" to "Tulsi",
    "Flexoki Light" to "Khadi",
    "Sakura" to "Gulab",
    "Lavender Bronze" to "Kansa",
    // Tried during the vote and dropped.
    "Latte" to "Neel",
    "Ultraviolet & Sand" to "Jamun",
    "Turquoise & Burgundy" to "Jamun",
    "Frappé" to "Monsoon",
    "Macchiato" to "Monsoon",
    // Released before v3.2.0, each to the theme closest to it.
    "Paper" to "Neel",
    "Cream" to "Neel",
    "Purple" to "Jamun",
    "Amoled" to "Monsoon",
    "Ocean" to "Monsoon",
    "Sunset" to "Monsoon",
    "Slate" to "Monsoon",
    "Charcoal" to "Monsoon",
    "Gold" to "Monsoon",
    "Rose" to "Monsoon",
    "Vibrant" to "Monsoon",
    "Neon" to "Monsoon"
)

fun getThemeByName(name: String): AppTheme {
    val resolved = RETIRED_THEMES[name] ?: name
    return themes.find { it.name == resolved } ?: themes[0]
}

/**
 * Each theme's partner in the other mode, dark with light, chosen by Parth (6 Oct 2026).
 *
 * The home-screen widget follows the launcher's day and night, not the app's theme, so a theme
 * needs a partner to stand in for it the other half of the day. Every dark theme used to borrow
 * Neel by day and every light one Diya by night, so Kamal's pine page turned navy at sunrise.
 */
private val THEME_PARTNERS = listOf(
    "Diya" to "Neel",
    "Jamun" to "Gulab",
    "Monsoon" to "Kansa",
    "Kaapi" to "Khadi",
    "Kamal" to "Tulsi"
).flatMap { (dark, light) -> listOf(dark to light, light to dark) }.toMap()

/** The theme that stands in for [theme] in the other mode. */
fun partnerOf(theme: AppTheme): AppTheme =
    THEME_PARTNERS[theme.name]?.let(::getThemeByName)
        // A theme added without a partner still gets a legible one; WidgetPaletteTest fails on it.
        ?: getThemeByName(if (theme.isDark) "Neel" else "Diya")

/**
 * Every Material component pulls its colours from here, so the app and the M3 defaults finally
 * agree and per-component `colors = ...` overrides are no longer needed to undo a purple scheme.
 */
fun AppTheme.toColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onAccent,
        primaryContainer = highlight,
        onPrimaryContainer = textPrimary,
        inversePrimary = background,
        secondary = secondary,
        onSecondary = background,
        secondaryContainer = highlight,
        onSecondaryContainer = textPrimary,
        tertiary = primary,
        onTertiary = onAccent,
        tertiaryContainer = highlight,
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
        outline = border,
        outlineVariant = divider,
        scrim = Color.Black,
        surfaceBright = highlight,
        surfaceDim = background,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = highlight,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = onAccent,
        primaryContainer = highlight,
        onPrimaryContainer = textPrimary,
        inversePrimary = background,
        secondary = secondary,
        onSecondary = background,
        secondaryContainer = highlight,
        onSecondaryContainer = textPrimary,
        tertiary = primary,
        onTertiary = onAccent,
        tertiaryContainer = highlight,
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
        outline = border,
        outlineVariant = divider,
        scrim = Color.Black,
        surfaceBright = background,
        surfaceDim = highlight,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = highlight,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background
    )
}
