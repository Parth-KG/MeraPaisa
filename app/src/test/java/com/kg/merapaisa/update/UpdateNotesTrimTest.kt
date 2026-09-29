package com.kg.merapaisa.update

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The punctuation a trimmed release note must not end on.
 *
 * Its own file because UpdateCheckTest predates the redesign, and new checks go in new files
 * rather than into tests that already existed.
 */
class UpdateNotesTrimTest {

    /**
     * The em dash in the trim set is behaviour, not prose.
     *
     * It is written as a unicode escape so no literal one sits in the source, which is a rule this
     * project enforces with a lint. Nothing pinned what it does, so removing the escape, or
     * "simplifying" it back to a literal and letting the lint strip it, would have changed what
     * release notes look like with nothing to catch it.
     *
     * The input is built so the cut lands immediately after the dash. A first attempt used a long
     * run of filler and then a dash, which put the dash past the 420 character window entirely:
     * the test passed with the trim removed, which is to say it asserted nothing.
     */
    @Test
    fun `a trim strips a dangling em dash rather than leaving it before the ellipsis`() {
        // 83 words of five characters is 415, so the dash sits at 415 and the space after it at
        // 416, which is the last space inside the window and therefore the cut.
        val notes = "Word ".repeat(83) + "\u2014 and then a good deal more text to force a trim"
        val body = UpdateCheck.plainNotes(notes).removeSuffix("…").trimEnd()

        assertTrue("the notes should have been trimmed", UpdateCheck.plainNotes(notes).endsWith("…"))
        assertTrue(
            "a cut must not leave a dangling em dash: '${body.takeLast(20)}'",
            !body.endsWith("\u2014")
        )
        assertTrue("and it should end on the word before it: '${body.takeLast(20)}'", body.endsWith("Word"))
    }

    /** The same for the other punctuation the cut trims, so the set is covered as a set. */
    @Test
    fun `a trim strips dangling punctuation of every kind in the set`() {
        listOf(",", ";", ":", "\u2014", "-").forEach { mark ->
            val notes = "Word ".repeat(83) + "$mark and then a good deal more text to force a trim"
            val body = UpdateCheck.plainNotes(notes).removeSuffix("…").trimEnd()
            assertTrue("a cut left a dangling '$mark': '${body.takeLast(20)}'", !body.endsWith(mark))
        }
    }
}
