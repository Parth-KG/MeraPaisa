package com.kg.merapaisa

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The rules ThemeContrastTest does not cover.
 *
 * That test checks the two text colours and the surface steps, which is why Paper could ship with
 * its positive ink at 4.39:1 on card: an amount, the one thing on the screen that has to be read
 * exactly, was never measured. This file measures the inks, the accent, and the label sitting on
 * an accent button, and it holds the palette to the design rules that are not about contrast at
 * all: an accent is for actions, an ink is for amounts, and neither may be a stock swatch.
 *
 * New checks go here. ThemeContrastTest stays as it is.
 */
class ThemeInkContrastTest {

    // -----------------------------------------------------------------------------------------
    // Legibility
    // -----------------------------------------------------------------------------------------

    /**
     * Every ink on every surface it can land on.
     *
     * An amount is the one thing on screen that has to be read exactly rather than recognised, so
     * it gets the same floor as body text, on all three surfaces rather than just the background.
     */
    @Test
    fun everyInkIsLegibleOnEverySurface() {
        val failures = themes.flatMap { theme ->
            val inks = listOf(
                "textPrimary" to theme.textPrimary,
                "textSecondary" to theme.textSecondary,
                "positive" to theme.positive,
                "negative" to theme.negative
            )
            val surfaces = listOf(
                "background" to theme.background,
                "surface" to theme.surface,
                "card" to theme.card
            )
            inks.flatMap { (inkName, ink) ->
                surfaces.map { (surfaceName, surface) ->
                    "${theme.name}: $inkName on $surfaceName" to contrastRatio(ink, surface)
                }
            }
        }.filter { (_, ratio) -> ratio < MIN_RATIO }

        assertTrue(
            "these fall below $MIN_RATIO:1\n" +
                failures.joinToString("\n") { (label, ratio) -> "  %s = %.2f:1".format(label, ratio) },
            failures.isEmpty()
        )
    }

    /**
     * The label on a primary button.
     *
     * Every filled button draws its label in the theme's `onAccent`, and `toColorScheme` passes it
     * on as `onPrimary` for switches. Get the pair wrong and every primary button in the app
     * becomes unreadable at once, which no screenshot of a single screen would catch.
     */
    @Test
    fun anAccentButtonLabelIsLegible() {
        val failures = themes
            .map { it.name to contrastRatio(it.onAccent, it.primary) }
            .filter { (_, ratio) -> ratio < MIN_RATIO }

        assertTrue(
            "a button label falls below $MIN_RATIO:1 on: " +
                failures.joinToString { (name, ratio) -> "%s (%.2f:1)".format(name, ratio) },
            failures.isEmpty()
        )
    }

    /**
     * Words on a key.
     *
     * The keypad's keys and its two direction buttons are the `highlight`, and the digits, the button
     * labels and the hint under each label are textPrimary on it. Nothing above measures that
     * tile, and the hint is small, so it gets the body-text floor too.
     */
    @Test
    fun wordsOnAKeyAreLegible() {
        val failures = themes
            .map { it.name to contrastRatio(it.textPrimary, it.highlight) }
            .filter { (_, ratio) -> ratio < MIN_RATIO }

        assertTrue(
            "text on a key falls below $MIN_RATIO:1 on: " +
                failures.joinToString { (name, ratio) -> "%s (%.2f:1)".format(name, ratio) },
            failures.isEmpty()
        )
    }

    /**
     * A theme's name in the picker, which is written in its own accent on its own page so the chip
     * is a sample of the theme. Whichever theme the picker is drawn in, every name has to be read.
     */
    @Test
    fun aThemeNamesItselfInItsAccent() {
        val failures = themes
            .map { it.name to contrastRatio(it.primary, it.background) }
            .filter { (_, ratio) -> ratio < MIN_RATIO }

        assertTrue(
            "a theme's name falls below $MIN_RATIO:1 on its own page: " +
                failures.joinToString { (name, ratio) -> "%s (%.2f:1)".format(name, ratio) },
            failures.isEmpty()
        )
    }

    // -----------------------------------------------------------------------------------------
    // Meaning
    // -----------------------------------------------------------------------------------------

    /**
     * An accent means "you can act on this". An ink means "this is how much". Every theme used to
     * ship the same colour for both, so a primary button and a credit balance were identical and
     * the app effectively had no accent of its own.
     *
     * Distance, not equality: two colours a few units apart read as the same colour on a phone,
     * and the equality check would pass while the screen stayed ambiguous.
     */
    @Test
    fun anAccentIsNeverAlsoAnAmountInk() {
        val failures = themes.flatMap { theme ->
            listOf(
                "${theme.name}: positive vs accent" to distance(theme.positive, theme.primary),
                "${theme.name}: negative vs accent" to distance(theme.negative, theme.primary)
            )
        }.filter { (_, d) -> d < MIN_ROLE_DISTANCE }

        assertTrue(
            "an amount ink is too close to the accent to mean something different\n" +
                failures.joinToString("\n") { (label, d) -> "  %s = %.1f, needs %.1f".format(label, d, MIN_ROLE_DISTANCE) },
            failures.isEmpty()
        )
    }

    /** Owed to you and owing have to be tellable apart at a glance, in every theme. */
    @Test
    fun theTwoDirectionsAreTellableApart() {
        val failures = themes
            .map { it.name to distance(it.positive, it.negative) }
            .filter { (_, d) -> d < MIN_DIRECTION_DISTANCE }

        assertTrue(
            "positive and negative are too close on: " +
                failures.joinToString { (name, d) -> "%s (%.1f)".format(name, d) },
            failures.isEmpty()
        )
    }

    /**
     * No colour straight off a swatch sheet.
     *
     * Ten of these twelve were in the palette verbatim, which is most of why the app read as a
     * template rather than as something chosen. Distance rather than equality, so nudging one
     * digit does not get a swatch back in.
     */
    @Test
    fun noThemeUsesAStockSwatch() {
        val failures = themes.flatMap { theme ->
            listOf("accent" to theme.primary, "positive" to theme.positive, "negative" to theme.negative)
                .flatMap { (role, colour) ->
                    STOCK_SWATCHES.map { (swatchName, swatch) ->
                        "${theme.name}: $role vs $swatchName" to distance(colour, swatch)
                    }
                }
        }.filter { (_, d) -> d < MIN_STOCK_DISTANCE }

        assertTrue(
            "these sit on top of a stock swatch\n" +
                failures.joinToString("\n") { (label, d) -> "  %s = %.1f, needs %.1f".format(label, d, MIN_STOCK_DISTANCE) },
            failures.isEmpty()
        )
    }

    /** Pure white on a dark ground glares and smears. Off-white reads as ink instead. */
    @Test
    fun darkThemesUseOffWhiteText() {
        val failures = themes
            .filter { it.isDark }
            .filter { it.textPrimary == Color.White }
            .map { it.name }

        assertTrue("these use pure #FFFFFF for textPrimary: $failures", failures.isEmpty())
    }

    /**
     * Diya is the default for a new install and where any name the app does not know lands, so it
     * is first, and the picker lists the dark themes before the light ones.
     */
    @Test
    fun diyaComesFirstAndDarkThemesBeforeLight() {
        assertEquals("Diya", themes.first().name)
        val firstLight = themes.indexOfFirst { !it.isDark }
        assertTrue(
            "a dark theme comes after a light one: ${themes.map { it.name }}",
            themes.drop(firstLight).none { it.isDark }
        )
    }

    // -----------------------------------------------------------------------------------------
    // Readability, past the floor
    // -----------------------------------------------------------------------------------------

    /**
     * Amounts and grey text at 7:1, on the page and on a highlighted row.
     *
     * Friends found some themes hard to read at the 4.5:1 floor, so the two things read most,
     * which way money goes and the words under each name, are held to the enhanced level wherever
     * they actually sit: on the page, and on a highlighted row or key.
     */
    @Test
    fun amountsAndGreyTextReachSevenToOne() {
        val failures = themes.flatMap { theme ->
            listOf("positive" to theme.positive, "negative" to theme.negative, "textSecondary" to theme.textSecondary)
                .flatMap { (inkName, ink) ->
                    listOf("background" to theme.background, "highlight" to theme.highlight).map { (onName, on) ->
                        "${theme.name}: $inkName on $onName" to contrastRatio(ink, on)
                    }
                }
        }.filter { (_, ratio) -> ratio < MIN_ENHANCED_RATIO }

        assertTrue(
            "these fall below $MIN_ENHANCED_RATIO:1\n" +
                failures.joinToString("\n") { (label, ratio) -> "  %s = %.2f:1".format(label, ratio) },
            failures.isEmpty()
        )
    }

    /**
     * A divider separates rows without competing with them; a border marks something you can press,
     * so it is the firmer of the two, and at 3:1 on every ground a field or a switch sits on: the
     * page, a sheet and a card. At 2.3 on the page, every theme's fields and off switches were faint.
     */
    @Test
    fun dividersAreQuietAndBordersFirm() {
        val failures = themes.flatMap { theme ->
            val divider = contrastRatio(theme.divider, theme.background)
            val border = listOf(theme.background, theme.surface, theme.card).minOf { contrastRatio(theme.border, it) }
            listOfNotNull(
                if (divider < MIN_DIVIDER) "${theme.name}: divider %.2f:1, needs %.2f".format(divider, MIN_DIVIDER) else null,
                if (border < MIN_BORDER || border <= divider)
                    "${theme.name}: border %.2f:1, needs %.2f and more than the divider".format(border, MIN_BORDER) else null
            )
        }

        assertTrue("these lines do not read as intended\n  " + failures.joinToString("\n  "), failures.isEmpty())
    }

    private companion object {
        const val MIN_RATIO = 4.5
        const val MIN_ENHANCED_RATIO = 7.0
        const val MIN_DIVIDER = 1.2
        const val MIN_BORDER = 3.0

        /** Below this two colours read as the same colour on a phone, whatever the hex says. */
        const val MIN_ROLE_DISTANCE = 15.0
        const val MIN_DIRECTION_DISTANCE = 20.0
        const val MIN_STOCK_DISTANCE = 12.0

        /** The sheets the old palette was assembled from. */
        val STOCK_SWATCHES = mapOf(
            "Material 2014 Light Blue 400" to Color(0xFF29B6F6),
            "Material 2014 Deep Orange 400" to Color(0xFFFF7043),
            "Material 2014 Deep Orange 300" to Color(0xFFFF8A65),
            "Material 2014 Blue 400" to Color(0xFF42A5F5),
            "Material 2014 Purple 200" to Color(0xFFCE93D8),
            "Material 2014 Teal 200" to Color(0xFF80CBC4),
            "Material 2014 Green 800" to Color(0xFF2E7D32),
            "Material 2014 Red 800" to Color(0xFFC62828),
            "Flat UI Emerald" to Color(0xFF2ECC71),
            "Flat UI Alizarin" to Color(0xFFE84B3A),
            "Material 3 baseline primary" to Color(0xFF6750A4),
            "Material 3 baseline primary 80" to Color(0xFFD0BCFF)
        )

        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }

        fun luminance(color: Color): Double =
            0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

        fun contrastRatio(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        /** CIE Lab distance, which tracks what the eye reports far better than RGB does. */
        fun distance(a: Color, b: Color): Double {
            fun lab(c: Color): Triple<Double, Double, Double> {
                val r = channel(c.red)
                val g = channel(c.green)
                val bl = channel(c.blue)
                val x = (r * 0.4124 + g * 0.3576 + bl * 0.1805) / 0.95047
                val y = r * 0.2126 + g * 0.7152 + bl * 0.0722
                val z = (r * 0.0193 + g * 0.1192 + bl * 0.9505) / 1.08883
                fun f(t: Double) = if (t > 0.008856) t.pow(1.0 / 3) else 7.787 * t + 16.0 / 116
                val fx = f(x)
                val fy = f(y)
                val fz = f(z)
                return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
            }

            val (l1, a1, b1) = lab(a)
            val (l2, a2, b2) = lab(b)
            return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
        }
    }
}
