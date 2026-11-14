package com.kg.merapaisa.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.pay.PaySheet
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Pay sheet: who and how much before anything opens, and the warning for paying more. */
@RunWith(AndroidJUnit4::class)
class PaySheetTest {

    @get:Rule
    val compose = createComposeRule()

    private var paid: Pair<Long, Long>? = null

    private fun show(amount: String) {
        val theme = themes.first()
        compose.setContent {
            var text by remember { mutableStateOf(amount) }
            var onlyOwed by remember { mutableStateOf(false) }
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    PaySheet(
                        personName = "Bilal",
                        upiId = "bilal@ybl",
                        owedMinor = 340_00,
                        amountText = text,
                        onAmountChange = { text = it },
                        recordOnlyWhatYouOwed = onlyOwed,
                        onRecordOnlyWhatYouOwedChange = { onlyOwed = it },
                        noUpiApp = false,
                        onDismiss = {},
                        onPay = { sent, record -> paid = sent to record }
                    )
                }
            }
        }
    }

    @Test
    fun theFullUpiIdIsShownBeforeAnythingOpens() {
        show("340")
        compose.onNodeWithText("bilal@ybl").assertExists()
        compose.onNodeWithText("Check the name it shows", substring = true).assertExists()
    }

    @Test
    fun payingWhatYouOweRecordsIt() {
        show("340")
        compose.onNodeWithText("more than you owe", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Open UPI app").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(340_00L to 340_00L, paid)
    }

    @Test
    fun payingMoreWarnsAndRecordsAllByDefault() {
        show("540")
        compose.onNodeWithText("That's ₹200 more than you owe Bilal.").assertExists()
        compose.onNodeWithText("Open UPI app").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(540_00L to 540_00L, paid)
    }

    @Test
    fun payingMoreCanRecordOnlyWhatYouOwed() {
        show("540")
        compose.onNodeWithText("Record only the ₹340 you owe").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Open UPI app").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(540_00L to 340_00L, paid)
    }
}
