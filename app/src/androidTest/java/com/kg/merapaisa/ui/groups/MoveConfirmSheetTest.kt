package com.kg.merapaisa.ui.groups

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.MoveFlowState
import com.kg.merapaisa.ui.MoveLineState
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The move's confirm sheet (v3.7.0): every person listed, and nothing moves while a rate is missing. */
@RunWith(AndroidJUnit4::class)
class MoveConfirmSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(bilalInDollars: Long?) {
        val theme = themes.first()
        val flow = MoveFlowState(
            groupId = 1,
            lines = listOf(
                MoveLineState(2, "Asha", "INR", 200_00, 200_00),
                MoveLineState(3, "Bilal", "USD", -300_00, bilalInDollars)
            )
        )
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    MoveToLedgerSheet("Goa", "INR", flow, onRetry = {}, onDismiss = {}, onMove = {})
                }
            }
        }
    }

    private fun moveButton() = compose.onNode(hasText("Move to ledger") and hasClickAction())

    @Test
    fun everyPersonIsListedWithWhatTheirLedgerGets() {
        show(bilalInDollars = -3_59)
        compose.onNodeWithText("Asha").assertExists()
        compose.onNodeWithText("owes you").assertExists()
        compose.onNodeWithText("you owe").assertExists()
        compose.onNodeWithText("Saved as $3.59").assertExists()
        compose.onNodeWithText("From Goa", substring = true).assertExists()
        moveButton().assertIsEnabled()
    }

    @Test
    fun aMissingRateHoldsTheMoveAndOffersToTryAgain() {
        show(bilalInDollars = null)
        compose.onNodeWithText("Couldn't get today's rate for USD", substring = true).assertExists()
        compose.onNodeWithText("Try again").assertExists()
        moveButton().assertIsNotEnabled()
    }
}
