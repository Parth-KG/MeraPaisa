package com.kg.merapaisa.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The check that decides whether a downloaded APK is allowed anywhere near the installer.
 *
 * This is the security-critical half of the updater. Everything else about in-app updating is a
 * convenience; this is what stops a hijacked download, a compromised host or a tampering proxy
 * turning an update into arbitrary code running as Mera Paisa, with the ledger.
 *
 * It needs a device, because it goes through `PackageManager`. It also needs two real APKs signed
 * with genuinely different keys. Those are pushed alongside the test rather than embedded, so the
 * test APK does not have to carry several megabytes of fixtures:
 *
 *     adb push merapaisa-v2.2.1.apk \
 *       /sdcard/Android/data/com.kg.merapaisa.debug/files/release-signed.apk
 *     adb push wrongly-signed.apk \
 *       /sdcard/Android/data/com.kg.merapaisa.debug/files/debug-signed.apk
 *
 * The running test app is the debug build, signed with the debug key. So the debug-signed APK must
 * be accepted and the release-signed one refused. That proves both directions rather than only
 * the happy one. A check that merely never accepts anything would pass a one-sided test.
 */
@RunWith(AndroidJUnit4::class)
class SignatureCheckTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun fixture(name: String): File? =
        context.getExternalFilesDir(null)?.let { File(it, name) }?.takeIf { it.isFile }

    @Test
    fun anApkSignedWithADifferentKeyIsRefused() {
        val apk = fixture("release-signed.apk")
        assumeTrue("push release-signed.apk to run this", apk != null)

        assertFalse(
            "a release-signed APK must be refused by the debug build: different key",
            UpdateInstaller.isSignedLikeThisApp(context, apk!!.absolutePath)
        )
    }

    @Test
    fun anApkSignedWithTheSameKeyIsAccepted() {
        val apk = fixture("debug-signed.apk")
        assumeTrue("push debug-signed.apk to run this", apk != null)

        assertTrue(
            "an APK signed with this build's own key must be accepted",
            UpdateInstaller.isSignedLikeThisApp(context, apk!!.absolutePath)
        )
    }

    /** Not an APK at all, so it cannot be read. Unreadable has to mean refused. */
    @Test
    fun somethingThatIsNotAnApkIsRefused() {
        val junk = File(context.cacheDir, "not-an-apk.apk").apply { writeText("this is not a zip") }
        try {
            assertFalse(UpdateInstaller.isSignedLikeThisApp(context, junk.absolutePath))
        } finally {
            junk.delete()
        }
    }

    @Test
    fun aMissingFileIsRefused() {
        assertFalse(
            UpdateInstaller.isSignedLikeThisApp(context, File(context.cacheDir, "nope.apk").absolutePath)
        )
    }

    /** An empty file is what an interrupted download leaves behind. */
    @Test
    fun anEmptyFileIsRefused() {
        val empty = File(context.cacheDir, "empty.apk").apply { writeText("") }
        try {
            assertFalse(UpdateInstaller.isSignedLikeThisApp(context, empty.absolutePath))
        } finally {
            empty.delete()
        }
    }
}
