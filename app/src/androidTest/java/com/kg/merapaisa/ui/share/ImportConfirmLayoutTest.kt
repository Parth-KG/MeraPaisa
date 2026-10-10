package com.kg.merapaisa.ui.share

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.gallery.Fixtures
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checking an update link: who it is filed against comes before what it holds.
 *
 * The choice sat under every entry, so on a long link the one decision on the screen was below
 * the fold, and the outcome above it named nobody until it was made.
 */
@RunWith(AndroidJUnit4::class)
class ImportConfirmLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theChoiceOfPersonComesBeforeTheEntries() {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    ImportLedgerDialog(
                        state = Fixtures.importConfirming,
                        persons = Fixtures.mixedPeople,
                        onTargetChange = {}, onNewPersonNameChange = {}, onToggleItem = {}, onApply = {},
                        onPasteChange = {}, onPasteSubmit = {}, onDismiss = {}
                    )
                }
            }
        }

        // On screen without a scroll.
        compose.onNodeWithText("File it against").assertIsDisplayed()
        val choice = compose.onNodeWithText("File it against").getUnclippedBoundsInRoot()

        // An entry may not be composed at all if it is below the fold, which is also after.
        compose.onAllNodesWithText("Cab to the airport").fetchSemanticsNodes().firstOrNull()?.let {
            val entryTop = compose.onAllNodesWithText("Cab to the airport")[0].getUnclippedBoundsInRoot().top
            assertTrue("an entry comes before the choice of person", entryTop > choice.bottom)
        }
    }
}
