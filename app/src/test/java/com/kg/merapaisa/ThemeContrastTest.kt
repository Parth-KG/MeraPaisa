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
        assertTrue("expected 6 themes, found ${themes.size}", themes.size == 6)
        assertTrue("theme names must be unique", themes.map { it.name }.toSet().size == themes.size)
    }

    @Test
    fun noTwoThemesLookAlike() {
        // The set was cut down because several themes were near-duplicates; keep it that way.
        val tooClose = themes.flatMap { a ->
            themes.filter { it.name > a.name }.map { b ->
                "${a.name} vs ${b.name}" to
                    (0.35 * distance(a.background, b.background) + 0.65 * distance(a.primary, b.primary))
            }
        }.filter { (_, d) -> d < MIN_THEME_DISTANCE }

        assertTrue(
            "these are too similar to be worth offering separately —\n" +
                tooClose.joinToString("\n") { (pair, d) -> "  %s = %.1f".format(pair, d) },
            tooClose.isEmpty()
        )
    }

    @Test
    fun everyRetiredThemeResolvesToASurvivor() {
        // Anyone still on a removed theme should land somewhere deliberate, not the default.
        mapOf(
            "Slate" to "Ocean", "Charcoal" to "Sunset", "Gold" to "Sunset",
            "Cream" to "Paper", "Rose" to "Purple", "Vibrant" to "Purple", "Neon" to "Amoled"
        ).forEach { (retired, expected) ->
            assertTrue(
                "$retired should resolve to $expected, got ${getThemeByName(retired).name}",
                getThemeByName(retired).name == expected
            )
        }
        // Anything unrecognised still falls back to the default rather than crashing.
        assertTrue(getThemeByName("Nonsense").name == themes.first().name)
    }

    private companion object {
        const val MIN_RATIO = 4.5
        const val MIN_SURFACE_SEPARATION = 1.03
        const val MIN_SELECTED_SEPARATION = 1.10
        const val MIN_THEME_DISTANCE = 15.0

        /** CIE76 distance in Lab space — a rough stand-in for "tell these apart at a glance". */
        fun distance(a: Color, b: Color): Double {
            fun lab(c: Color): Triple<Double, Double, Double> {
                fun lin(v: Float): Double {
                    val d = v.toDouble()
                    return if (d <= 0.04045) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
                }
                val r = lin(c.red); val g = lin(c.green); val bl = lin(c.blue)
                val x = (r * 0.4124 + g * 0.3576 + bl * 0.1805) / 0.95047
                val y = r * 0.2126 + g * 0.7152 + bl * 0.0722
                val z = (r * 0.0193 + g * 0.1192 + bl * 0.9505) / 1.08883
                fun f(t: Double) = if (t > 0.008856) t.pow(1.0 / 3) else 7.787 * t + 16.0 / 116
                return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
            }
            val (l1, a1, b1) = lab(a)
            val (l2, a2, b2) = lab(b)
            return kotlin.math.sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
        }

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
