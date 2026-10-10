package com.kg.merapaisa.ui.gallery

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kg.merapaisa.themes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Screenshots the sheets and dialogs, which [DesignGalleryTest] cannot see.
 *
 * A ModalBottomSheet or an AlertDialog opens a window of its own, so `onRoot()` finds two roots
 * and `captureToImage` of the first shows only what is underneath. This captures the whole display
 * through UiAutomation instead, which is what a person sees: the sheet or the dialog, over the
 * scrim, over the themed background, status bar and all.
 *
 * The cases live in [GalleryWindowCases]. Run it with scripts/run-gallery.sh, or on its own:
 *   adb shell am instrument -w -e class com.kg.merapaisa.ui.gallery.WindowGalleryTest \
 *     com.kg.merapaisa.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class WindowGalleryTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outputDir: File by lazy {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = ctx.getExternalFilesDir("gallery") ?: File(ctx.filesDir, "gallery")
        dir.mkdirs()
        dir
    }

    @Test
    fun captureEverySheetAndDialog() = capture(fontScale = 1f, label = "")

    /**
     * Every sheet and dialog whose words or buttons can wrap, at double type.
     *
     * DeviceConfigurationOverride alone does not reach these: a sheet or dialog is its own window,
     * which reads its density from the activity, so the first run at "double type" produced images
     * identical to the normal ones. The scale is set on the activity's configuration as well.
     */
    @Test
    fun captureLargeTypeSheets() = capture(
        fontScale = 2f,
        label = "-fs2",
        only = setOf(
            "sheet-edit-entry", "dialog-delete-person", "sheet-add-person", "sheet-reminder", "sheet-settle-up",
            "sheet-theme-trying", "sheet-move-debt", "dialog-convert-currency", "sheet-edit-person",
            "sheet-create-group", "sheet-edit-group", "sheet-add-expense", "sheet-share",
            "dialog-replace-ledger"
        )
    )

    private fun capture(fontScale: Float, label: String, only: Set<String>? = null) {
        val cases = GalleryWindowCases.filter { only == null || it.first in only }
        val originalScale = compose.activity.resources.configuration.fontScale
        if (fontScale != 1f) setActivityFontScale(fontScale)
        try {
            captureCases(cases, fontScale, label)
        } finally {
            // Put it back. The resources are shared by every test in this process, so leaving
            // double type set made the next class lay out at double type too: the group screen
            // test found its expense below the fold and failed only when run after this one.
            if (fontScale != 1f) setActivityFontScale(originalScale)
        }
    }

    private fun setActivityFontScale(scale: Float) = compose.runOnUiThread {
        val resources = compose.activity.resources
        val config = android.content.res.Configuration(resources.configuration).apply {
            this.fontScale = scale
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun captureCases(
        cases: List<Pair<String, @Composable () -> Unit>>,
        fontScale: Float,
        label: String
    ) {
        val themeState = mutableStateOf(themes.first())
        val caseState = mutableStateOf<(@Composable () -> Unit)?>(null)

        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                GalleryFrame(themeState.value) { caseState.value?.invoke() }
            }
        }

        val written = mutableListOf<String>()
        for (theme in themes) {
            for ((name, case) in cases) {
                // Close the previous window first, so each capture opens its sheet from nothing
                // rather than swapping content inside one that is already up.
                caseState.value = null
                compose.waitForIdle()

                themeState.value = theme
                caseState.value = case
                compose.waitForIdle()
                // Compose's own animations are done once it is idle, but the window manager fades
                // a new window in on its own clock, which the test clock does not drive.
                SystemClock.sleep(WINDOW_SETTLE_MS)

                val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    ?: error("no screenshot for $name in ${theme.name}")
                val file = File(outputDir, "${theme.name.lowercase()}-$name$label.png")
                FileOutputStream(file).use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                written += file.name
            }
        }
        caseState.value = null
        compose.waitForIdle()

        check(written.size == themes.size * cases.size) {
            "expected ${themes.size * cases.size} captures, wrote ${written.size}"
        }
    }

    private companion object {
        const val WINDOW_SETTLE_MS = 600L
    }
}
