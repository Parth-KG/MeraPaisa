package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.SUPPORTED_CURRENCIES
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Chips at double type on a narrow phone: whole, on one line, and never smaller than a fingertip.
 *
 * At large type the last chip in a row was squeezed until its word broke inside it ("Phot/o"),
 * and a currency chip sized to its one symbol was 34dp wide.
 */
@RunWith(AndroidJUnit4::class)
class ChoiceChipsLargeTypeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        val theme = themes.first()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                MeraPaisaTheme(theme) {
                    CompositionLocalProvider(LocalAppTheme provides theme) {
                        Column(Modifier.width(320.dp).testTag("form")) { content() }
                    }
                }
            }
        }
    }

    private fun lineCount(text: String): Int {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().lineCount
    }

    private fun assertInside(chip: DpRect, form: DpRect, name: String) {
        assertTrue("$name runs past the form's edge", chip.right <= form.right && chip.left >= form.left)
    }

    @Test
    fun shownAsChipsKeepTheirWordsWhole() {
        show {
            AvatarPicker(
                pfpType = "initials", onTypeChange = {}, emoji = "", onEmojiChange = {},
                hasPhoto = false, selectedColour = "", onColourChange = {}, onPickPhoto = {}
            )
        }

        val form = compose.onNodeWithTag("form").getUnclippedBoundsInRoot()
        listOf("Initials", "Emoji", "Photo").forEach { word ->
            assertEquals("$word broke over two lines", 1, lineCount(word))
            assertInside(compose.onNodeWithText(word).getUnclippedBoundsInRoot(), form, word)
        }
    }

    @Test
    fun aCurrencyChipIsAFullTarget() {
        show { CurrencyChips(selected = "INR", onSelect = {}) }

        val form = compose.onNodeWithTag("form").getUnclippedBoundsInRoot()
        SUPPORTED_CURRENCIES.forEach { code ->
            val chip = compose.onNodeWithContentDescription(code).getUnclippedBoundsInRoot()
            assertTrue("$code is ${chip.right - chip.left} wide", chip.right - chip.left >= 48.dp)
            assertTrue("$code is ${chip.bottom - chip.top} tall", chip.bottom - chip.top >= 48.dp)
            assertInside(chip, form, code)
        }
    }
}
