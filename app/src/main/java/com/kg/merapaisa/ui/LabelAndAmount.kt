package com.kg.merapaisa.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.ui.theme.Spacing

/**
 * Words on the left and a figure on the right, or the figure under the words when both will not
 * fit on one line.
 *
 * A plain Row lost one of them at large type. With the words measured first, a lakh figure was cut
 * to a lone "+"; with the figure measured first, a name shrank to one letter per line. The figure is
 * the thing being read, so it is always measured at full width first. The words get what is left,
 * and if that is less than [minLabel], the pair stacks: words on top at full width, the figure
 * under them on the right, where the eye already looks for it.
 */
@Composable
fun LabelAndAmount(
    modifier: Modifier = Modifier,
    minLabel: Dp = 112.dp,
    gap: Dp = Spacing.md,
    label: @Composable () -> Unit,
    amount: @Composable () -> Unit
) {
    Layout(contents = listOf(label, amount), modifier = modifier) { (labelParts, amountParts), c ->
        val width = c.maxWidth
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val figure = amountParts.first().measure(loose)
        val room = width - figure.width - gap.roundToPx()

        if (room >= minLabel.roundToPx()) {
            val words = labelParts.first().measure(loose.copy(maxWidth = room))
            val height = maxOf(words.height, figure.height).coerceIn(c.minHeight, c.maxHeight)
            layout(width, height) {
                words.place(0, (height - words.height) / 2)
                figure.place(width - figure.width, (height - figure.height) / 2)
            }
        } else {
            val words = labelParts.first().measure(loose)
            val height = (words.height + figure.height).coerceIn(c.minHeight, c.maxHeight)
            layout(width, height) {
                words.place(0, 0)
                figure.place(width - figure.width, words.height)
            }
        }
    }
}
