package com.kg.merapaisa.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The theme picker: one row in Settings, and a sheet where a theme is tried on your own ledger
 * before it is used.
 *
 * Settings used to unfold every theme as a list under a button that was dead until you picked one.
 * Now nothing changes until "Use" is pressed, the only button offered before a pick is the way out,
 * and the picture of your ledger is one thing to TalkBack, not rows that do nothing.
 */
@RunWith(AndroidJUnit4::class)
class ThemePickerTest {

    @get:Rule
    val compose = createComposeRule()

    private val inUse = themes.first()
    private val other = themes[2]
    private var applied: String? = null
    private val asha = PersonWithBalance(
        person = Person(id = 1, name = "Asha", currency = "INR"),
        balanceMinor = 1_200_00
    )

    private val settings = @Composable {
        MeraPaisaTheme(inUse) {
            CompositionLocalProvider(LocalAppTheme provides inUse) {
                SettingsScreen(
                    people = listOf(asha),
                    appLockEnabled = false,
                    appLockAvailable = true,
                    onAppLockChange = {},
                    onImportLink = {},
                    onBackupRestore = {},
                    onExportCsv = {},
                    canExport = true,
                    onCheckUpdates = {},
                    appVersion = "3.2.0",
                    onDismiss = {},
                    onApply = { applied = it }
                )
            }
        }
    }

    private fun themeRow(): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Theme"))
        return compose.onNodeWithText("Theme")
    }

    private fun chipMatcher(name: String) = hasContentDescription(name, substring = true) and isSelectable()

    private fun chip(name: String) = compose.onNode(chipMatcher(name))

    private fun chipCount(name: String) = compose.onAllNodes(chipMatcher(name)).fetchSemanticsNodes().size

    /**
     * The sheet opens and closes over a few frames. One idle frame was usually enough and sometimes
     * not, so the tests wait for the chips to arrive, or to go, instead.
     */
    private fun openSheet() {
        themeRow().performClick()
        compose.waitUntil(5_000) { chipCount(inUse.name) > 0 }
    }

    private fun waitForSheetToClose() = compose.waitUntil(5_000) { chipCount(other.name) == 0 }

    private fun tryTheme(name: String) {
        chip(name).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun settingsNamesTheThemeInUseAndListsNoThemes() {
        compose.setContent(settings)

        themeRow().assertExists()
        compose.onNodeWithText(inUse.name).assertExists()
        themes.forEach { chip(it.name).assertDoesNotExist() }
    }

    @Test
    fun theSheetOpensOnTheThemeInUseWithOnlyAWayOut() {
        compose.setContent(settings)
        openSheet()

        themes.forEach { chip(it.name).assertExists() }
        chip("${inUse.name}, in use").assertIsSelected()
        compose.onNodeWithText("Keep ${inUse.name}").assertExists()
        compose.onNode(hasText("Use ", substring = true)).assertDoesNotExist()
    }

    @Test
    fun tryingAThemeRedrawsThePictureAndOffersToUseIt() {
        compose.setContent(settings)
        openSheet()
        tryTheme(other.name)

        chip(other.name).assertIsSelected()
        chip("${inUse.name}, in use").assertIsNotSelected()
        compose.onNode(hasContentDescription("Your ledger in ${other.name}")).assertExists()
        compose.onNodeWithText("Use ${other.name}").assertExists()
        assertNull("trying applies nothing", applied)
    }

    @Test
    fun thePictureIsOneThingToTalkBack() {
        compose.setContent(settings)
        openSheet()

        compose.onNode(hasContentDescription("Your ledger in ${inUse.name}")).assertExists()
        compose.onNodeWithText("Asha").assertDoesNotExist()
    }

    @Test
    fun usingSendsTheTriedTheme() {
        compose.setContent(settings)
        openSheet()
        tryTheme(other.name)
        compose.onNodeWithText("Use ${other.name}").performClick()
        compose.waitUntil(5_000) { applied != null }
        waitForSheetToClose()

        assertEquals(other.name, applied)
        chip(other.name).assertDoesNotExist()
    }

    @Test
    fun keepingSendsNothing() {
        compose.setContent(settings)
        openSheet()
        tryTheme(other.name)
        compose.onNodeWithText("Keep ${inUse.name}").performClick()
        waitForSheetToClose()

        assertNull(applied)
        chip(other.name).assertDoesNotExist()
    }

    @Test
    fun theSheetAndTheTriedThemeSurviveRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent(settings)
        openSheet()
        tryTheme(other.name)

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        chip(other.name).assertIsSelected()
        compose.onNodeWithText("Use ${other.name}").assertExists()
    }
}
