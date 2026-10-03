package com.kg.merapaisa.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Editing one entry: what a save writes back, and what a long-press opens.
 *
 * The sheet keeps the stored figure while its amount field is left alone, because yen is kept in
 * hundredths and a split can leave ¥33.34 in one entry, which the field shows as 33. Saving a new
 * note once wrote back 33 and lost the rest. Nothing exercised the sheet at all until a long-press
 * made the note something people open it for.
 */
@RunWith(AndroidJUnit4::class)
class EditEntryTest {

    @get:Rule
    val compose = createComposeRule()

    private val lunch = Transaction(id = 7, personId = 1, amountMinor = 3334, note = "Lunch")

    private fun show(content: @Composable () -> Unit) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) { content() }
            }
        }
    }

    @Test
    fun changingOnlyTheNoteKeepsAYenEntrysHundredths() {
        var saved: Transaction? = null
        show {
            EditEntrySheet(
                entry = lunch,
                currency = "JPY",
                onSave = { saved = it },
                onDelete = {},
                onDismiss = {}
            )
        }

        compose.onNode(hasText("Lunch") and hasSetTextAction()).performTextReplacement("Lunch with Asha")
        compose.onNodeWithText("Save entry").performClick()
        compose.waitForIdle()

        assertEquals("Lunch with Asha", saved?.note)
        assertEquals("the amount must still be ¥33.34, kept in hundredths", 3334L, saved?.amountMinor)
    }

    @Test
    fun aLongPressOffersTheNoteAndTheAmountAndOpensTheOneChosen() {
        val asha = PersonWithBalance(Person(id = 1, name = "Asha", currency = "JPY"), 3334)
        show {
            EntryHistoryContent(
                person = asha,
                entries = listOf(lunch),
                onBack = {}, onEdit = {}, onDelete = {}, onReverse = {}, onClear = {}
            )
        }

        compose.onNodeWithText("Lunch").performTouchInput { longClick() }
        compose.onNodeWithText("Edit note").assertIsDisplayed()
        compose.onNodeWithText("Edit amount").performClick()
        compose.waitForIdle()

        compose.onNode(hasText("33") and hasSetTextAction()).assertIsFocused()
    }
}
