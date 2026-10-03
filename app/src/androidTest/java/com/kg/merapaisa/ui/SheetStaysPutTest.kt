package com.kg.merapaisa.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * A sheet stays where it is while what is inside it is flung.
 *
 * Material's sheet hands the speed left at the end of a fling to its own settle, which springs the
 * whole sheet past its place and back, up to 300 px, at either end of a list. And a sheet about as
 * tall as the screen moved its own resting place as it moved, so one fling set New group bouncing
 * until the screen was touched. Every other test stayed green through both; the phone showed them
 * in a second.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class SheetStaysPutTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(sheetContent: @Composable () -> Unit) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) { sheetContent() }
            }
        }
        compose.waitForIdle()
    }

    /** Where the sheet is: the top of its drag handle. */
    private fun sheetTop(): Float =
        compose.onNodeWithContentDescription("Drag handle").fetchSemanticsNode().boundsInWindow.top

    /** Steps the clock [frames] frames and returns the furthest the sheet got from [rest]. */
    private fun furthestFrom(rest: Float, frames: Int): Float {
        var furthest = 0f
        repeat(frames) {
            compose.mainClock.advanceTimeByFrame()
            furthest = maxOf(furthest, abs(sheetTop() - rest))
        }
        return furthest
    }

    @Test
    fun flingingAListIntoEitherEndLeavesTheSheetWhereItIs() {
        lateinit var scroll: ScrollState
        // Where the sheet's first line sits, after every layout of it: the sheet moving shows here.
        val positions = mutableListOf<Float>()
        show {
            SheetFrame(
                onDismissRequest = {},
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                scroll = rememberScrollState()
                Text(
                    "Who else is in it?",
                    Modifier.onGloballyPositioned { positions += it.positionInWindow().y }
                )
                Column(Modifier.weight(1f, fill = false).verticalScroll(scroll).testTag("list")) {
                    repeat(16) { Text("Person $it", Modifier.fillMaxWidth().height(48.dp)) }
                }
                Text("At the foot")
            }
        }
        val rest = compose.runOnIdle { positions.last() }
        val list = compose.onNodeWithTag("list")

        fun furthestDuring(swipe: () -> Unit): Float {
            compose.runOnIdle { positions.clear() }
            swipe()
            compose.waitForIdle()
            return compose.runOnIdle { positions.maxOfOrNull { abs(it - rest) } ?: 0f }
        }

        // The drag stays inside the list, so only the fling runs into an end.
        val reachingTheEnd = furthestDuring {
            list.performTouchInput {
                swipeWithVelocity(Offset(centerX, centerY + 100f), Offset(centerX, centerY - 100f), endVelocity = 4000f)
            }
        }
        assertTrue("the fling never reached the end of the list", compose.runOnIdle { scroll.value == scroll.maxValue })
        val reachingTheTop = furthestDuring {
            list.performTouchInput {
                swipeWithVelocity(Offset(centerX, centerY - 100f), Offset(centerX, centerY + 100f), endVelocity = 4000f)
            }
        }
        assertTrue("the fling never reached the top of the list", compose.runOnIdle { scroll.value == 0 })

        assertTrue("a fling to the end of the list moved the sheet $reachingTheEnd px", reachingTheEnd <= 1f)
        assertTrue("a fling to the top of the list moved the sheet $reachingTheTop px", reachingTheTop <= 1f)
    }

    /**
     * New group with six people on a 2400 px phone: the sheet ends 11 px short of the screen. One
     * fling used to start a bounce of 400 px, four times a second, that never ended.
     */
    @Test
    fun aSheetNearlyAsTallAsTheScreenComesBackToRest() {
        show {
            SheetFrame(
                onDismissRequest = {},
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                val density = LocalDensity.current
                val screen = LocalWindowInfo.current.containerSize.height
                val bars = WindowInsets.safeDrawing.getTop(density) + WindowInsets.safeDrawing.getBottom(density)
                val handle = with(density) { 48.dp.roundToPx() }
                val body = screen - bars - handle - 11
                Spacer(Modifier.fillMaxWidth().height(with(density) { body.toDp() }).testTag("body"))
            }
        }
        val rest = sheetTop()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithContentDescription("Drag handle")
            .performTouchInput { swipeUp(centerY, centerY - 300f, durationMillis = 30) }
        furthestFrom(rest, frames = 120)
        val stillMoving = furthestFrom(rest, frames = 30)

        assertTrue("two seconds after a fling the sheet was still $stillMoving px from its place", stillMoving <= 1f)
    }
}
