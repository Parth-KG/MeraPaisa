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
 * textSecondary is tuned so every theme clears the WCAG AA 4.5:1 ratio against both its
 * background and its card fill. ThemeContrastTest enforces it; do not darken these by eye.
 */
val themes = listOf(
    AppTheme("Midnight", Color(0xFF0A0A0F), Color(0xFF111118), Color(0xFF16161F), Color(0xFF2ECC71), Color(0xFFE84B3A), Color.White, Color(0xFF818181), Color(0xFF2ECC71), Color(0xFFE84B3A)),
    AppTheme("Amoled", Color(0xFF000000), Color(0xFF0A0A0A), Color(0xFF111111), Color(0xFF00FF88), Color(0xFFFF3B3B), Color.White, Color(0xFF7D7D7D), Color(0xFF00FF88), Color(0xFFFF3B3B)),
    AppTheme("Ocean", Color(0xFF0A0F1A), Color(0xFF0F1929), Color(0xFF142030), Color(0xFF29B6F6), Color(0xFFFF7043), Color.White, Color(0xFF6E8B9A), Color(0xFF29B6F6), Color(0xFFFF7043)),
    AppTheme("Sunset", Color(0xFF1A0F0A), Color(0xFF291509), Color(0xFF301A0E), Color(0xFFFF8A65), Color(0xFF42A5F5), Color.White, Color(0xFFAA7D6F), Color(0xFFFF8A65), Color(0xFF42A5F5)),
    AppTheme("Purple", Color(0xFF0F0A1A), Color(0xFF160F29), Color(0xFF1C1430), Color(0xFFCE93D8), Color(0xFF80CBC4), Color.White, Color(0xFF9F63E9), Color(0xFFCE93D8), Color(0xFF80CBC4)),
    AppTheme("Paper", Color(0xFFF5F5F0), Color(0xFFFFFFFF), Color(0xFFF0EDE8), Color(0xFF2E7D32), Color(0xFFC62828), Color(0xFF1A1A1A), Color(0xFF6B6B6B), Color(0xFF2E7D32), Color(0xFFC62828))
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
