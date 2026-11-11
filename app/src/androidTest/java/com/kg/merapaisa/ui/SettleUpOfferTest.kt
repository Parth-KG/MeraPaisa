package com.kg.merapaisa.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
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
 * Settle up is offered only when there is a balance to square, and Reopen is never offered.
 *
 * Until v3.3.1 a person was filed away by Settle up and brought back by Reopen. The tabs now
 * follow the balance, so at zero someone is already on Even: Settle up would record nothing, and
 * there is nothing to reopen, since recording any amount takes them back to Active by itself.
 */
@RunWith(AndroidJUnit4::class)
class SettleUpOfferTest {

    @get:Rule
    val compose = createComposeRule()

    private fun person(balanceMinor: Long) =
        PersonWithBalance(Person(id = 1, name = "Asha", currency = "INR"), balanceMinor)

    private fun showKeypad(balanceMinor: Long, onSettleUp: () -> Unit = {}) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    NumPad(
                        person = person(balanceMinor),
                        input = "",
                        onKey = {}, onSettleUp = onSettleUp, onAdd = {}, onSubtract = {},
                        note = "", onNoteChange = {}, showNote = false, onToggleNote = {}
                    )
                }
            }
        }
    }

    private fun openMenu(balanceMinor: Long) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    PersonRow(
                        person = person(balanceMinor),
                        isSelected = false,
                        onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {},
                        onEditClick = {}, onSettleUp = {}, onShareSummary = {}, onShareLedger = {},
                        onMoveDebt = {}
                    )
                }
            }
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            .performTouchInput { longClick() }
    }

    @Test
    fun theKeypadOffersSettleUpForSomeoneWhoOwesYou() {
        var settled = 0
        showKeypad(1_250_00) { settled++ }
        compose.onNodeWithText("Settle up").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, settled)
        compose.onNodeWithText("Reopen").assertDoesNotExist()
    }

    @Test
    fun theKeypadOffersSettleUpForSomeoneYouOwe() {
        showKeypad(-40_00)
        compose.onNodeWithText("Settle up").assertExists()
        compose.onNodeWithText("Reopen").assertDoesNotExist()
    }

    @Test
    fun theKeypadAtZeroOffersNeither() {
        showKeypad(0)
        compose.onNodeWithText("You paid for them").assertExists()
        compose.onNodeWithText("Settle up").assertDoesNotExist()
        compose.onNodeWithText("Reopen").assertDoesNotExist()
    }

    @Test
    fun theMenuOffersSettleUpOnlyWithABalance() {
        openMenu(500_00)
        compose.onNodeWithText("Settle up").assertExists()
        compose.onNodeWithText("Reopen").assertDoesNotExist()
    }

    @Test
    fun theMenuAtZeroOffersNeither() {
        openMenu(0)
        compose.onNodeWithText("Edit").assertExists()
        compose.onNodeWithText("Settle up").assertDoesNotExist()
        compose.onNodeWithText("Reopen").assertDoesNotExist()
    }
}
