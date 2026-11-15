package com.kg.merapaisa.ui.groups

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Adjust the shares" in a group's Add and Edit expense (v3.7.0). */
@RunWith(AndroidJUnit4::class)
class AddExpenseAdjustTest {

    @get:Rule
    val compose = createComposeRule()

    private val members = listOf(
        Person(id = 1, name = "You", currency = "INR", isSelf = true),
        Person(id = 2, name = "Asha", currency = "INR"),
        Person(id = 3, name = "Bilal", currency = "INR")
    )
    private var saved: Map<Long, Long>? = null

    private fun show(editing: ExpenseDraft? = null) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    AddExpenseDialog(
                        members = members, currency = "INR", selfId = 1, onDismiss = {},
                        onSave = { _, _, _, shares -> saved = shares }, editing = editing
                    )
                }
            }
        }
    }

    @Test
    fun aTypedShareHoldsAndTheOthersTakeUpTheDifference() {
        show()
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Dinner")
        fields[1].performTextInput("900")
        compose.onNodeWithText("Adjust the shares").performScrollTo().performClick()
        compose.onNodeWithText("Split equally").assertExists()
        // The share fields follow the description and the amount.
        compose.onAllNodes(hasSetTextAction())[2].performTextReplacement("500")
        compose.waitForIdle()
        compose.onNode(hasText("Add expense") and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        val shares = saved!!
        assertEquals(900_00L, shares.values.sum())
        assertEquals(500_00L, shares[1L])
        assertEquals(200_00L, shares[2L])
        assertEquals(200_00L, shares[3L])
    }

    @Test
    fun anUnequalExpenseOpensAdjustedAndSavesItsSharesUntouched() {
        val stored = mapOf(1L to 500_00L, 2L to 300_00L, 3L to 100_00L)
        show(ExpenseDraft("Deposit", 900_00, 1, stored))
        compose.onNodeWithText("Split equally").performScrollTo().assertExists()
        compose.onNode(hasText("Save expense") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(stored, saved)
    }

    @Test
    fun splittingEquallyAgainSavesEqualShares() {
        show(ExpenseDraft("Deposit", 900_00, 1, mapOf(1L to 500_00L, 2L to 300_00L, 3L to 100_00L)))
        compose.onNodeWithText("Split equally").performScrollTo().performClick()
        compose.onNode(hasText("Save expense") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(mapOf(1L to 300_00L, 2L to 300_00L, 3L to 300_00L), saved)
    }
}
