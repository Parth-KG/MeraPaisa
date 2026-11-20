package com.kg.merapaisa.ui.walkthrough

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every arrow on every page has something to point at, at normal and at double type: its part
 * was found, sits on the page, and has its callout above or below it, and no arrow runs into
 * another arrow or another callout's words.
 *
 * A component that loses its name, or a page whose layout moves a part to the wrong side of its
 * callout, fails here rather than drawing an arrow at nothing.
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughTargetsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun atNormalType() = checkEveryPage(1f)

    @Test
    fun atDoubleType() = checkEveryPage(2f)

    private fun checkEveryPage(fontScale: Float) {
        val pageIndex = mutableIntStateOf(0)
        val targets = mutableStateOf(TourTargets())
        var root: LayoutCoordinates? = null
        val theme = themes.first()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                MeraPaisaTheme(theme) {
                    CompositionLocalProvider(LocalAppTheme provides theme) {
                        Box(Modifier.fillMaxSize().onGloballyPositioned { root = it }) {
                            androidx.compose.runtime.key(pageIndex.intValue) {
                                TourPageContent(TourPages[pageIndex.intValue], targets = targets.value)
                            }
                        }
                    }
                }
            }
        }

        for (i in TourPages.indices) {
            compose.runOnIdle {
                targets.value = TourTargets()
                pageIndex.intValue = i
            }
            compose.waitForIdle()
            compose.runOnIdle { checkPage(TourPages[i], targets.value, root!!, fontScale) }
        }
    }

    private fun checkPage(page: TourPage, targets: TourTargets, root: LayoutCoordinates, fontScale: Float) {
        val where = "${page.title} at ${fontScale}x"
        val width = root.size.width.toFloat()
        val ring = 4f * compose.density.density
        val arrows = page.callouts.map { callout ->
            val target = targets.boundsIn(root, callout.target)
            assertNotNull("$where: nothing named ${callout.target}", target)
            val from = targets.boundsIn(root, CalloutKey(callout.target))
            assertNotNull("$where: the callout for ${callout.target} wasn't laid out", from)
            assertTrue("$where: ${callout.target} runs off the page", target!!.left >= 0f && target.right <= width)
            val arrow = arrowBetween(from!!, target.inflate(ring), inset = 12f, gap = 4f)
            assertNotNull("$where: the callout for ${callout.target} is level with it", arrow)
            Triple(callout, from, arrow!!)
        }
        for ((a, fromA, arrowA) in arrows) {
            for ((b, fromB, arrowB) in arrows) {
                if (a == b) continue
                assertFalse("$where: the callouts for ${a.target} and ${b.target} overlap", fromA.overlaps(fromB))
                assertFalse("$where: the arrows to ${a.target} and ${b.target} cross", arrowA.crosses(arrowB))
                assertFalse("$where: the arrow to ${a.target} runs over the words for ${b.target}", arrowA.crosses(fromB))
            }
        }
    }
}
