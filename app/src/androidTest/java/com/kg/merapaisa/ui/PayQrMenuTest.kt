package com.kg.merapaisa.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
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

/** A person's menu offers Show QR when it can, and says why not outside rupees. */
@RunWith(AndroidJUnit4::class)
class PayQrMenuTest {

    @get:Rule
    val compose = createComposeRule()

    private fun openMenu(currency: String, onShowQr: (() -> Unit)?, onlyInRupees: Boolean) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    PersonRow(
                        person = PersonWithBalance(Person(id = 1, name = "Asha", currency = currency), 500_00),
                        isSelected = false,
                        onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {},
                        onEditClick = {}, onSettleUp = {}, onShareSummary = {}, onShareLedger = {},
                        onMoveDebt = {}, onShowQr = onShowQr, qrOnlyInRupees = onlyInRupees
                    )
                }
            }
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)).performTouchInput { longClick() }
    }

    @Test
    fun someoneWhoOwesYouRupeesGetsShowQr() {
        openMenu("INR", onShowQr = {}, onlyInRupees = false)
        compose.onNodeWithText("Show QR to pay you").assertExists()
    }

    @Test
    fun outsideRupeesTheMenuSaysWhy() {
        openMenu("USD", onShowQr = null, onlyInRupees = true)
        compose.onNodeWithText("Show QR (UPI is rupees only)").assertIsNotEnabled()
        compose.onNodeWithText("Show QR to pay you").assertDoesNotExist()
    }

    @Test
    fun withoutYourUpiIdThereIsNoQrAtAll() {
        openMenu("INR", onShowQr = null, onlyInRupees = false)
        compose.onNodeWithText("Show QR", substring = true).assertDoesNotExist()
    }
}
