package com.kg.merapaisa.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kg.merapaisa.data.netTotalsByCurrency
import com.kg.merapaisa.getThemeByName
import com.kg.merapaisa.themes
import com.kg.merapaisa.widget.LockedWidgetContent
import com.kg.merapaisa.widget.WidgetContent
import com.kg.merapaisa.widget.WidgetPalette
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Screenshots the home screen widget, which the Compose gallery cannot reach.
 *
 * Glance does not compose into the view tree the way the rest of the app does. It produces a
 * `RemoteViews`, which a launcher inflates in its own process, so `captureToImage` has nothing to
 * capture. `GlanceRemoteViews` composes one here instead, and the result is inflated into an
 * ordinary FrameLayout, measured, laid out and drawn to a bitmap.
 *
 * Without this the widget was the one surface being changed with nobody looking at it.
 *
 * Run it:
 *   adb shell am instrument -w -e class com.kg.merapaisa.ui.gallery.WidgetGalleryTest \
 *     com.kg.merapaisa.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetGalleryTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val outputDir: File by lazy {
        val dir = context.getExternalFilesDir("gallery") ?: File(context.filesDir, "gallery")
        dir.mkdirs()
        dir
    }

    /**
     * Whichever mode the device is actually in.
     *
     * A day and night colour reaches the launcher as a pair and is resolved there, at inflation,
     * from the launcher's own configuration. Composing against an overridden context does not move
     * it: that produced two byte-identical files labelled day and night. So the capture names
     * itself after the real device setting, and the run script flips the device between passes.
     */
    private val mode: String
        get() = if (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        ) "night" else "day"

    private val people = Fixtures.mixedPeople.filter { it.balanceMinor != 0L }
    private val totals = netTotalsByCurrency(people)

    private val sizes = mapOf(
        "strip" to DpSize(250.dp, 60.dp),
        "tall" to DpSize(300.dp, 200.dp)
    )

    /**
     * Every theme, at both declared sizes, in whichever mode the phone is in.
     *
     * A widget picks up the launcher's night mode rather than the app's, which is why the palette
     * carries two themes and why both have to be looked at. A theme that reads on one and not the
     * other would never show up in a single capture, so scripts/run-gallery.sh runs this twice.
     */
    @Test
    fun captureTheWidget() {
        val written = mutableListOf<String>()
        // Straight from the list the app offers, so a theme added or retired is drawn or dropped
        // here without anyone remembering to. Named themes once outlived their removal and were
        // quietly drawn as whatever their old name resolved to.
        themes.forEach { theme ->
            val palette = paletteLike(theme)
            sizes.forEach { (sizeName, size) ->
                written += shoot("${theme.name.lowercase()}-widget-$sizeName-$mode", size) {
                    WidgetContent(persons = people, totals = totals, palette = palette)
                }
            }
        }

        // The locked state shows nothing of the ledger, which is the whole point of it, so it is
        // worth a picture of its own to confirm it really shows nothing.
        written += shoot("neel-widget-locked-$mode", sizes.getValue("tall")) {
            LockedWidgetContent(paletteLike(getThemeByName("Neel")))
        }

        check(written.isNotEmpty()) { "no widget images were written" }
    }

    /**
     * The widget at double type, in Neel, Diya and Monsoon.
     *
     * The launcher inflates the widget with the phone's own font scale, so the scale goes on the
     * context the RemoteViews are composed and inflated with. A row that only fits at 1.0 clips
     * its figure here instead of on someone's home screen.
     */
    @Test
    fun captureTheWidgetAtDoubleType() {
        val large = context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).apply { fontScale = 2f }
        )
        val written = mutableListOf<String>()
        listOf("Neel", "Diya", "Monsoon").forEach { name ->
            val palette = paletteLike(getThemeByName(name))
            sizes.forEach { (sizeName, size) ->
                written += shoot("${name.lowercase()}-widget-$sizeName-$mode-fs2", size, large) {
                    WidgetContent(persons = people, totals = totals, palette = palette)
                }
            }
        }
        check(written.isNotEmpty()) { "no widget images were written" }
    }

    /**
     * The pairing DebtWidget builds for a chosen theme.
     *
     * A dark choice keeps Neel for the launcher's light mode; a light choice keeps Diya for
     * its dark mode. Mirrored here because the widget's own helper is private, and because a
     * capture that invented its own pairing would not be showing what ships.
     */
    private fun paletteLike(selected: com.kg.merapaisa.AppTheme) =
        if (selected.isDark) WidgetPalette(day = getThemeByName("Neel"), night = selected)
        else WidgetPalette(day = selected, night = getThemeByName("Diya"))

    /** Composes Glance content to a RemoteViews, inflates it, and draws it to a PNG. */
    private fun shoot(
        name: String,
        size: DpSize,
        themed: Context = context,
        content: @Composable () -> Unit
    ): String {
        val remoteViews = runBlocking {
            GlanceRemoteViews().compose(context = themed, size = size, content = content)
        }.remoteViews

        val density = context.resources.displayMetrics.density
        val widthPx = (size.width.value * density).toInt()
        val heightPx = (size.height.value * density).toInt()

        lateinit var bitmap: Bitmap
        // Inflation and layout touch the view hierarchy, so they belong on the main thread.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val host = FrameLayout(themed)
            val view: View = remoteViews.apply(themed, host)
            host.addView(view, ViewGroup.LayoutParams(widthPx, heightPx))
            host.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            )
            host.layout(0, 0, widthPx, heightPx)
            bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            host.draw(Canvas(bitmap))
        }

        val file = File(outputDir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.name
    }
}
