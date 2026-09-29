package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.data.currencySymbol
import com.kg.merapaisa.data.formatMinorPlain
import com.kg.merapaisa.data.parseAmountToMinor
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Corrects or removes a single entry. Before this, a mistyped amount could only be papered over
 * with a compensating entry, leaving the mistake in the history for good.
 *
 * Two fields and a way out is a form, not a decision, so it arrives on a sheet rather than in a
 * dialog. It used to be an AlertDialog opened on top of another AlertDialog, which stacked two
 * scrims over the history and left the entry being corrected hidden behind both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntrySheet(
    entry: Transaction,
    currency: String,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    // The digits only, no symbol and no grouping, because this is a field the user types back
    // into and a comma would stop it parsing. A leading minus is how you flip which way it runs.
    var amount by remember(entry.id) {
        mutableStateOf(formatMinorPlain(entry.amountMinor, currency, trimZeros = true))
    }
    var note by remember(entry.id) { mutableStateOf(entry.note) }

    val amountMinor = parseAmountToMinor(amount)
    val isValid = amountMinor != null && amountMinor != 0L

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = Shapes.sheet,
        containerColor = theme.surface,
        contentColor = theme.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Edit entry",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.lg))

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount in ${currencySymbol(currency)}") },
                supportingText = {
                    // Says which way the entry runs while it is valid, and what to type when it
                    // is not. The minus is the plain keyboard one, because that is the key the
                    // field can actually read.
                    Text(
                        when {
                            amountMinor != null && amountMinor > 0L -> "They owe you this"
                            amountMinor != null && amountMinor < 0L -> "You owe them this"
                            amountMinor == 0L ->
                                "An entry of zero moves nothing. Type an amount above zero."
                            amount.isBlank() ->
                                "Type the amount. A minus in front flips which way this entry runs."
                            else ->
                                "That is not an amount this app can read. Digits and one decimal " +
                                    "point, with a minus in front to flip which way it runs."
                        },
                        style = MeraPaisaType.label,
                        color = if (isValid) theme.textSecondary else theme.negative
                    )
                },
                isError = !isValid,
                singleLine = true,
                // Text rather than Decimal: the decimal keypad on most phones has no minus, and
                // the minus is the only way to turn an entry around.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                placeholder = { Text("Dinner, cab fare") },
                singleLine = true,
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )

            Spacer(Modifier.height(Spacing.md))
            // Removing the line outright, kept apart from the two buttons that close the form so
            // it cannot be hit while reaching for Save.
            TextButton(
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDelete(entry) }
                },
                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = Spacing.sm)
            ) {
                Text("Delete this entry", style = MeraPaisaType.action, color = theme.negative)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                shape = Shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                border = BorderStroke(1.dp, theme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textPrimary)
            ) {
                Text("Cancel", style = MeraPaisaType.action)
            }
            Button(
                onClick = {
                    val minor = amountMinor ?: return@Button
                    // The sheet plays its way out before the entry is written, so the list behind
                    // it is not seen reordering under a sheet that is still on screen.
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onSave(entry.copy(amountMinor = minor, note = note.trim()))
                    }
                },
                enabled = isValid,
                shape = Shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.background
                )
            ) {
                Text("Save entry", style = MeraPaisaType.action)
            }
        }
    }
}
