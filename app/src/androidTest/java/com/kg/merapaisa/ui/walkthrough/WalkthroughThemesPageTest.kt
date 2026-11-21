package com.kg.merapaisa.ui.walkthrough

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ThemeStore
import com.kg.merapaisa.getThemeByName
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.dialogs.SettingsScreen
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The themes as a first run's first page (Parth, 20 Nov 2026): there on a first run and not on a
 * replay, the theme in use already chosen, a tap used at once by every page after it, and saved
 * where Settings reads it.
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughThemesPageTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context get() = compose.activity.applicationContext
    private lateinit var before: String

    @Before
    fun remember() = runBlocking { before = ThemeStore.getTheme(compose.activity.applicationContext).first() }

    @After
    fun restore() = runBlocking { ThemeStore.setTheme(context, before) }

    private val showSettings = mutableStateOf(false)

    /**
     * As MainActivity has it: the stored theme drawn around everything, the themes page first; or,
     * once [showSettings] is set, Settings in the same stored theme.
     */
    private fun firstRun(withThemes: Boolean = true) {
        compose.setContent {
            val name by ThemeStore.getTheme(context).collectAsState(initial = before)
            val inUse = getThemeByName(name)
            val scope = rememberCoroutineScope()
            MeraPaisaTheme(inUse) {
                CompositionLocalProvider(LocalAppTheme provides inUse) {
                    if (showSettings.value) SettingsScreen(
                        people = emptyList(), appLockEnabled = false, appLockAvailable = true,
                        onAppLockChange = {}, onImportLink = {}, onBackupRestore = {}, onExportCsv = {},
                        canExport = false, onCheckUpdates = {}, appVersion = "3.8.0", onDismiss = {},
                        onApply = {}
                    ) else WalkthroughScreen(
                        finishLabel = "Start",
                        onFinish = {},
                        chooseTheme = if (withThemes) {
                            { WalkthroughThemes(inUse = inUse, onChoose = { scope.launch { ThemeStore.setTheme(context, it) } }) }
                        } else null
                    )
                }
            }
        }
    }

    @Test
    fun aFirstRunStartsWithTheThemeInUseChosen() {
        runBlocking { ThemeStore.setTheme(context, "Diya") }
        firstRun()
        compose.onNodeWithText("Choose a theme").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 1 of ${TourPages.size + 1}").assertExists()
        compose.onNodeWithContentDescription("Diya").assertIsSelected()
        themes.filter { it.name != "Diya" }.forEach {
            compose.onNodeWithContentDescription(it.name).performScrollTo().assertIsNotSelected()
        }
        // Choosing is never required.
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Welcome to Mera Paisa").assertIsDisplayed()
    }

    @Test
    fun aReplayHasNoThemes() {
        firstRun(withThemes = false)
        compose.onNodeWithText("Choose a theme").assertDoesNotExist()
        compose.onNodeWithText("Welcome to Mera Paisa").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 1 of ${TourPages.size}").assertExists()
    }

    @Test
    fun aTapIsUsedAtOnceSavedAndShownInSettings() {
        runBlocking { ThemeStore.setTheme(context, "Diya") }
        firstRun()
        compose.onNodeWithContentDescription("Kaapi").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { ThemeStore.getTheme(context).first() } == "Kaapi" }
        compose.onNodeWithContentDescription("Kaapi").assertIsSelected()
        compose.onNodeWithContentDescription("Diya").performScrollTo().assertIsNotSelected()

        // Settings, drawn in the stored theme as the app draws it, names the choice.
        compose.runOnUiThread { showSettings.value = true }
        compose.onNodeWithText("Theme").assertIsDisplayed()
        compose.onNodeWithText("Kaapi").assertIsDisplayed()
        assertEquals("Kaapi", runBlocking { ThemeStore.getTheme(context).first() })
    }
}
