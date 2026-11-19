package com.kg.merapaisa.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.MAX_NAME_CHARS
import com.kg.merapaisa.data.MAX_NOTE_CHARS
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A note stops at 500 characters and a name at 100 (v3.7.1), typed or pasted, because the other
 * phone refuses a link carrying more as damaged, and resending never helped.
 */
@RunWith(AndroidJUnit4::class)
class FieldCapTest {

    @get:Rule
    val compose = createComposeRule()

    private fun waitFor(matcher: SemanticsMatcher) =
        compose.waitUntil(5_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun show(content: @Composable () -> Unit) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) { content() }
            }
        }
    }

    @Test
    fun aPastedNoteOf600CharactersKeeps500() {
        var saved: Transaction? = null
        show {
            EditEntrySheet(
                entry = Transaction(id = 7, personId = 1, amountMinor = 100_00, note = "Lunch"),
                currency = "INR",
                onSave = { saved = it },
                onDelete = {},
                onDismiss = {}
            )
        }
        waitFor(hasText("Lunch") and hasSetTextAction())
        compose.onNode(hasText("Lunch") and hasSetTextAction()).performTextReplacement("n".repeat(600))
        compose.onNodeWithText("Save entry").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { saved != null }
        assertEquals(MAX_NOTE_CHARS, saved?.note?.length)
    }

    @Test
    fun aNameOf150CharactersKeeps100() {
        var added: String? = null
        show { AddPersonDialog(onDismiss = {}, onAdd = { name, _, _, _, _ -> added = name }) }
        waitFor(hasSetTextAction())
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("a".repeat(150))
        compose.onNodeWithText("Add person").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { added != null }
        assertEquals(MAX_NAME_CHARS, added?.length)
    }
}
