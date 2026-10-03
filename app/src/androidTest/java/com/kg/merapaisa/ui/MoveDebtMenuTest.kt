package com.kg.merapaisa.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A person's menu offers to move a debt only when there is one, named for which way it runs.
 *
 * "Move this debt" was offered only to someone who owed you. Its mirror, "Move what you owe", is
 * for someone you owe, and a person you are even with gets neither: an item that always refuses
 * is worse than no item.
 */
@RunWith(AndroidJUnit4::class)
class MoveDebtMenuTest {

    @get:Rule
    val compose = createComposeRule()

    private fun openMenuFor(balanceMinor: Long) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    PersonRow(
                        person = PersonWithBalance(Person(id = 1, name = "Rondu", currency = "INR"), balanceMinor),
                        isSelected = false,
                        onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {},
                        onEditClick = {}, onSettleToggle = {}, onShareSummary = {}, onShareLedger = {},
                        onMoveDebt = {}
                    )
                }
            }
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            .performTouchInput { longClick() }
    }

    @Test
    fun someoneWhoOwesYouOffersToMoveTheirDebt() {
        openMenuFor(500_00)
        compose.onNodeWithText("Move this debt").assertExists()
        compose.onNodeWithText("Move what you owe").assertDoesNotExist()
    }

    @Test
    fun someoneYouOweOffersToMoveWhatYouOwe() {
        openMenuFor(-500_00)
        compose.onNodeWithText("Move what you owe").assertExists()
        compose.onNodeWithText("Move this debt").assertDoesNotExist()
    }

    @Test
    fun someoneYouAreEvenWithOffersNeither() {
        openMenuFor(0)
        compose.onNodeWithText("Edit").assertExists()
        compose.onNodeWithText("Move this debt").assertDoesNotExist()
        compose.onNodeWithText("Move what you owe").assertDoesNotExist()
    }
}
