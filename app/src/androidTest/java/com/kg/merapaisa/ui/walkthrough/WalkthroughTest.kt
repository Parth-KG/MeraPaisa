package com.kg.merapaisa.ui.walkthrough

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Moving through the walkthrough: Next, Back, a swipe, system back, Skip, and the last page. */
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var finished = 0

    private fun show() {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    WalkthroughScreen(finishLabel = "Start", onFinish = { finished++ })
                }
            }
        }
    }

    private fun onPage(n: Int) =
        compose.onNodeWithContentDescription("Page $n of ${TourPages.size}").assertIsDisplayed()

    @Test
    fun nextAndBackMoveAPageAndTheLastOneFinishes() {
        show()
        onPage(1)
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.onNodeWithText("Next").performClick()
        onPage(2)
        compose.onNodeWithText("Your balances").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        onPage(1)
        repeat(TourPages.size - 1) { compose.onNodeWithText("Next").performClick() }
        onPage(TourPages.size)
        compose.onNodeWithText("Skip").assertDoesNotExist()
        compose.onNodeWithText("Start").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun aSwipeTurnsThePageEvenAcrossThePicture() {
        show()
        compose.onNodeWithText("Next").performClick()
        onPage(2)
        compose.onNodeWithContentDescription("A sample of Balances", substring = true)
            .performTouchInput { swipeLeft() }
        onPage(3)
    }

    @Test
    fun systemBackGoesBackAPageThenLeaves() {
        show()
        compose.onNodeWithText("Next").performClick()
        onPage(2)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        onPage(1)
        assertEquals(0, finished)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, finished)
    }

    @Test
    fun skipLeavesFromAnyPage() {
        show()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Skip").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun thePictureTakesNoLongPress() {
        show()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithContentDescription("A sample of Balances", substring = true)
            .performTouchInput { longClick(center) }
        compose.waitForIdle()
        // A person's menu would offer this; the picture opens nothing.
        compose.onNodeWithText("Send a reminder").assertDoesNotExist()
        onPage(2)
    }

    @Test
    fun theCalloutsAreReadInWordsTheyCanSay() {
        show()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithContentDescription("A minus sign means you owe Bilal", substring = true).assertExists()
    }
}
