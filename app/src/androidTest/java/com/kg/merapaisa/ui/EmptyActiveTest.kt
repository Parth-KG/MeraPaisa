package com.kg.merapaisa.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An empty Active tab says why it is empty: a new ledger, or everyone even. */
@RunWith(AndroidJUnit4::class)
class EmptyActiveTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(everyoneEven: Boolean) {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    EmptyState(tab = Tab.Active, everyoneEven = everyoneEven)
                }
            }
        }
    }

    @Test
    fun aNewLedgerSaysNoOneIsHereYet() {
        show(everyoneEven = false)
        compose.onNodeWithText("No one here yet.").assertExists()
        compose.onNodeWithText("Add a person").assertExists()
    }

    @Test
    fun everyoneEvenIsNotAnEmptyLedger() {
        show(everyoneEven = true)
        compose.onNodeWithText("It's quiet in here.").assertExists()
        compose.onNodeWithText("No one here yet.").assertDoesNotExist()
        compose.onNodeWithText("Add a person").assertExists()
    }
}
