package com.kg.merapaisa.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
 * Which way the money goes when a direction button is tapped.
 *
 * MainScreen wires onAdd to record a positive amount and onSubtract a negative one, and a positive
 * balance means "owes you". So the button for "you paid them" has to be the one calling onAdd:
 * you covered something, they owe you more. The button for "they paid you" has to call
 * onSubtract: they paid you back, they owe you less.
 *
 * The redesign got this backwards. When the "+" and minus keys became words, "They paid me" went
 * on the button that records a positive amount, so someone who had just been repaid would tap it
 * and raise what they owed instead of lowering it. The labels read naturally, the code compiled,
 * every other test passed, and nothing on screen looked wrong until the balance did. It was caught
 * in review before any release. This test is what stops it coming back.
 */
@RunWith(AndroidJUnit4::class)
class NumPadDirectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val asha = PersonWithBalance(Person(id = 1, name = "Asha", currency = "INR"), 0)

    private fun render(onAdd: () -> Unit, onSubtract: () -> Unit) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    NumPad(
                        person = asha,
                        input = "100",
                        onKey = {}, onSettleToggle = {},
                        onAdd = onAdd, onSubtract = onSubtract,
                        note = "", onNoteChange = {}, showNote = false, onToggleNote = {}
                    )
                }
            }
        }
    }

    /** You paid for them, so they owe you more: the positive side. */
    @Test
    fun youPaidThemRecordsWhatTheyOweYou() {
        var added = 0
        var subtracted = 0
        render(onAdd = { added++ }, onSubtract = { subtracted++ })

        compose.onNodeWithText("You paid them").performClick()

        assertEquals("\"You paid them\" must increase what they owe you", 1, added)
        assertEquals("and must not also record a repayment", 0, subtracted)
    }

    /** They paid you back, so they owe you less: the negative side. */
    @Test
    fun theyPaidYouRecordsARepayment() {
        var added = 0
        var subtracted = 0
        render(onAdd = { added++ }, onSubtract = { subtracted++ })

        compose.onNodeWithText("They paid you").performClick()

        assertEquals("\"They paid you\" must reduce what they owe you", 1, subtracted)
        assertEquals("and must not also increase it", 0, added)
    }
}
