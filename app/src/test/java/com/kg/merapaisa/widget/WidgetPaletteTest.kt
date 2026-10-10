package com.kg.merapaisa.widget

import androidx.compose.ui.graphics.Color
import com.kg.merapaisa.partnerOf
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.AVATAR_HUES
import com.kg.merapaisa.ui.avatarColours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The home-screen widget's colours, measured the way the app's own are.
 *
 * The widget drew its initials in the text colour over a heavier wash of the person's ink, which
 * nothing measured, and in Kamal they fell under 4.5:1.
 */
class WidgetPaletteTest {

    /** Every hue on offer, the green every avatar was stored as before v3.0.0, and junk. */
    private val stored = AVATAR_HUES + listOf("#4CAF50", "", "not a colour", "#12")

    @Test
    fun widgetInitialsClearAaOnTheirTileInEveryTheme() {
        val failures = themes.flatMap { theme ->
            stored.map { colour ->
                val (ink, tile) = avatarColours(colour, theme)
                "${theme.name}, \"$colour\"" to contrastRatio(ink, tile)
            }
        }.filter { (_, ratio) -> ratio < 4.5 }

        assertTrue(
            "initials fall below 4.5:1 on their tile\n" +
                failures.joinToString("\n") { (label, ratio) -> "  %s = %.2f:1".format(label, ratio) },
            failures.isEmpty()
        )
    }

    @Test
    fun theTileIsSolid() {
        themes.forEach { theme ->
            stored.forEach { colour ->
                assertTrue("${theme.name}, \"$colour\"", avatarColours(colour, theme).tile.alpha == 1f)
            }
        }
    }

    @Test
    fun everyThemeHasOnePartnerOfTheOtherDarkness() {
        themes.forEach { theme ->
            val partner = partnerOf(theme)
            assertTrue("${theme.name} is paired with ${partner.name}", partner.isDark != theme.isDark)
        }
    }

    @Test
    fun thePairsGoBothWays() {
        themes.forEach { theme ->
            assertEquals(theme.name, partnerOf(partnerOf(theme)).name)
        }
    }

    @Test
    fun dayIsTheLightThemeOfThePairAndNightTheDarkOne() {
        themes.forEach { theme ->
            val palette = paletteFor(theme.name)
            assertTrue(!palette.day.isDark && palette.night.isDark)
            assertTrue(theme.name == palette.day.name || theme.name == palette.night.name)
        }
        assertEquals("Neel", paletteFor("Diya").day.name)
        assertEquals("Kamal", paletteFor("Tulsi").night.name)
    }

    @Test
    fun aRetiredNameGivesItsSurvivorsPalette() {
        assertEquals(paletteFor("Diya"), paletteFor("Midnight"))
        assertEquals(paletteFor("Monsoon"), paletteFor("Mocha"))
    }

    private companion object {
        /** WCAG 2.1 relative luminance, computed here so the test owns its own definition. */
        fun luminance(color: Color): Double {
            fun channel(value: Float): Double {
                val c = value.toDouble()
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        }

        fun contrastRatio(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }
    }
}
