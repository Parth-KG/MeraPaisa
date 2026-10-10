package com.kg.merapaisa.ui

import com.kg.merapaisa.themes
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Avatar colours, which have to survive every theme without being chosen once per theme.
 *
 * The stored value is a hue. [avatarInk] supplies the lightness, because a colour that reads on a
 * light page disappears on a dark one, and one that reads on a dark page glares on a light one.
 * These tests are what stop a future palette edit from quietly reintroducing that.
 */
class AvatarInkTest {

    /**
     * Initials are drawn in the ink over a wash of the same ink, so they must clear AA on every
     * ground an avatar sits on: the page in the list, a highlighted row, a card, a sheet.
     *
     * This once measured the card alone, which is not where the list draws them, and so missed
     * Kamal's page at 3.5:1 and the light themes' highlighted rows under 4:1.
     */
    @Test
    fun everyHueIsLegibleOnEveryTheme() {
        val failures = themes.flatMap { theme ->
            val grounds = listOf(
                "page" to theme.background, "highlighted row" to theme.highlight,
                "card" to theme.card, "sheet" to theme.surface
            )
            AVATAR_HUES.flatMap { hue ->
                val ink = avatarInk(hue, theme)
                grounds.map { (groundName, ground) ->
                    "${theme.name} / $hue on its $groundName" to contrast(ink, blend(ink, ground, AVATAR_WASH))
                }
            }
        }.filter { (_, ratio) -> ratio < 4.5 }

        assertTrue(
            "these initials fall below 4.5:1 on their own tile\n" +
                failures.joinToString("\n") { (label, r) -> "  %s = %.2f:1".format(label, r) },
            failures.isEmpty()
        )
    }

    /** Two people should not get avatars you have to compare side by side to tell apart. */
    @Test
    fun theHuesAreFarEnoughApart() {
        val failures = mutableListOf<String>()
        for (i in AVATAR_HUES.indices) {
            for (j in i + 1 until AVATAR_HUES.size) {
                val gap = hueGap(AVATAR_HUES[i], AVATAR_HUES[j])
                if (gap < 0.04f) failures += "${AVATAR_HUES[i]} vs ${AVATAR_HUES[j]} = %.3f".format(gap)
            }
        }
        assertTrue("hues too close:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Two people still cannot share a look once their hues are re-lit for a theme. Re-lighting
     * pulls every ink toward one lightness, so hues that are apart on the wheel can land close on
     * the page: Slate and Indigo did, 5.5 apart in Kamal, which is why Slate became Violet.
     */
    @Test
    fun theRelitInksStayApartInEveryTheme() {
        val failures = themes.flatMap { theme ->
            val inks = AVATAR_HUES.map { it to avatarInk(it, theme) }
            inks.flatMapIndexed { i, (a, inkA) ->
                inks.drop(i + 1).map { (b, inkB) -> "${theme.name}: $a vs $b" to distance(inkA, inkB) }
            }
        }
        val tooClose = failures.filter { (_, d) -> d < MIN_INK_DISTANCE }
        assertTrue(
            "these avatar inks are too close to tell apart\n" +
                tooClose.joinToString("\n") { (label, d) -> "  %s = %.1f".format(label, d) } +
                "\nclosest: %s = %.1f".format(failures.minBy { it.second }.first, failures.minOf { it.second }),
            tooClose.isEmpty()
        )
    }

    /**
     * The point of re-lighting rather than replacing: somebody who chose green keeps green. Every
     * avatar in an existing ledger is Material Green 500, and they should stay green and become
     * readable, not turn into the accent.
     */
    @Test
    fun anExistingColourKeepsItsHue() {
        val storedGreen = "#4CAF50"
        val onDark = avatarInk(storedGreen, isDarkTheme = true)
        val onLight = avatarInk(storedGreen, isDarkTheme = false)

        assertTrue("still green on a dark theme", onDark.green > onDark.red && onDark.green > onDark.blue)
        assertTrue("still green on a light theme", onLight.green > onLight.red && onLight.green > onLight.blue)
        assertTrue("and lighter on dark than on light", onDark.green > onLight.green)
    }

    /** A stored value that is not a colour must not crash a list of people. */
    @Test
    fun rubbishFallsBackInsteadOfThrowing() {
        listOf("", "nonsense", "#12", "#GGGGGG", "rgb(1,2,3)").forEach { bad ->
            val ink = avatarInk(bad, isDarkTheme = true)
            assertTrue("$bad produced nothing usable", ink.alpha > 0f)
        }
    }

    private companion object {
        const val MIN_INK_DISTANCE = 10.0

        /** CIE76 distance in Lab, as ThemeContrastTest measures it. */
        fun distance(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
            fun lab(c: androidx.compose.ui.graphics.Color): Triple<Double, Double, Double> {
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

        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }

        fun luminance(c: androidx.compose.ui.graphics.Color): Double =
            0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

        fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        /** [top] at [alpha] over [bottom], which is what the avatar tile actually is. */
        fun blend(
            top: androidx.compose.ui.graphics.Color,
            bottom: androidx.compose.ui.graphics.Color,
            alpha: Float
        ) = androidx.compose.ui.graphics.Color(
            top.red * alpha + bottom.red * (1 - alpha),
            top.green * alpha + bottom.green * (1 - alpha),
            top.blue * alpha + bottom.blue * (1 - alpha)
        )
    }
}
