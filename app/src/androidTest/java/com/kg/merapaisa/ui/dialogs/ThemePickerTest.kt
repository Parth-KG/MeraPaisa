package com.kg.merapaisa.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The theme picker in Settings, folded until asked for.
 *
 * Settings listed every theme on every visit, under a "Use this theme" button that was dead unless
 * you had picked one. Now the themes fold into one row, the button exists only while they are
 * open, and closing drops a pick nobody applied, so nothing waits out of sight.
 */
@RunWith(AndroidJUnit4::class)
class ThemePickerTest {

    @get:Rule
    val compose = createComposeRule()

    private val inUse = themes.first()
    private val other = themes[2]
    private var applied: String? = null

    private val settings = @Composable {
        MeraPaisaTheme(inUse) {
            CompositionLocalProvider(LocalAppTheme provides inUse) {
                SettingsScreen(
                    currentThemeName = inUse.name,
                    appLockEnabled = false,
                    appLockAvailable = true,
                    onAppLockChange = {},
                    onImportLink = {},
                    onBackupRestore = {},
                    onExportCsv = {},
                    canExport = true,
                    onCheckUpdates = {},
                    appVersion = "3.1.0",
                    onDismiss = {},
                    onApply = { applied = it }
                )
            }
        }
    }

    private fun summary(): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Theme, ${inUse.name}"))
        return compose.onNode(hasContentDescription("Theme, ${inUse.name}"))
    }

    private fun themeRow(name: String) = compose.onNode(hasText(name) and isSelectable())

    private fun shownThemeRow(name: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(name) and isSelectable())
        return themeRow(name)
    }

    private val useButton get() = compose.onNodeWithText("Use this theme")

    @Test
    fun opensFoldedAndNamesTheThemeInUse() {
        compose.setContent(settings)

        summary().assertExists()
        themes.forEach { themeRow(it.name).assertDoesNotExist() }
        useButton.assertDoesNotExist()
    }

    @Test
    fun openingShowsEveryThemeAndAButtonThatWaitsForAPick() {
        compose.setContent(settings)
        summary().performClick()

        themes.forEach { shownThemeRow(it.name).assertExists() }
        useButton.assertIsNotEnabled()
    }

    @Test
    fun closingDropsAnUnappliedPick() {
        compose.setContent(settings)
        summary().performClick()
        shownThemeRow(other.name).performClick()
        useButton.assertIsEnabled()

        summary().performClick()
        themes.forEach { themeRow(it.name).assertDoesNotExist() }
        useButton.assertDoesNotExist()

        summary().performClick()
        shownThemeRow(inUse.name).assertIsSelected()
        useButton.assertIsNotEnabled()
    }

    @Test
    fun theOpenListAndAPickSurviveRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent(settings)
        summary().performClick()
        shownThemeRow(other.name).performClick()

        restoration.emulateSavedInstanceStateRestore()

        shownThemeRow(other.name).assertIsSelected()
        useButton.assertIsEnabled()
    }

    @Test
    fun applyingSendsThePick() {
        compose.setContent(settings)
        summary().performClick()
        shownThemeRow(other.name).performClick()
        useButton.performClick()

        assertEquals(other.name, applied)
    }
}
