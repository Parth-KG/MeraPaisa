package com.kg.merapaisa.ui.dialogs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A person's history at double type: an entry's note keeps room to be read.
 *
 * The row gave its figure its full width first and the note what was left, so beside a lakh figure
 * at double type the note came down to a letter or two and an ellipsis. From the list's stacking
 * scale up, the figure goes under the note instead.
 */
@RunWith(AndroidJUnit4::class)
class HistoryLargeTypeTest {

    @get:Rule
    val compose = createComposeRule()

    private val note = "Dinner at the place near the station"
    private val asha = PersonWithBalance(Person(id = 1, name = "Asha", currency = "INR"), 12_34_567_50)
    private val dinner = Transaction(id = 1, personId = 1, amountMinor = 12_34_567_50, note = note)

    @Test
    fun aNoteBesideALakhFigureStillShowsItsWords() {
        val theme = themes.first()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                MeraPaisaTheme(theme) {
                    CompositionLocalProvider(LocalAppTheme provides theme) {
                        EntryHistoryContent(
                            person = asha,
                            entries = listOf(dinner),
                            onBack = {}, onEdit = {}, onDelete = {}, onReverse = {}, onClear = {}
                        )
                    }
                }
            }
        }

        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(note, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        val firstLine = layout.getLineEnd(0, visibleEnd = true)
        assertTrue("only ${note.take(firstLine)} of the note shows", firstLine >= 12)
    }
}
