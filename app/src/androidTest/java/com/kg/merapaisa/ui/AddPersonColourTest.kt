package com.kg.merapaisa.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.dialogs.AddPersonDialog
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * In Add a person, the colour swatch stays where it started while a name is typed. It used to
 * follow the name, jumping with every letter (Parth, 15 Nov 2026, twice).
 */
@RunWith(AndroidJUnit4::class)
class AddPersonColourTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun typingANameLeavesTheColourWhereItStarted() {
        val theme = themes.first()
        compose.setContent {
            MeraPaisaTheme(theme) {
                CompositionLocalProvider(LocalAppTheme provides theme) {
                    // Indigo and teal taken, so a new person starts on olive.
                    AddPersonDialog(
                        onDismiss = {}, onAdd = { _, _, _, _, _ -> },
                        existingColours = listOf("#3F6DA8", "#2E8079")
                    )
                }
            }
        }
        val olive = hasContentDescription("Olive")
        compose.onNode(olive).assertIsSelected()
        val nameField = compose.onAllNodes(hasSetTextAction())[0]
        for (letter in "Chaitanya") {
            nameField.performTextInput(letter.toString())
            compose.onNode(olive).assertIsSelected()
        }
    }
}
