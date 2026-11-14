package com.kg.merapaisa.ui.dialogs

import com.kg.merapaisa.ui.AVATAR_HUES
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The colour a new person starts on: the one fewest people have, so the swatch is chosen once
 * when the sheet opens rather than following the name letter by letter (Parth, 15 Nov 2026).
 */
class LeastUsedHueTest {

    @Test
    fun `an empty ledger starts on the first hue`() {
        assertEquals(AVATAR_HUES.first(), leastUsedHue(emptyList()))
    }

    @Test
    fun `people added one after another walk through all eight before any repeats`() {
        val taken = mutableListOf<String>()
        repeat(AVATAR_HUES.size) { taken += leastUsedHue(taken) }
        assertEquals(AVATAR_HUES, taken)
        assertEquals("the ninth starts the round again", AVATAR_HUES.first(), leastUsedHue(taken))
    }

    @Test
    fun `the hue fewest people have wins, wherever it sits`() {
        val everyoneButOlive = AVATAR_HUES.filter { it != "#6E8C3A" }.flatMap { listOf(it, it) } + "#6E8C3A"
        assertEquals("#6E8C3A", leastUsedHue(everyoneButOlive))
    }

    @Test
    fun `stored colours match whatever their case, and a retired one counts for none`() {
        val lower = AVATAR_HUES.first().lowercase()
        assertEquals(AVATAR_HUES[1], leastUsedHue(listOf(lower, "#5A6B7D")))
    }
}
