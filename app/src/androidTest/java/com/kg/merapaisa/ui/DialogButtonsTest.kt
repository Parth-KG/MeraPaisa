package com.kg.merapaisa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A decision's two buttons either share a line or stack, whole.
 *
 * Material's own row wrapped in reverse: with a long name in the way out, the confirm dropped under
 * it and the long button ran to the dialog's edge. These pin the two shapes DialogButtons allows.
 */
@RunWith(AndroidJUnit4::class)
class DialogButtonsTest {

    @get:Rule
    val compose = createComposeRule()

    // At the phone's own text size whatever that is, so widths here are the test's, not the phone's.
    private fun show(width: Dp, confirm: String, dismiss: String, equalWidths: Boolean = false) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1f)) {
                Box(Modifier.width(width).testTag("slot")) {
                    DialogButtons(
                        equalWidths = equalWidths,
                        confirm = { Choice(confirm, "confirm", filled = equalWidths) },
                        dismiss = { Choice(dismiss, "dismiss", filled = equalWidths) }
                    )
                }
            }
        }
    }

    @Composable
    private fun Choice(label: String, tag: String, filled: Boolean) {
        val modifier = Modifier.heightIn(min = 48.dp).testTag(tag)
        if (filled) Button(onClick = {}, modifier = modifier) { ButtonLabel(label) }
        else TextButton(onClick = {}, modifier = modifier) { ButtonLabel(label) }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun DpRect.across() = right - left

    @Test
    fun twoShortAnswersShareALineWithTheConfirmLast() {
        show(360.dp, confirm = "Delete", dismiss = "Keep Asha")

        val confirm = bounds("confirm")
        val dismiss = bounds("dismiss")
        assertEquals("one line", confirm.top, dismiss.top)
        assertTrue("the confirm comes after the way out", confirm.left > dismiss.right)
        assertEquals("aligned to the end", bounds("slot").right, confirm.right)
    }

    @Test
    fun aLongAnswerStacksBothAtFullWidthWithTheConfirmOnTop() {
        show(240.dp, confirm = "Delete", dismiss = "Keep Chaitanya Venkataraman")

        val confirm = bounds("confirm")
        val dismiss = bounds("dismiss")
        val slot = bounds("slot")
        assertTrue("the confirm is on top", confirm.bottom <= dismiss.top)
        assertEquals(slot.across(), confirm.across())
        assertEquals(slot.across(), dismiss.across())
    }

    @Test
    fun equalWidthsGiveBothTheWiderOnesWidth() {
        show(400.dp, confirm = "Convert", dismiss = "Keep the amounts", equalWidths = true)

        val confirm = bounds("confirm")
        val dismiss = bounds("dismiss")
        assertEquals("one line", confirm.top, dismiss.top)
        assertEquals(dismiss.across(), confirm.across())
    }
}
