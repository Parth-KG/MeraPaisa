package com.kg.merapaisa

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Secondary text is real content — dates, "you owe", balances-at-a-glance — so it has to be
 * readable, not decorative. Every theme must clear the WCAG AA 4.5:1 ratio against the two
 * surfaces that text actually sits on: the screen background and the card fill.
 */
class ThemeContrastTest {

    @Test
    fun secondaryTextClearsWcagAaOnEveryTheme() {
        val failures = themes.flatMap { theme ->
            listOf(
                "${theme.name}: textSecondary on background" to
                    contrastRatio(theme.textSecondary, theme.background),
                "${theme.name}: textSecondary on card" to
                    contrastRatio(theme.textSecondary, theme.card)
            )
        }.filter { (_, ratio) -> ratio < MIN_RATIO }

        assertTrue(
            "these fall below $MIN_RATIO:1 —\n" +
                failures.joinToString("\n") { (label, ratio) -> "  %s = %.2f:1".format(label, ratio) },
            failures.isEmpty()
        )
    }

    @Test
    fun primaryTextClearsWcagAaOnEveryTheme() {
        val failures = themes
            .map { it.name to contrastRatio(it.textPrimary, it.background) }
            .filter { (_, ratio) -> ratio < MIN_RATIO }

        assertTrue(
            "textPrimary falls below $MIN_RATIO:1 on: " +
                failures.joinToString { (name, ratio) -> "%s (%.2f:1)".format(name, ratio) },
            failures.isEmpty()
        )
    }

    @Test
    fun rowsAndKeysAreVisibleAgainstEveryBackground() {
        // The old UI painted every surface with Color.White.copy(alpha = 0.03f), which on the
        // light themes was white on near-white. Each fill must stand off what sits behind it.
        val failures = themes.flatMap { theme ->
            listOf(
                "${theme.name}: fill on background" to
                    Pair(contrastRatio(theme.fill, theme.background), MIN_SURFACE_SEPARATION),
                "${theme.name}: fillStrong on fill" to
                    Pair(contrastRatio(theme.fillStrong, theme.fill), MIN_SELECTED_SEPARATION)
            )
        }.filter { (_, measured) -> measured.first < measured.second }

        assertTrue(
            "these surfaces are indistinguishable —\n" +
                failures.joinToString("\n") { (label, measured) ->
                    "  %s = %.3f:1, needs %.3f:1".format(label, measured.first, measured.second)
                },
            failures.isEmpty()
        )
    }

    @Test
    fun allThemesAreNamedAndDistinct() {
        assertTrue("expected 13 themes, found ${themes.size}", themes.size == 13)
        assertTrue("theme names must be unique", themes.map { it.name }.toSet().size == themes.size)
    }

    private companion object {
        const val MIN_RATIO = 4.5
        const val MIN_SURFACE_SEPARATION = 1.03
        const val MIN_SELECTED_SEPARATION = 1.10

        /** WCAG 2.1 relative luminance, computed here so the test owns its own definition. */
        fun luminance(color: Color): Double {
            fun channel(value: Float): Double {
                val c = value.toDouble()
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(color.red) +
                0.7152 * channel(color.green) +
                0.0722 * channel(color.blue)
        }

        fun contrastRatio(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }
    }
}
