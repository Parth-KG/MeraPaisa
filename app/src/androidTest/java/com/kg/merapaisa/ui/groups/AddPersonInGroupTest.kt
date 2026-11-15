package com.kg.merapaisa.ui.groups

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Group
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.dialogs.CreateGroupDialog
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Add a new person" inside New group and Edit group (v3.7.0): the person is made in the ledger and
 * comes back ticked, and everything already typed or chosen in the group sheet stays.
 */
@RunWith(AndroidJUnit4::class)
class AddPersonInGroupTest {

    @get:Rule
    val compose = createComposeRule()

    private val people = mutableStateListOf<PersonWithBalance>()
    private var createdCurrency: String? = null

    private val create: CreatePerson = { name, _, _, _, currency, onAdded ->
        createdCurrency = currency
        val id = 100L + people.size
        people += PersonWithBalance(Person(id = id, name = name, currency = currency), 0)
        onAdded(id)
    }

    private fun themed(content: @androidx.compose.runtime.Composable () -> Unit) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) { CompositionLocalProvider(LocalAppTheme provides theme) { content() } }
        }
    }

    private fun isOn(text: String) = hasText(text) and isToggleable() and
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)

    private fun addThroughSheet(name: String) {
        compose.onNodeWithText("Add a new person").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Name") and hasSetTextAction()).performTextInput(name)
        compose.onNodeWithText("Add person").performClick()
        compose.waitForIdle()
    }

    @Test
    fun newGroupFromAnEmptyLedgerOffersTheRowNotADeadEnd() {
        themed { CreateGroupDialog(people = people, defaultCurrency = "INR", onDismiss = {}, onCreate = { _, _, _, _ -> }, onCreatePerson = create) }
        compose.onNodeWithText("Add some people first", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Add a new person").assertExists()
    }

    @Test
    fun aPersonAddedFromNewGroupComesBackTickedWithTheRestKept() {
        people += PersonWithBalance(Person(id = 1, name = "Asha", currency = "INR"), 0)
        themed { CreateGroupDialog(people = people, defaultCurrency = "INR", onDismiss = {}, onCreate = { _, _, _, _ -> }, onCreatePerson = create) }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Goa")
        compose.onNode(hasContentDescription("USD")).performClick()
        compose.onNodeWithText("Asha").performClick()

        addThroughSheet("Bilal")

        assertEquals("they start in the group's currency", "USD", createdCurrency)
        compose.onNode(isOn("Bilal")).performScrollTo().assertExists()
        compose.onNode(isOn("Asha")).assertExists()
        compose.onNodeWithText("Goa").assertExists()
        compose.onNodeWithText("2 selected, plus you").assertExists()
    }

    @Test
    fun aPersonAddedFromEditGroupComesBackTicked() {
        people += PersonWithBalance(Person(id = 1, name = "Asha", currency = "INR"), 0)
        val group = Group(id = 7, name = "Flat 402", currency = "EUR")
        themed {
            EditGroupSheet(
                group = group,
                members = listOf(people[0].person),
                people = people,
                onDismiss = {},
                onSave = { _, _, _ -> },
                onCreatePerson = create
            )
        }
        addThroughSheet("Chaitanya")
        assertEquals("EUR", createdCurrency)
        compose.onNode(isOn("Chaitanya")).performScrollTo().assert(isOn("Chaitanya"))
        compose.onNodeWithText("1 to add").assertExists()
    }
}
