package com.kg.merapaisa.ui.dialogs

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
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
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.SecondaryAction
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
    val myUpiId by viewModel.myUpiId.collectAsState()
    val myName by produceState<String?>(null) { value = viewModel.myNameForUpi() }
    ReminderSheet(
        person = person,
        entries = entries,
        onDismiss = onDismiss,
        myUpiId = myUpiId,
        myName = myName,
        onSentWithPayLink = { viewModel.rememberReminderSent(person) }
    )
}

/** The sheet itself, given the entries, so the gallery can draw it without a database. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSheet(
    person: PersonWithBalance,
    entries: List<Transaction>,
    onDismiss: () -> Unit,
    /** Your own UPI ID: with it, a reminder to someone who owes you rupees carries a pay link. */
    myUpiId: String? = null,
    myName: String? = null,
    /** Called once a reminder went out with its pay link still in it. */
    onSentWithPayLink: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val payLink = reminderPayLink(person, myUpiId, myName)
    var message by remember(person.id, payLink) { mutableStateOf(buildReminderText(person, payLink, myUpiId)) }
    var includeLog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val scroll = rememberScrollState()

    SheetFrame(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scroll)
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
                // One word on one line. "Message to Chaitanya Venkataraman" wrapped at large type
                // and its second line ran into the text being typed; the message itself opens
                // with their name.
                label = { Text("Message", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

        SheetFoot(above = scroll) {
            SecondaryAction("Don't send", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
            PrimaryAction("Send reminder", enabled = message.isNotBlank()) {
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
                    // Asked about on the next open ("Did Asha pay you?") only while the link is
                    // still in the message; a reminder with the link edited out asks nothing.
                    if (payLink != null && finalText.contains(payLink)) onSentWithPayLink()
                    onDismiss()
                }
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
internal fun buildReminderText(person: PersonWithBalance, payLink: String? = null, myUpiId: String? = null): String {
    val amount = amountString(abs(person.balanceMinor), person.currency)
    val text = when {
        person.balanceMinor > 0 -> "Hi ${person.name}, you owe me $amount. Send it when you can."
        person.balanceMinor < 0 -> "Hi ${person.name}, I owe you $amount. Will send it soon."
        else -> "Hi ${person.name}, we're even now."
    }
    // The link opens their UPI app with the amount filled in (the ₹1 test found a link sent through
    // a chat app arrives tappable). The ID and amount in plain text are for anyone whose app does
    // not open it, or who would rather type.
    return if (payLink != null && myUpiId != null) {
        "$text\n\nPay by UPI: $payLink\nOr to $myUpiId, $amount"
    } else text
}

/**
 * The pay link a reminder carries: only to someone who owes you, in rupees (UPI is rupees only),
 * once your own UPI ID is set. Null otherwise.
 */
internal fun reminderPayLink(person: PersonWithBalance, myUpiId: String?, myName: String?): String? {
    if (myUpiId == null || !com.kg.merapaisa.data.isUpiId(myUpiId)) return null
    if (com.kg.merapaisa.data.normaliseCurrency(person.currency) != "INR" || person.balanceMinor <= 0) return null
    return com.kg.merapaisa.data.upiPayLink(myUpiId, myName, person.balanceMinor)
}
