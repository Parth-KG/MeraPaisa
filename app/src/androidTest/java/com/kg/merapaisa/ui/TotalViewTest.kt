package com.kg.merapaisa.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.TotalViewStore
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.netTotalsByCurrency
import com.kg.merapaisa.data.sidesByCurrency
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The overall total turns over between the net and both sides, and stays turned.
 *
 * The net hides how much is out either way, so a tap anywhere on the total shows what you are
 * owed and what you owe, kept apart. The choice is a preference, read back when the screen comes
 * back, and an all-even ledger has nothing to turn.
 */
@RunWith(AndroidJUnit4::class)
class TotalViewTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val people = listOf(
        PersonWithBalance(Person(id = 1, name = "Rondu", currency = "INR"), 1_500_00),
        PersonWithBalance(Person(id = 2, name = "Sasti", currency = "INR"), -300_00)
    )

    @Before
    fun startOnTheNet() = runBlocking { TotalViewStore.setShowsBothSides(context, false) }

    @After
    fun leaveItOnTheNet() = runBlocking { TotalViewStore.setShowsBothSides(context, false) }

    private val theme = themes.first()

    @Composable
    private fun Total(people: List<PersonWithBalance>) {
        MeraPaisaTheme(theme) {
            CompositionLocalProvider(LocalAppTheme provides theme) {
                val scope = rememberCoroutineScope()
                val bothSides by TotalViewStore.showsBothSides(context).collectAsState(initial = false)
                NetPosition(
                    totals = netTotalsByCurrency(people),
                    sides = sidesByCurrency(people),
                    bothSides = bothSides,
                    onToggle = { scope.launch { TotalViewStore.setShowsBothSides(context, !bothSides) } }
                )
            }
        }
    }

    @Test
    fun aTapTurnsTheTotalOverAndTheChoiceComesBackWithTheScreen() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Total(people) }
        compose.onNode(hasStateDescription("Net")).assertExists()
        compose.onNodeWithText("Owed to you").assertExists()

        compose.onNode(hasStateDescription("Net")).performClick()
        compose.waitUntil(5_000) { runBlocking { TotalViewStore.showsBothSides(context).first() } }
        compose.onNode(hasStateDescription("Both sides")).assertExists()
        compose.onNodeWithText("Overall").assertExists()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasStateDescription("Both sides")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theTapSaysWhatItWillShow() {
        compose.setContent { Total(people) }
        compose.onNode(hasStateDescription("Net"))
            .assert(SemanticsMatcher("a tap shows both sides") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show both sides"
            })
    }

    @Test
    fun withEveryoneEvenThereIsNothingToTurn() {
        val even = listOf(PersonWithBalance(Person(id = 1, name = "Rondu", currency = "INR"), 0))
        compose.setContent { Total(even) }

        compose.onNodeWithText("You're even with everyone").assertExists()
        compose.onNode(hasClickAction()).assertDoesNotExist()
    }
}
