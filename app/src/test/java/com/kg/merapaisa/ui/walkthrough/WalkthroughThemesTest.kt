package com.kg.merapaisa.ui.walkthrough

import com.kg.merapaisa.partnerOf
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.WalkthroughOrigin
import com.kg.merapaisa.ui.offersThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The first-run themes page: which themes, in what pairs, and for whom. */
class WalkthroughThemesTest {

    @Test
    fun everyThemeOnceInTheWidgetsPairs() {
        val pairs = themePairs()
        assertEquals(5, pairs.size)
        assertEquals(themes.map { it.name }.toSet(), pairs.flatMap { listOf(it.first.name, it.second.name) }.toSet())
        for ((dark, light) in pairs) {
            assertTrue("${dark.name} goes on the left", dark.isDark)
            assertFalse("${light.name} goes on the right", light.isDark)
            assertEquals(light.name, partnerOf(dark).name)
        }
        assertEquals(listOf("Diya", "Jamun", "Monsoon", "Kaapi", "Kamal"), pairs.map { it.first.name })
    }

    @Test
    fun onlyAFirstRunStartsWithTheThemes() {
        assertTrue(WalkthroughOrigin.FirstRun.offersThemes)
        // Settings has Theme; an existing user comes by What's new.
        assertFalse(WalkthroughOrigin.Settings.offersThemes)
        assertFalse(WalkthroughOrigin.WhatsNew.offersThemes)
    }
}
