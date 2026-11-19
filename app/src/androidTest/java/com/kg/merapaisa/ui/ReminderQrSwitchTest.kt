package com.kg.merapaisa.ui

import org.junit.Assert.assertTrue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.dialogs.ReminderSheet
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reminder's "Attach a QR to pay you": shown only where Show QR is, and on every time the
 * sheet opens. What it sends is covered on the JVM by ReminderQrTextTest.
 */
@RunWith(AndroidJUnit4::class)
class ReminderQrSwitchTest {

    @get:Rule
    val compose = createComposeRule()

    private fun open(
        balance: Long,
        currency: String = "INR",
        myUpiId: String? = "parth@okaxis",
        onAddUpiId: (() -> Unit)? = null
    ) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    ReminderSheet(
                        person = PersonWithBalance(Person(id = 1, name = "Asha", currency = currency), balance),
                        entries = emptyList(),
                        onDismiss = {},
                        myUpiId = myUpiId,
                        myName = "Parth",
                        onAddUpiId = onAddUpiId
                    )
                }
            }
        }
    }

    @Test
    fun someoneWhoOwesYouRupeesGetsTheSwitchTurnedOn() {
        open(500_00)
        compose.onNodeWithText("Attach a QR to pay you").assertExists()
        compose.onNode(qrSwitch()).assertIsOn()
        // The editable message stays as it was: the UPI line is only added when it goes.
        compose.onNodeWithText("Hi Asha, you owe me ₹500. Send it when you can.").assertExists()
        compose.onNodeWithText("Scan the QR", substring = true).assertDoesNotExist()
    }

    @Test
    fun theSwitchTurnsOff() {
        open(500_00)
        compose.onNode(qrSwitch()).performClick()
        compose.onNode(qrSwitch()).assertIsOff()
    }

    @Test
    fun noSwitchWithoutYourUpiId() {
        open(500_00, myUpiId = null)
        compose.onNodeWithText("Attach a QR to pay you").assertDoesNotExist()
    }

    @Test
    fun withoutYourUpiIdTheRowOffersToAddOne() {
        var asked = false
        open(500_00, myUpiId = null, onAddUpiId = { asked = true })
        compose.onNodeWithText("Attach a QR to pay you").assertDoesNotExist()
        compose.onNodeWithText("Add your UPI ID to attach a QR").performClick()
        assertTrue(asked)
    }

    @Test
    fun noAddRowOutsideRupeesOrWhenNothingIsOwed() {
        open(50_00, currency = "USD", myUpiId = null, onAddUpiId = {})
        compose.onNodeWithText("Add your UPI ID to attach a QR").assertDoesNotExist()
    }

    @Test
    fun noAddRowWhenYouOweThem() {
        open(-40_00, myUpiId = null, onAddUpiId = {})
        compose.onNodeWithText("Add your UPI ID to attach a QR").assertDoesNotExist()
    }

    @Test
    fun noSwitchOutsideRupees() {
        open(50_00, currency = "USD")
        compose.onNodeWithText("Attach a QR to pay you").assertDoesNotExist()
    }

    @Test
    fun noSwitchWhenYouOweThemOrYouAreEven() {
        open(-40_00)
        compose.onNodeWithText("Attach a QR to pay you").assertDoesNotExist()
    }

    private fun qrSwitch() = androidx.compose.ui.test.hasText("Attach a QR to pay you") and
        androidx.compose.ui.test.isToggleable()
}
