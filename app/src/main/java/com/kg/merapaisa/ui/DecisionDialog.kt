package com.kg.merapaisa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing

/**
 * A decision, which is the one thing a dialog is for: it names what will happen in the title and
 * in the button, so neither the question nor the answer depends on reading the sentence between
 * them.
 *
 * Deleting a person, deleting a group, reversing entries, clearing a history, replacing a ledger
 * and recording an update link's deletions all ask through this. They used to be built one at a
 * time, and two of them had fallen back to Material's own dialog: a 28dp corner, a smaller title
 * and buttons in a different ink from every other question the app asks.
 *
 * Both buttons name an outcome, the way out included: "Keep Asha" says what not deleting means
 * where "Cancel" says only that something stops.
 *
 * [warning] is the one line that must not be skimmed, set apart in the negative ink.
 *
 * The confirm takes the accent, as every action does, unless the decision is [destructive]:
 * deleting a person, a group, an expense or an entry, clearing a history, replacing the ledger, or
 * recording an update link's deletions. Then its word is in the negative ink, so "Delete" does not
 * wear the colour of "Settle up" and stands apart from the way out beside it. The word only, never
 * a filled button: the ink marks the one answer that takes something away, and nothing else.
 * Settling up and reversing entries write a line rather than remove one, so they keep the accent.
 */
@Composable
internal fun DecisionDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String,
    destructive: Boolean = false,
    warning: String? = null
) {
    val theme = LocalAppTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Shapes.medium,
        containerColor = theme.card,
        title = { Text(title, style = MeraPaisaType.screenTitle, color = theme.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(body, style = MeraPaisaType.body, color = theme.textSecondary)
                if (warning != null) {
                    Text(warning, style = MeraPaisaType.body, color = theme.negative)
                }
            }
        },
        // Both buttons go in the one slot, laid out by DialogButtons rather than Material's row.
        confirmButton = {
            DialogButtons(
                confirm = {
                    TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                        ButtonLabel(confirmLabel, color = if (destructive) theme.negative else theme.primary)
                    }
                },
                dismiss = {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        ButtonLabel(dismissLabel, color = theme.textSecondary)
                    }
                }
            )
        }
    )
}

/**
 * A decision's two buttons: side by side at the end when both fit on one line, otherwise one
 * above the other at full width, the confirm on top.
 *
 * Material's dialog lays its buttons in a flow row that wraps in reverse. With a long name in the
 * way out ("Keep Chaitanya Venkataraman") the confirm dropped below it, the pair kept the row's gap
 * between them, and the long one ran out to the dialog's edge. Measured here instead, a pair either
 * fits whole or stacks whole.
 *
 * [equalWidths] gives both the wider one's width, for two filled buttons that are equally
 * outcomes, where a narrow one beside a wide one would read as the lesser choice.
 */
@Composable
internal fun DialogButtons(
    confirm: @Composable () -> Unit,
    dismiss: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    equalWidths: Boolean = false
) {
    Layout(content = { dismiss(); confirm() }, modifier = modifier) { measurables, constraints ->
        require(measurables.size == 2) { "DialogButtons takes exactly one button in each slot" }
        val (dismissButton, confirmButton) = measurables
        val gap = Spacing.sm.roundToPx()
        val dismissWidth = dismissButton.maxIntrinsicWidth(constraints.maxHeight)
        val confirmWidth = confirmButton.maxIntrinsicWidth(constraints.maxHeight)
        val widest = maxOf(dismissWidth, confirmWidth)
        val rowWidth = if (equalWidths) 2 * widest + gap else dismissWidth + confirmWidth + gap

        if (rowWidth <= constraints.maxWidth) {
            fun fixed(width: Int) = Constraints.fixedWidth(width).copy(maxHeight = constraints.maxHeight)
            val d = dismissButton.measure(fixed(if (equalWidths) widest else dismissWidth))
            val c = confirmButton.measure(fixed(if (equalWidths) widest else confirmWidth))
            val height = maxOf(d.height, c.height)
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else rowWidth
            layout(width, height) {
                // Aligned to the end, where a dialog's answers sit, the confirm last.
                c.placeRelative(width - c.width, (height - c.height) / 2)
                d.placeRelative(width - c.width - gap - d.width, (height - d.height) / 2)
            }
        } else {
            val full = Constraints.fixedWidth(constraints.maxWidth).copy(maxHeight = constraints.maxHeight)
            val c = confirmButton.measure(full)
            val d = dismissButton.measure(full)
            layout(constraints.maxWidth, c.height + gap + d.height) {
                c.placeRelative(0, 0)
                d.placeRelative(0, c.height + gap)
            }
        }
    }
}
