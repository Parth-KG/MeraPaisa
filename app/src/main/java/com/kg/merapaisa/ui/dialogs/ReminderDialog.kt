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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.data.buildActivityLog
import com.kg.merapaisa.ui.MainViewModel
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.shareText
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * A message about one balance, written for you and yours to edit before it goes.
 *
 * The draft is the whole point of the screen, so it is a field you can rewrite rather than a
 * preview: what this app knows is the figure, and what you know is how you talk to this person.
 */
@Composable
fun ReminderDialog(
    person: PersonWithBalance,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    // Keyed on the person: `getTransactions` builds a fresh Flow on every call, and collecting a
    // new one on each recomposition restarted the query every time the draft changed.
    val stream = remember(person.id) { viewModel.getTransactions(person.id) }
    val entries by stream.collectAsState(initial = emptyList())
    ReminderSheet(person = person, entries = entries, onDismiss = onDismiss)
}

/** The sheet itself, given the entries, so the gallery can draw it without a database. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSheet(
    person: PersonWithBalance,
    entries: List<Transaction>,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var message by remember(person.id) { mutableStateOf(buildReminderText(person)) }
    var includeLog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                "Send a reminder",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "It goes out through your phone's share sheet, so you pick which app sends it.",
                style = MeraPaisaType.body,
                color = theme.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.lg))

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message to ${person.name}") },
                textStyle = MeraPaisaType.body,
                minLines = 3,
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = includeLog,
                        enabled = entries.isNotEmpty(),
                        onValueChange = { includeLog = it },
                        role = Role.Switch
                    )
                    .heightIn(min = 48.dp)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Attach every entry", style = MeraPaisaType.bodyStrong, color = theme.textPrimary)
                    Text(
                        if (entries.isEmpty())
                            "There is nothing recorded against ${person.name} yet, so there is " +
                                "nothing to attach."
                        else
                            "Adds the full list under your message, with the balance after each one.",
                        style = MeraPaisaType.label,
                        color = theme.textSecondary
                    )
                }
                Switch(checked = includeLog, onCheckedChange = null, enabled = entries.isNotEmpty())
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
                Text("Don't send", style = MeraPaisaType.action)
            }
            Button(
                onClick = {
                    val finalText = if (includeLog && entries.isNotEmpty()) {
                        message + "\n\nEvery entry so far:\n" +
                            buildActivityLog(entries, person.currency)
                    } else {
                        message
                    }
                    // The sheet is played out before the share sheet arrives over it, so the two
                    // do not animate across each other.
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        shareText(context, finalText, "Send reminder via")
                        onDismiss()
                    }
                },
                enabled = message.isNotBlank(),
                shape = Shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.background
                )
            ) {
                Text("Send reminder", style = MeraPaisaType.action)
            }
        }
    }
}

/**
 * The draft, in the three shapes a balance can take.
 *
 * It used to open by apologising for asking, in the stock phrase a billing system uses, and at
 * zero it announced that everything was settled up, with an exclamation mark. Asking for money is
 * awkward enough without sounding like an invoice about it, and nobody writing to a person they
 * know types that sentence. Zero is not settled here either, since settled is something you choose
 * to do to somebody. It is even.
 *
 * The figure goes through `amountString`, so ₹1,00,000 arrives grouped the way the ledger shows it
 * rather than as a run of digits. The magnitude is sent without a sign, because the sentence
 * around it already says which way the money runs.
 */
private fun buildReminderText(person: PersonWithBalance): String {
    val amount = amountString(abs(person.balanceMinor), person.currency)
    return when {
        person.balanceMinor > 0 -> "Hi ${person.name}, you owe me $amount. Send it when you can."
        person.balanceMinor < 0 -> "Hi ${person.name}, I owe you $amount. Will send it soon."
        else -> "Hi ${person.name}, we're even now."
    }
}
