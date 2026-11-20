package com.kg.merapaisa.ui.whatsnew

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.dialogs.SettingsScreen
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The sheet after an update, and the Settings row that opens the walkthrough again. */
@RunWith(AndroidJUnit4::class)
class WhatsNewSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val theme = themes.first()

    @Test
    fun v380SaysWhatChangedAndOffersTheWalkthroughOnce() {
        var closed = 0
        var walkthrough = 0
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    WhatsNewSheet(
                        whatsNew = whatsNewSince(24)!!,
                        onClose = { closed++ },
                        onShowWalkthrough = { walkthrough++ }
                    )
                }
            }
        }
        compose.onNodeWithText("What's new in 3.8.0").assertIsDisplayed()
        RELEASE_LINES.last().lines.forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Show me how it works").performClick()
        compose.onNodeWithText("Show me how it works").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("one tap, however many", 1, walkthrough)
        assertEquals(0, closed)
    }

    @Test
    fun settingsOpensTheWalkthroughAgain() {
        var opened = 0
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    SettingsScreen(
                        people = emptyList(),
                        appLockEnabled = false,
                        appLockAvailable = true,
                        onAppLockChange = {},
                        onImportLink = {},
                        onBackupRestore = {},
                        onExportCsv = {},
                        canExport = false,
                        onCheckUpdates = {},
                        appVersion = "3.8.0",
                        onDismiss = {},
                        onApply = {},
                        onHowItWorks = { opened++ }
                    )
                }
            }
        }
        compose.onNodeWithText("How Mera Paisa works").performClick()
        assertEquals(1, opened)
    }
}
