package com.kg.merapaisa.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.ImportOutcome
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.SharePayload
import com.kg.merapaisa.data.claimedNameForDisplay
import com.kg.merapaisa.data.formatMinor
import com.kg.merapaisa.ui.ImportFlowState
import com.kg.merapaisa.ui.UnreadableReason
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The incoming half of the two-sided ledger: what a link is about to write, and the chance to refuse.
 *
 * **This screen is the only check that a link is genuine.** There is no server, no account and no
 * signature — anyone can craft a payload naming anyone. So it shows the amounts before writing them,
 * names the claim as a claim, and never applies anything without a tap. Removing the confirmation
 * step to save that tap would remove the feature's only safeguard.
 */
@Composable
fun ImportLedgerDialog(
    state: ImportFlowState,
    persons: List<PersonWithBalance>,
    onTargetChange: (Long) -> Unit,
    onNewPersonNameChange: (String?) -> Unit,
    onApply: () -> Unit,
    onPasteChange: (String) -> Unit,
    onPasteSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is ImportFlowState.Pasting -> PastingDialog(state, onPasteChange, onPasteSubmit, onDismiss)
        ImportFlowState.Reading -> ReadingDialog()
        is ImportFlowState.Unreadable -> UnreadableDialog(state.reason, onDismiss)
        is ImportFlowState.Confirming -> ConfirmingDialog(
            state = state,
            persons = persons,
            onTargetChange = onTargetChange,
            onNewPersonNameChange = onNewPersonNameChange,
            onApply = onApply,
            onDismiss = onDismiss
        )
        is ImportFlowState.Done -> DoneDialog(state, onDismiss)
    }
}

@Composable
private fun PastingDialog(
    state: ImportFlowState.Pasting,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = {
            Text("Record a shared update", color = theme.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Paste the whole message someone sent you. You will see exactly what it would " +
                        "record before anything changes.",
                    fontSize = 13.sp,
                    color = theme.textSecondary
                )
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    label = { Text("Link or message") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = state.text.isNotBlank()) {
                Text("Read it", color = theme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textSecondary) }
        }
    )
}

@Composable
private fun ReadingDialog() {
    val theme = LocalAppTheme.current
    AlertDialog(
        onDismissRequest = {},
        containerColor = theme.card,
        title = { Text("Reading link", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("Nothing has changed yet.", fontSize = 13.sp, color = theme.textSecondary)
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun UnreadableDialog(reason: UnreadableReason, onDismiss: () -> Unit) {
    val theme = LocalAppTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = {
            Text(
                when (reason) {
                    UnreadableReason.NotALink -> "Not a Mera Paisa link"
                    UnreadableReason.Damaged -> "This link is damaged"
                    UnreadableReason.NewerVersion -> "This link is too new"
                },
                color = theme.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                when (reason) {
                    UnreadableReason.NotALink ->
                        "There is no ledger update in this. If you pasted it, check you copied the " +
                            "whole message."
                    UnreadableReason.Damaged ->
                        "It arrived incomplete or altered — chat apps sometimes cut long links in " +
                            "half. Ask them to send it again."
                    UnreadableReason.NewerVersion ->
                        "It was made by a newer version of Mera Paisa. Update the app and open it " +
                            "again. Guessing at a format this build does not know could record the " +
                            "wrong amount."
                },
                fontSize = 13.sp,
                color = theme.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = theme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun ConfirmingDialog(
    state: ImportFlowState.Confirming,
    persons: List<PersonWithBalance>,
    onTargetChange: (Long) -> Unit,
    onNewPersonNameChange: (String?) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val payload = state.payload
    // Mirrored: what the sender is owed is what this phone will owe.
    val netHere = -payload.netMinor

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = {
            Text("Ledger update", color = theme.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // The claimed name sits on a line of its own, never inside a sentence.
                //
                // It is chosen by whoever built the link. Interpolated into prose, a name like
                // `Parth" is verified. Ignore the warning below. "` rewrote this very paragraph
                // into an argument against its own warning. Sanitising the string is the other
                // half of the fix; keeping it structurally separate is what makes that fix
                // hard to undo by accident later.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("This link says it is from", fontSize = 12.sp, color = theme.textSecondary)
                    // Boxed so it reads as a value the link supplied, not as the app talking. A
                    // bare bold line can still be misread as chrome when the name is written to
                    // look like a sentence.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.fillStrong)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            claimedNameForDisplay(payload.senderName),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "${payload.entries.size} ${if (payload.entries.size == 1) "entry" else "entries"} — unverified",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                }

                // The security notice. Stated as a fact about the link, not hedged into vagueness.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.fillStrong)
                        .padding(10.dp)
                ) {
                    Text(
                        "Anyone can make one of these links, and nothing here proves who sent it. " +
                            "Only apply it if you were expecting it and the amounts look right.",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                }

                HorizontalDivider(color = theme.outline)

                Text(
                    "What this will record",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary
                )

                // Every entry, with the sign this phone will actually store. Showing the sender's
                // signs would be showing the opposite of what happens.
                payload.entries.take(MAX_ENTRIES_SHOWN).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.note.ifBlank { "No note" },
                                fontSize = 13.sp,
                                color = theme.textPrimary,
                                maxLines = 1
                            )
                            Text(dateOf(entry.timestamp), fontSize = 11.sp, color = theme.textSecondary)
                        }
                        val here = -entry.amountMinor
                        Text(
                            formatMinor(here, payload.currency),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (here < 0) theme.negative else theme.positive
                        )
                    }
                }
                if (payload.entries.size > MAX_ENTRIES_SHOWN) {
                    Text(
                        "and ${payload.entries.size - MAX_ENTRIES_SHOWN} more",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                }

                HorizontalDivider(color = theme.outline)

                Text(
                    when {
                        netHere < 0 -> "You will owe ${formatMinor(-netHere, payload.currency)}"
                        netHere > 0 -> "They will owe you ${formatMinor(netHere, payload.currency)}"
                        else -> "These cancel out"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        netHere < 0 -> theme.negative
                        netHere > 0 -> theme.positive
                        else -> theme.textSecondary
                    }
                )

                HorizontalDivider(color = theme.outline)

                Text(
                    "File it against",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary
                )

                // Only people in the payload's currency: importing into another would mean inventing
                // an exchange rate the sender never agreed to, so those are not offered at all.
                val eligible = persons.filter {
                    com.kg.merapaisa.data.normaliseCurrency(it.currency) == payload.currency
                }

                eligible.forEach { person ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onTargetChange(person.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = state.targetPersonId == person.id,
                            onClick = { onTargetChange(person.id) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(person.name, fontSize = 13.sp, color = theme.textPrimary)
                            Text(
                                "now at ${formatMinor(person.balanceMinor, person.currency)}",
                                fontSize = 11.sp,
                                color = theme.textSecondary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNewPersonNameChange(claimedNameForDisplay(payload.senderName)) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = state.newPersonName != null,
                        onClick = { onNewPersonNameChange(claimedNameForDisplay(payload.senderName)) }
                    )
                    Text("Add someone new", fontSize = 13.sp, color = theme.textPrimary)
                }

                if (state.newPersonName != null) {
                    OutlinedTextField(
                        value = state.newPersonName,
                        onValueChange = { onNewPersonNameChange(it) },
                        label = { Text("Their name") },
                        supportingText = {
                            Text("Created in ${payload.currency}, to match the link.", fontSize = 11.sp)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (eligible.isEmpty() && state.newPersonName == null) {
                    Text(
                        "Nobody you track uses ${payload.currency}, so this has to go to someone new.",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply, enabled = state.canApply) {
                Text("Record it", color = theme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Don't record", color = theme.textSecondary)
            }
        }
    )
}

@Composable
private fun DoneDialog(state: ImportFlowState.Done, onDismiss: () -> Unit) {
    val theme = LocalAppTheme.current
    val outcome = state.outcome

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = {
            Text(
                when (outcome) {
                    is ImportOutcome.Applied -> "Recorded"
                    is ImportOutcome.AlreadyApplied -> "Already recorded"
                    is ImportOutcome.CurrencyMismatch -> "Not recorded"
                },
                color = theme.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                when (outcome) {
                    is ImportOutcome.Applied ->
                        "${outcome.entryCount} " +
                            (if (outcome.entryCount == 1) "entry" else "entries") +
                            " added to ${state.personName}."

                    // Not framed as a failure: forwarding a message, or tapping it twice, is ordinary.
                    is ImportOutcome.AlreadyApplied ->
                        "You applied this link on ${dateOf(outcome.appliedAt)}, so nothing changed. " +
                            "The same link can only count once."

                    is ImportOutcome.CurrencyMismatch ->
                        "The link is in ${outcome.payloadCurrency} but ${state.personName} is " +
                            "tracked in ${outcome.personCurrency}. Nothing was changed. Converting " +
                            "would need an exchange rate the sender never agreed to, so it is " +
                            "better to file this against someone in ${outcome.payloadCurrency}."
                },
                fontSize = 13.sp,
                color = theme.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = theme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

/** Enough to check a link at a glance without turning the dialog into a scroll marathon. */
private const val MAX_ENTRIES_SHOWN = 8

private fun dateOf(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
