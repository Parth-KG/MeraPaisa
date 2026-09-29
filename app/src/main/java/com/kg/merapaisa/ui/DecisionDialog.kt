package com.kg.merapaisa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * The confirm button takes the accent, as every action does. It used to take the negative ink
 * when the action destroyed something, but that ink is for amounts; the title and the button's
 * own word ("Delete") say what it does.
 */
@Composable
internal fun DecisionDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String,
    confirmColour: Color = LocalAppTheme.current.primary,
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
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(confirmLabel, style = MeraPaisaType.action, color = confirmColour)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(dismissLabel, style = MeraPaisaType.action, color = theme.textSecondary)
            }
        }
    )
}
