package com.kg.merapaisa

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A theme name stored by an older version, read back through the real preference file.
 *
 * The theme is kept as its name, so a phone updated to v3.2.0 can be holding a name the app no
 * longer has: Midnight before its rename, Paper from a release before that, or Latte from a build
 * tried during the vote. Each has to open on a theme that exists, never on the default by accident.
 */
@RunWith(AndroidJUnit4::class)
class ThemeMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var before: String

    @Before
    fun remember() = runBlocking { before = ThemeStore.getTheme(context).first() }

    @After
    fun restore() = runBlocking { ThemeStore.setTheme(context, before) }

    private fun opensOn(stored: String): String = runBlocking {
        ThemeStore.setTheme(context, stored)
        getThemeByName(ThemeStore.getTheme(context).first()).name
    }

    @Test
    fun aPhoneThatChoseLatteOpensOnNeel() {
        assertEquals("Neel", opensOn("Latte"))
    }

    @Test
    fun aRenamedThemeIsKeptUnderItsNewName() {
        assertEquals("Diya", opensOn("Midnight"))
        assertEquals("Kamal", opensOn("Pine & Rose Quartz"))
        assertEquals("Gulab", opensOn("Sakura"))
    }

    @Test
    fun everyOldNameOpensOnATheme() {
        val old = listOf(
            "Midnight", "Pastelón Dark", "Mocha", "Espresso", "Pine & Rose Quartz", "Ledger",
            "Flexoki Light", "Sakura", "Lavender Bronze", "Latte", "Ultraviolet & Sand",
            "Turquoise & Burgundy", "Frappé", "Macchiato", "Paper", "Cream", "Purple", "Amoled",
            "Ocean", "Sunset", "Slate", "Charcoal", "Gold", "Rose", "Vibrant", "Neon"
        )
        val names = themes.map { it.name }
        old.forEach { stored ->
            val landed = opensOn(stored)
            assertTrue("$stored opened on $landed, which is not a theme", landed in names)
            // Only Midnight is meant to land on the default, because Diya is Midnight renamed.
            if (stored != "Midnight") {
                assertTrue("$stored fell through to the default", landed != themes.first().name)
            }
        }
    }
}
