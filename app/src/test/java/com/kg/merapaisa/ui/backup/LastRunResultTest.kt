package com.kg.merapaisa.ui.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which "last run" lines the backup screen draws as a success.
 *
 * The screen reads a stored sentence, so rewording the sentence silently changed its colour: after
 * "Backed up successfully." became "Saved a backup.", every good weekly backup showed in the
 * negative ink, which is the one colour meant to say it failed.
 */
class LastRunResultTest {

    @Test
    fun `today's success sentences are successes`() {
        assertTrue(lastRunSucceeded("Saved a backup."))
        assertTrue(lastRunSucceeded("Saved a backup. 1 older backup removed."))
        assertTrue(lastRunSucceeded("Saved a backup. 3 older backups removed."))
    }

    @Test
    fun `a sentence stored by an earlier version still counts`() {
        assertTrue(lastRunSucceeded("Backed up successfully."))
        assertTrue(lastRunSucceeded("Backed up successfully. 2 older removed."))
    }

    @Test
    fun `failures are not successes`() {
        assertFalse(lastRunSucceeded("Couldn't write to the backup folder, so nothing was saved."))
        assertFalse(lastRunSucceeded("This week's backup didn't finish. Mera Paisa will try again shortly."))
        assertFalse(lastRunSucceeded("No folder chosen, so nothing was backed up. Choose one first."))
    }
}
