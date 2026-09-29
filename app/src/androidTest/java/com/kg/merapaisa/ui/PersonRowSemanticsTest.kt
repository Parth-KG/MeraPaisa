package com.kg.merapaisa.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What TalkBack can reach on a person's row.
 *
 * The redesign gave the row one spoken sentence by clearing the semantics of everything inside it,
 * and the history button went with the words: a screen reader could no longer open anyone's
 * history. Found by driving the app on a phone by its accessibility labels, which could not find
 * the button either.
 */
@RunWith(AndroidJUnit4::class)
class PersonRowSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theRowReadsAsOneSentenceAndHistoryStaysReachable() {
        var historyOpened = 0
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    PersonRow(
                        person = PersonWithBalance(Person(id = 1, name = "Asha", currency = "INR"), 1_200_00),
                        isSelected = false,
                        onHistoryClick = { historyOpened++ },
                        onClick = {}, onSendReminder = {}, onDelete = {}, onEditClick = {},
                        onSettleToggle = {}, onShareSummary = {}, onShareLedger = {}, onMoveDebt = {}
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Asha owes you", substring = true).assertHasClickAction()
        compose.onNodeWithContentDescription("History for Asha").assertHasClickAction().performClick()
        assertEquals("the history button must still open the history", 1, historyOpened)
    }
}
