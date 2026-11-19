package com.kg.merapaisa.ui.dialogs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
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
 * Add a person and New group answer their button once. Each sheet slides away after the tap, and
 * a second tap in that time added the person, or made the group, a second time.
 */
@RunWith(AndroidJUnit4::class)
class SheetAddsOnceTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    @Test
    fun twoQuickTapsOnAddPersonAddOnePerson() {
        var added = 0
        show { AddPersonDialog(onDismiss = {}, onAdd = { _, _, _, _, _ -> added++ }) }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Chaitanya")
        // After typing, the keyboard can take a real touch, so the button is pressed directly.
        compose.onNodeWithText("Add person").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Add person").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertEquals(1, added)
    }

    @Test
    fun twoQuickTapsOnCreateGroupMakeOneGroup() {
        var made = 0
        val asha = PersonWithBalance(Person(id = 2, name = "Asha", currency = "INR"), 0)
        show {
            CreateGroupDialog(
                people = listOf(asha),
                defaultCurrency = "INR",
                onDismiss = {},
                onCreate = { _, _, _, _ -> made++ }
            )
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Goa")
        compose.onNodeWithText("Asha").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Create group").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Create group").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertEquals(1, made)
    }
}
