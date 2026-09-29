package com.kg.merapaisa.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
 * A share field keeps what is typed into it.
 *
 * Shares are shown with two decimals, and the field used to rebuild its text from the amount on
 * every keystroke. So the first backspace on "600.00" that changed the number snapped it back to
 * "60.00": the field could not be cleared, and the next digit landed after the decimals, turning
 * an intended 800 into 8,000. Found by typing into it on a phone.
 */
@RunWith(AndroidJUnit4::class)
class SplitShareEditingTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun whatIsTypedStaysAsTyped() {
        val theme = themes.first()
        val people = listOf("Asha", "Bilal").mapIndexed { i, name ->
            PersonWithBalance(Person(id = i + 1L, name = name, currency = "INR"), 0)
        }
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    SplitAdjustmentsContent(
                        convert = { amount, _, _ -> amount },
                        amountMinor = 1_200_00,
                        sourceCurrency = "INR",
                        selectedPersons = people,
                        includeMe = false,
                        note = "",
                        onNoteChange = {}, onBack = {}, onCancel = {}, onConfirm = {}
                    )
                }
            }
        }

        // The note is the first text field; Asha's share is the second.
        val share = compose.onAllNodes(hasSetTextAction())[1]
        share.performClick()
        share.performTextReplacement("60")
        share.assertTextEquals("60")
        share.performTextReplacement("600")
        share.assertTextEquals("600")
    }
}
