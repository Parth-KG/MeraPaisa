package com.kg.merapaisa.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
 * A share whose rate could not be fetched says nothing about what it would be saved as.
 *
 * It used to fall back to the unconverted figure, so a ₹1,200 share for someone kept in dollars
 * read "Saved as $1,200" under the error that said no rate had come back.
 */
@RunWith(AndroidJUnit4::class)
class SplitMissingRateTest {

    @get:Rule
    val compose = createComposeRule()

    private val diego = PersonWithBalance(Person(id = 4, name = "Diego", currency = "USD"), 0L)

    private fun show(convert: suspend (Long, String, String) -> Long?) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    SplitAdjustmentsContent(
                        convert = convert,
                        amountMinor = 2_400_00,
                        sourceCurrency = "INR",
                        selectedPersons = listOf(diego),
                        includeMe = true,
                        note = "",
                        onNoteChange = {}, onBack = {}, onCancel = {}, onConfirm = {}
                    )
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Getting today's rates", substring = true))
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun noRateShowsNoSavedAsFigure() {
        show { _, from, to -> if (from == to) null else null }
        compose.onNodeWithText("Couldn't get today's rate for USD", substring = true).assertExists()
        compose.onNodeWithText("Saved as", substring = true).assertDoesNotExist()
    }

    @Test
    fun aRateShowsTheConvertedFigure() {
        show { amount, _, _ -> amount / 100 }
        compose.onNodeWithText("Saved as \$12", substring = true).assertExists()
    }
}
