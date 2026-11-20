package com.kg.merapaisa.ui.whatsnew

import com.kg.merapaisa.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lines the app ships for "What's new", and the guard that a version bump can't ship without
 * them: the newest entry has to be this build's version or the one being made.
 */
class WhatsNewLinesTest {

    @Test
    fun theVersionBeingBuiltHasItsLines() {
        val newest = RELEASE_LINES.last()
        assertTrue(
            "versionCode ${BuildConfig.VERSION_CODE} has no entry in WhatsNewLines.kt. Write this " +
                "version's lines there (or an entry with none) before the bump (docs/RELEASE.md).",
            newest.versionCode >= BuildConfig.VERSION_CODE
        )
        RELEASE_LINES.firstOrNull { it.versionCode == BuildConfig.VERSION_CODE }?.let {
            assertEquals(BuildConfig.VERSION_NAME.substringBefore('-'), it.versionName)
        }
    }

    @Test
    fun entriesRunOldestFirstAndEachSaysLittle() {
        RELEASE_LINES.zipWithNext().forEach { (a, b) -> assertTrue(a.versionCode < b.versionCode) }
        assertEquals(RELEASE_LINES.size, RELEASE_LINES.map { it.versionName }.toSet().size)
        for (release in RELEASE_LINES) {
            assertTrue(
                "${release.versionName} needs none, or 2 to 4 lines",
                release.lines.isEmpty() || release.lines.size in 2..MAX_WHATS_NEW_LINES
            )
            for (line in release.lines) {
                assertTrue("\"$line\" is ${line.length} characters", line.length <= MAX_WHATS_NEW_LINE_CHARS)
                assertFalse(line.contains('!'))
                assertFalse(line.contains('\u2014'))
            }
        }
    }

    // -- how the sheet chooses ------------------------------------------------------------

    private fun release(code: Int, vararg lines: String, walkthrough: Boolean = false) =
        ReleaseLines(code, "3.$code.0", lines.toList(), walkthrough)

    @Test
    fun oneVersionShowsItsOwnLines() {
        val shown = whatsNewSince(24, listOf(release(24, "old a", "old b"), release(25, "a", "b")))!!
        assertEquals(listOf("a", "b"), shown.lines)
        assertEquals("3.25.0", shown.versionName)
        assertEquals(25, shown.newestCode)
    }

    @Test
    fun skippedVersionsTakeEachFirstLineBeforeAnySecond() {
        val releases = listOf(
            release(25, "25a", "25b"),
            release(26, "26a", "26b", "26c"),
            release(27, "27a", "27b")
        )
        assertEquals(listOf("27a", "26a", "25a", "27b"), whatsNewSince(24, releases)!!.lines)
    }

    @Test
    fun aVersionWithNothingToTellAddsNothing() {
        val releases = listOf(release(25, "a", "b"), release(26))
        val shown = whatsNewSince(24, releases)!!
        assertEquals(listOf("a", "b"), shown.lines)
        assertEquals("3.25.0", shown.versionName)
        assertEquals("seen up to the newest, told or not", 26, shown.newestCode)
        assertNull(whatsNewSince(25, releases))
    }

    @Test
    fun nothingNewerMeansNoSheet() {
        assertNull(whatsNewSince(25, listOf(release(25, "a", "b"))))
        assertNull(whatsNewSince(40, listOf(release(25, "a", "b"))))
    }

    @Test
    fun theWalkthroughIsOfferedWhenAnyVersionShownOffersIt() {
        val releases = listOf(release(25, "a", "b", walkthrough = true), release(26, "c", "d"))
        assertTrue(whatsNewSince(24, releases)!!.offersWalkthrough)
        assertFalse(whatsNewSince(25, releases)!!.offersWalkthrough)
    }

    @Test
    fun v380OffersTheWalkthrough() {
        val shown = whatsNewSince(24)!!
        assertEquals("3.8.0", shown.versionName)
        assertTrue(shown.offersWalkthrough)
    }
}
