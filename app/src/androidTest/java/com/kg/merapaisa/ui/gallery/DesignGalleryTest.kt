package com.kg.merapaisa.ui.gallery

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kg.merapaisa.AppTheme
import com.kg.merapaisa.themes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Screenshots every gallery case in every theme, so a redesign can be judged by looking rather
 * than by reading diffs.
 *
 * This asserts almost nothing. It is a camera, not a test: the point is the PNGs it leaves on the
 * device. What it does guarantee is that every case composes without throwing in every theme,
 * which is worth having on its own, and it fails loudly if a case renders an empty frame.
 *
 * Run it, with the sheets, dialogs and the widget in day and night, using scripts/run-gallery.sh,
 * or on its own:
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.kg.merapaisa.ui.gallery.DesignGalleryTest
 *
 * Then collect the images:
 *   adb pull /sdcard/Android/data/com.kg.merapaisa.debug/files/gallery build/gallery
 *
 * If that path is denied, the app's own sandbox still has them:
 *   adb exec-out run-as com.kg.merapaisa.debug tar c files/gallery | tar x -C build/
 *
 * One case renders one screen's worth, so [GalleryCases] rather than this file decides what gets
 * reviewed. Everything here walks that list.
 */
@RunWith(AndroidJUnit4::class)
class DesignGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private val outputDir: File by lazy {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = ctx.getExternalFilesDir("gallery") ?: File(ctx.filesDir, "gallery")
        dir.mkdirs()
        dir
    }

    /** Every case, every theme, at the normal font size. */
    @Test
    fun captureEveryCaseInEveryTheme() = capture(fontScale = 1f, label = "")

    /**
     * The same again at double type. Amount columns and rows built around a fixed height are
     * where large text breaks first, and it breaks silently: nothing throws, the figure is just
     * clipped. Restricted to the screens where that matters most: Balances, the split's shares,
     * a group, a person's history, and Settings with its themes open.
     */
    @Test
    fun captureLargeTypeCases() = capture(
        fontScale = 2f,
        label = "-fs2",
        only = setOf(
            "people-rows", "net-total-multi", "net-total-single", "numpad", "group-detail",
            "split-adjustments", "split-adjustments-locked", "history", "settings-themes-open"
        )
    )

    private fun capture(fontScale: Float, label: String, only: Set<String>? = null) {
        val cases = GalleryCases.filter { only == null || it.first in only }
        val themeState = mutableStateOf(themes.first())
        val caseState = mutableStateOf(cases.first())

        // One setContent for the whole run. createComposeRule allows only one, and swapping two
        // pieces of state is far quicker than a fresh rule per image.
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                GalleryFrame(themeState.value) { caseState.value.second() }
            }
        }

        val empties = mutableListOf<String>()
        for (theme in themes) {
            for (case in cases) {
                themeState.value = theme
                caseState.value = case
                compose.waitForIdle()

                val name = "${theme.name.lowercase()}-${case.first}$label.png"
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                FileOutputStream(File(outputDir, name)).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                if (isBlank(bitmap, theme)) empties += name
            }
        }

        check(empties.isEmpty()) {
            "these rendered as a flat background, so the case drew nothing:\n" +
                empties.joinToString("\n") { "  $it" }
        }
    }

    /**
     * True when every sampled pixel is the theme's background.
     *
     * A case that silently draws nothing still produces a valid PNG, and a folder of plausible
     * looking empty frames is worse than a failure. Sampling a grid is enough: any real content
     * puts something other than the background somewhere.
     */
    private fun isBlank(bitmap: android.graphics.Bitmap, theme: AppTheme): Boolean {
        val bg = theme.background
        val target = android.graphics.Color.rgb(
            (bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt()
        )
        // Every third pixel, not a coarse grid. A 24 by 24 grid reported the Settled empty state
        // as blank in all six themes: its two short centred lines are thin enough that 576 sample
        // points all landed between the glyphs. The image was fine; the sampler was not.
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                if (bitmap.getPixel(x, y) != target) return false
                y += 3
            }
            x += 3
        }
        return true
    }
}
