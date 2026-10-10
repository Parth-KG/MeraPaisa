package com.kg.merapaisa.ui.dialogs

import com.kg.merapaisa.data.isTypableAmount
import com.kg.merapaisa.ui.ButtonLabel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import com.kg.merapaisa.ui.DecisionDialog
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.format.SignStyle
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.SecondaryAction

/** A field of [EditEntrySheet], for opening it with that field ready to type in. */
enum class EntryField { Amount, Note }

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
    onDismiss: () -> Unit,
    focus: EntryField? = null
) {
    val theme = LocalAppTheme.current
    // The digits only, no symbol and no grouping, because this is a field the user types back
    // into and a comma would stop it parsing. A leading minus is how you flip which way it runs.
    // Paise keep both digits, as every other amount does: trimming zeros turned 1200.50 into
    // "1200.5". Only a whole ".00" is dropped.
    val initialAmount = remember(entry.id) {
        // The app's own minus, as the history beside it shows. The parser reads either.
        formatMinorPlain(entry.amountMinor, currency).removeSuffix(".00").replaceFirst("-", "\u2212")
    }
    // The cursor starts after what is there, so a long-press that asked for a field lands ready to
    // add to it.
    var amount by remember(entry.id) {
        mutableStateOf(TextFieldValue(initialAmount, TextRange(initialAmount.length)))
    }
    var note by remember(entry.id) { mutableStateOf(TextFieldValue(entry.note, TextRange(entry.note.length))) }
    val amountFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }

    // An untouched field keeps the stored figure. Yen is kept in hundredths and a split can leave
    // ¥33.34 in one entry, which the field shows as 33; saving a new note used to write back 33
    // and lose the rest.
    val amountMinor = if (amount.text == initialAmount) entry.amountMinor else parseAmountToMinor(amount.text)
    val isValid = amountMinor != null && amountMinor != 0L
    var confirmingDelete by remember(entry.id) { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val scroll = rememberScrollState()

    SheetFrame(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Asked from inside the sheet, which has its own window: from outside, the request ran
        // before that window had laid out the fields and found nothing to focus.
        if (focus != null) {
            LaunchedEffect(entry.id, focus) {
                val target = if (focus == EntryField.Amount) amountFocus else noteFocus
                repeat(30) {
                    withFrameNanos { }
                    if (target.requestFocus()) return@LaunchedEffect
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scroll)
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
                onValueChange = { if (isTypableAmount(it.text, currency, allowNegative = true)) amount = it },
                label = { Text("Amount in ${currencySymbol(currency)}") },
                supportingText = {
                    // Says which way the entry runs while it is valid, and what to type when it
                    // is not. Worded as a change, not a debt: "You owe them this" was wrong for
                    // the entry that settled someone up, which is a payment to you. The minus is the plain keyboard one, because that is the key the
                    // field can actually read.
                    Text(
                        when {
                            amountMinor != null && amountMinor > 0L -> "In your favour: they owe you this much more"
                            amountMinor != null && amountMinor < 0L -> "In their favour: they owe you this much less"
                            amountMinor == 0L ->
                                "An entry of zero moves nothing. Type another amount, or delete " +
                                    "this entry."
                            amount.text.isBlank() ->
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).focusRequester(amountFocus)
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                placeholder = { Text("Dinner, cab fare") },
                singleLine = true,
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).focusRequester(noteFocus)
            )

            Spacer(Modifier.height(Spacing.md))
            // Removing the line outright, kept apart from the two buttons that close the form so
            // it cannot be hit while reaching for Save.
            TextButton(
                // Asked first. It deleted on the tap, and an entry is part of the balance.
                onClick = { confirmingDelete = true },
                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = Spacing.sm)
            ) {
                ButtonLabel("Delete this entry", color = theme.textPrimary)
            }
        }

        SheetFoot(above = scroll) {
            SecondaryAction("Keep it as it was", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
            PrimaryAction("Save entry", enabled = isValid) {
                val minor = amountMinor ?: return@PrimaryAction
                // The sheet plays its way out before the entry is written, so the list behind
                // it is not seen reordering under a sheet that is still on screen.
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onSave(entry.copy(amountMinor = minor, note = note.text.trim()))
                }
            }
        }
    }

    if (confirmingDelete) {
        val figure = amountString(entry.amountMinor, currency, SignStyle.None)
        DecisionDialog(
            title = "Delete this entry?",
            body = "The $figure goes from the history, and the balance moves back by that much. " +
                "This can't be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Keep it",
            onConfirm = {
                confirmingDelete = false
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDelete(entry) }
            },
            onDismiss = { confirmingDelete = false }
        )
    }
}
