package com.kg.merapaisa.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deciding whether a newer release exists.
 *
 * This runs on the JVM because it parses with `data/Json.kt` rather than `org.json`. A piece of
 * code whose answer decides whether an APK gets installed is worth testing without a device.
 *
 * The payloads below are shaped like real GitHub release responses, trimmed to the fields read.
 */
class UpdateCheckTest {

    private fun release(
        tag: String = "v2.3.0",
        assets: String = """[{"name": "merapaisa-v2.3.0.apk", "size": 3601281,
             "browser_download_url": "https://github.com/Parth-KG/MeraPaisa/releases/download/v2.3.0/merapaisa-v2.3.0.apk"}]""",
        draft: Boolean = false,
        prerelease: Boolean = false,
        body: String = "Fixes and an updater."
    ) = """{"tag_name": "$tag", "draft": $draft, "prerelease": $prerelease,
            "body": "$body", "assets": $assets}"""

    // ---------------------------------------------------------------------------------------
    // Version comparison
    // ---------------------------------------------------------------------------------------

    /** The one string comparison gets backwards, and the reason this is not `a > b`. */
    @Test
    fun `2_10_0 is newer than 2_9_0`() {
        assertTrue(UpdateCheck.compareVersions("2.10.0", "2.9.0") > 0)
        assertTrue(UpdateCheck.compareVersions("2.9.0", "2.10.0") < 0)
    }

    @Test
    fun `ordinary comparisons`() {
        assertTrue(UpdateCheck.compareVersions("2.3.0", "2.2.1") > 0)
        assertTrue(UpdateCheck.compareVersions("2.2.1", "2.3.0") < 0)
        assertEquals(0, UpdateCheck.compareVersions("2.2.1", "2.2.1"))
        assertTrue(UpdateCheck.compareVersions("3.0.0", "2.99.99") > 0)
    }

    @Test
    fun `missing components count as zero`() {
        assertEquals(0, UpdateCheck.compareVersions("2.2", "2.2.0"))
        assertTrue(UpdateCheck.compareVersions("2.2.1", "2.2") > 0)
    }

    @Test
    fun `a v prefix and surrounding space do not matter`() {
        assertEquals(0, UpdateCheck.compareVersions(" 2.2.1 ", "2.2.1"))
    }

    /** A malformed tag must make the app decide "no update" quietly, never crash on launch. */
    @Test
    fun `a nonsense version sorts as zero rather than throwing`() {
        assertEquals(0, UpdateCheck.compareVersions("banana", "0.0.0"))
        assertTrue(UpdateCheck.compareVersions("2.2.1", "banana") > 0)
        assertEquals(0, UpdateCheck.compareVersions("", ""))
    }

    @Test
    fun `a suffixed version compares on its numeric part`() {
        assertEquals(0, UpdateCheck.compareVersions("2.2.1-debug", "2.2.1"))
    }

    // ---------------------------------------------------------------------------------------
    // Interpreting a release
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a newer release is offered, with its apk`() {
        val status = UpdateCheck.interpret(release(), "2.2.1")
        assertTrue("expected Available, got $status", status is UpdateStatus.Available)
        status as UpdateStatus.Available
        assertEquals("2.3.0", status.version)
        assertTrue(status.downloadUrl.endsWith(".apk"))
        assertEquals(3_601_281L, status.sizeBytes)
    }

    @Test
    fun `the same version is up to date`() {
        assertEquals(UpdateStatus.UpToDate, UpdateCheck.interpret(release(tag = "v2.2.1"), "2.2.1"))
    }

    /** A debug build ahead of any release must not be told to downgrade. */
    @Test
    fun `an older release is up to date`() {
        assertEquals(UpdateStatus.UpToDate, UpdateCheck.interpret(release(tag = "v2.0.0"), "2.2.1"))
    }

    @Test
    fun `drafts and prereleases are ignored`() {
        assertEquals(UpdateStatus.UpToDate, UpdateCheck.interpret(release(draft = true), "2.2.1"))
        assertEquals(UpdateStatus.UpToDate, UpdateCheck.interpret(release(prerelease = true), "2.2.1"))
    }

    /** GitHub attaches source tarballs to every release; only the APK is installable. */
    @Test
    fun `a release with no apk is reported rather than offered`() {
        val assets = """[{"name": "Source code.zip", "size": 1000,
            "browser_download_url": "https://github.com/x/y/archive/v2.3.0.zip"}]"""
        val status = UpdateCheck.interpret(release(assets = assets), "2.2.1")
        assertTrue("expected Unreachable, got $status", status is UpdateStatus.Unreachable)
        assertTrue((status as UpdateStatus.Unreachable).reason.contains("APK"))
    }

    @Test
    fun `the apk is picked out from among other assets`() {
        val assets = """[
            {"name": "Source code.zip", "size": 1, "browser_download_url": "https://x/y.zip"},
            {"name": "merapaisa-v2.3.0.apk", "size": 42, "browser_download_url": "https://x/y.apk"},
            {"name": "notes.txt", "size": 2, "browser_download_url": "https://x/n.txt"}
        ]"""
        val status = UpdateCheck.interpret(release(assets = assets), "2.2.1") as UpdateStatus.Available
        assertEquals("https://x/y.apk", status.downloadUrl)
        assertEquals(42L, status.sizeBytes)
    }

    // ---------------------------------------------------------------------------------------
    // Failures, all of which must read as "couldn't check", never as a fault
    // ---------------------------------------------------------------------------------------

    @Test
    fun `no response is unreachable`() {
        assertTrue(UpdateCheck.interpret(null, "2.2.1") is UpdateStatus.Unreachable)
    }

    @Test
    fun `junk is unreachable rather than a crash`() {
        assertTrue(UpdateCheck.interpret("not json", "2.2.1") is UpdateStatus.Unreachable)
        assertTrue(UpdateCheck.interpret("", "2.2.1") is UpdateStatus.Unreachable)
        assertTrue(UpdateCheck.interpret("[]", "2.2.1") is UpdateStatus.Unreachable)
    }

    /** GitHub answers a rate limit with a JSON object that has no tag_name. */
    @Test
    fun `a rate-limit response is unreachable`() {
        val body = """{"message": "API rate limit exceeded", "documentation_url": "https://docs.github.com"}"""
        assertTrue(UpdateCheck.interpret(body, "2.2.1") is UpdateStatus.Unreachable)
    }

    @Test
    fun `a truncated response is unreachable`() {
        val full = release()
        assertTrue(UpdateCheck.interpret(full.take(full.length / 2), "2.2.1") is UpdateStatus.Unreachable)
    }

    // ---------------------------------------------------------------------------------------
    // Release notes
    // ---------------------------------------------------------------------------------------

    /** GitHub returns markdown; the dialog is a plain Text. Raw syntax on screen looks broken. */
    @Test
    fun `emphasis and heading markers are stripped`() {
        assertEquals("Bold thing", UpdateCheck.plainNotes("**Bold thing**"))
        assertEquals("A heading", UpdateCheck.plainNotes("## A heading"))
        assertEquals("emphasis", UpdateCheck.plainNotes("*emphasis*"))
        assertEquals("under", UpdateCheck.plainNotes("_under_"))
    }

    /** The superseded banners on older releases are GitHub callouts, and read as noise. */
    @Test
    fun `callout markers are dropped and their text kept`() {
        val notes = "> [!IMPORTANT]\n> **Superseded by v2.2.1.**\n> Install that instead."
        val out = UpdateCheck.plainNotes(notes)
        assertTrue(out.contains("Superseded by v2.2.1."))
        assertTrue(out.contains("Install that instead."))
        assertFalse(out.contains("[!IMPORTANT]"))
        assertFalse(out.contains(">"))
    }

    /**
     * "Left alone" now means the words are untouched, not the line breaks. Since v2.3 wrapped
     * prose is reflowed, because a break inside a paragraph is an artefact of how the notes were
     * typed, not something the reader should see on a narrow screen.
     */
    @Test
    fun `the words of ordinary prose are untouched`() {
        assertEquals(
            "Fixes a crash when adding an expense. Also: faster startup.",
            UpdateCheck.plainNotes("Fixes a crash when adding an expense.\nAlso: faster startup.")
        )
        val single = "One line, nothing to do."
        assertEquals(single, UpdateCheck.plainNotes(single))
    }

    /** An underscore inside a word is not emphasis: version_name must survive. */
    @Test
    fun `underscores inside words are not treated as emphasis`() {
        assertEquals("version_name stays", UpdateCheck.plainNotes("version_name stays"))
    }

    @Test
    fun `blank runs collapse rather than leaving gaps`() {
        assertEquals("a\n\nb", UpdateCheck.plainNotes("a\n\n\n\nb"))
    }

    @Test
    fun `empty notes stay empty`() {
        assertEquals("", UpdateCheck.plainNotes(""))
        assertEquals("", UpdateCheck.plainNotes("   \n\n  "))
    }

    /**
     * The one that actually looked broken on a phone: release notes are hard-wrapped at ~95
     * characters, so every source line break became a real break at a random-looking point.
     */
    @Test
    fun `hard-wrapped paragraphs are reflowed into one line`() {
        val wrapped = "Fixes from the first proper test run on a real phone. If you use share links,\n" +
            "this one is worth installing."
        assertEquals(
            "Fixes from the first proper test run on a real phone. If you use share links, this one is worth installing.",
            UpdateCheck.plainNotes(wrapped)
        )
    }

    @Test
    fun `blank lines still separate paragraphs`() {
        val notes = "First paragraph\nwrapped oddly.\n\nSecond paragraph\nalso wrapped."
        assertEquals("First paragraph wrapped oddly.\n\nSecond paragraph also wrapped.", UpdateCheck.plainNotes(notes))
    }

    /** A bullet's line break carries meaning, so it must survive the reflow. */
    @Test
    fun `list items keep their own lines`() {
        val notes = "Changes:\n- first thing\n- second thing"
        val out = UpdateCheck.plainNotes(notes)
        assertTrue(out.contains("- first thing\n- second thing"))
    }

    @Test
    fun `numbered items and table rows keep their lines`() {
        assertTrue(UpdateCheck.plainNotes("1. one\n2. two").contains("1. one\n2. two"))
        assertTrue(UpdateCheck.plainNotes("| a | b |\n| c | d |").contains("| a | b |\n| c | d |"))
    }

    /** The real v2.2.1 notes, which is what prompted this. */
    @Test
    fun `a realistic release body comes out readable`() {
        val body = "Fixes from the first proper test run on a real phone. **If you use share links, this one is worth\n" +
            "installing.**\n\n" +
            "**A crafted link could tamper with the screen that warns you about it**\n\n" +
            "When someone sends you a ledger update, Mera Paisa shows you what it would record and reminds you\n" +
            "that anyone can make one of these links.\n\n" +
            "- the sender's name now sits on its own\n" +
            "- quote characters are stripped from it\n"
        val out = UpdateCheck.plainNotes(body)

        assertFalse("no markdown emphasis should survive", out.contains("**"))
        assertTrue("the first paragraph must be one line",
            out.startsWith("Fixes from the first proper test run on a real phone. If you use share links, this one is worth installing."))
        assertTrue("bullets keep their breaks", out.contains("- the sender's name now sits on its own\n- quote characters are stripped from it"))
        assertFalse("no stray mid-sentence break", out.contains("worth\ninstalling"))
        assertFalse(out.contains("reminds you\nthat anyone"))
    }

    /** A wrapped bullet is one bullet. Splitting it strands half a sentence below a gap. */
    @Test
    fun `a wrapped bullet stays one bullet`() {
        val notes = "- the sender's name now sits on its own, in a box,\n  under a label"
        val out = UpdateCheck.plainNotes(notes)
        assertEquals("- the sender's name now sits on its own, in a box, under a label", out)
    }

    @Test
    fun `a blank line ends a bullet list`() {
        val out = UpdateCheck.plainNotes("- one\n- two\n\nA new paragraph\nwrapped.")
        assertTrue(out.contains("- one\n- two"))
        assertTrue(out.contains("A new paragraph wrapped."))
    }

    // ---------------------------------------------------------------------------------------
    // Length
    // ---------------------------------------------------------------------------------------

    /** Full notes live on the releases page; the dialog only needs enough to decide by. */
    @Test
    fun `very long notes are trimmed`() {
        val long = ("Sentence number one is here. ".repeat(80))
        val out = UpdateCheck.plainNotes(long)
        assertTrue("should be trimmed, was ${out.length}", out.length < 500)
        assertTrue("a trim must be visible", out.endsWith("…"))
    }

    @Test
    fun `a trim never cuts mid-word`() {
        val out = UpdateCheck.plainNotes("supercalifragilistic ".repeat(60))
        val body = out.removeSuffix("…").trimEnd()
        assertTrue("ended mid-word: '${body.takeLast(25)}'",
            body.isEmpty() || body.endsWith("supercalifragilistic"))
    }

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

    @Test
    fun `short notes are not trimmed and gain no ellipsis`() {
        val short = "One small fix."
        assertEquals(short, UpdateCheck.plainNotes(short))
    }

    /** The real v2.2.1 body, which is what looked broken on the phone. */
    @Test
    fun `the real release notes come out readable and bounded`() {
        val body = "Fixes from the first proper test run on a real phone. **If you use share links, this one is worth\n" +
            "installing.**\n\n" +
            "**A crafted link could tamper with the screen that warns you about it**\n\n" +
            "When someone sends you a ledger update, Mera Paisa shows you what it would record and reminds you\n" +
            "that anyone can make one of these links. The name in the link is chosen by whoever built it.\n\n" +
            "- the sender's name now sits on its own, in a box,\n" +
            "  under \"This link says it is from\"\n" +
            "- quote characters are stripped from it\n"
        val out = UpdateCheck.plainNotes(body)

        assertFalse(out.contains("**"))
        assertFalse("no mid-sentence break", out.contains("worth\ninstalling"))
        assertFalse("no mid-sentence break", out.contains("reminds you\nthat anyone"))
        assertTrue("bounded for a dialog, was ${out.length}", out.length <= 460)
    }
}
