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

    /** Initials are drawn in the ink over a wash of the same ink, so they must clear AA. */
    @Test
    fun everyHueIsLegibleOnEveryTheme() {
        val failures = themes.flatMap { theme ->
            AVATAR_HUES.map { hue ->
                val ink = avatarInk(hue, theme.isDark)
                // The tile behind the initials is the ink at AVATAR_WASH over the theme's card.
                val behind = blend(ink, theme.card, AVATAR_WASH)
                "${theme.name} / $hue" to contrast(ink, behind)
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
