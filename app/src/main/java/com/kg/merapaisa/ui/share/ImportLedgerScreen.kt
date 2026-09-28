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
import com.kg.merapaisa.data.ReconcileItem
import com.kg.merapaisa.data.ReconcilePlan
import com.kg.merapaisa.data.SharePayload
import com.kg.merapaisa.data.claimedNameForDisplay
import com.kg.merapaisa.ui.format.amountString
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
    onToggleItem: (String) -> Unit,
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
            onToggleItem = onToggleItem,
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
    onToggleItem: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val payload = state.payload
    // Mirrored: what the sender is owed is what this phone will owe.
    //
    // Once there is a comparison to go on, the figure follows the tick boxes instead of the raw
    // payload. Appending everything is only what happens when there is nothing to compare against.
    val netHere = state.plan?.takeIf { it.comparable }?.netChangeFor(state.selected)
        ?: -payload.netMinor

    AlertDialog(
        // Not dismissable once the write has started. The entries are already going in by then, so
        // a back tap or a tap outside would hide the dialog without stopping anything — and the
        // result would then reappear on its own, announcing a change the user had just waved away.
        onDismissRequest = { if (!state.busy) onDismiss() },
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
                    // "contains" rather than "will record" once the tick boxes decide what is
                    // recorded: this list is the link's contents, not the outcome.
                    if (state.showsDifferences) "What the link contains" else "What this will record",
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
                            amountString(here, payload.currency),
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

                // With a comparison in play the figure is a *change* to a balance that already
                // exists, and "you will owe ₹12" would read as the whole of it. Naming where the
                // person lands afterwards is the one phrasing that cannot be misread — and it is
                // the number the user can check against the list behind this dialog.
                val target = persons.firstOrNull { it.id == state.targetPersonId }
                val after = if (state.showsDifferences && target != null) target.balanceMinor + netHere else null

                Text(
                    when {
                        after != null && netHere == 0L ->
                            "Nothing changes. ${target!!.name} stays at ${amountString(after, payload.currency)}"
                        after != null ->
                            "${target!!.name} ends up at ${amountString(after, payload.currency)}"
                        netHere < 0 -> "You will owe ${amountString(-netHere, payload.currency)}"
                        netHere > 0 -> "They will owe you ${amountString(netHere, payload.currency)}"
                        else -> "These cancel out"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    // Colours what is actually being stated: the resulting balance when there is
                    // one, otherwise the change itself.
                    color = when {
                        (after ?: netHere) < 0L -> theme.negative
                        (after ?: netHere) > 0L -> theme.positive
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
                                "now at ${amountString(person.balanceMinor, person.currency)}",
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

                if (state.showsDifferences && state.plan != null) {
                    DifferencesSection(
                        plan = state.plan,
                        selected = state.selected,
                        currency = payload.currency,
                        onToggleItem = onToggleItem
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
            // Disabled while writing, for the same reason: "Don't record" cannot be offered at a
            // moment when tapping it records anyway.
            TextButton(onClick = onDismiss, enabled = !state.busy) {
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
                    is ImportOutcome.Reconciled ->
                        if (outcome.changedNothing) "Nothing to change" else "Ledgers match"
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

                    // Spelled out rather than totalled. Somebody who has just agreed to delete
                    // entries off their own ledger should be told that it happened, not handed a
                    // count of additions that quietly omits it.
                    is ImportOutcome.Reconciled ->
                        if (outcome.changedNothing) {
                            "You and ${state.personName} already agree. Nothing was changed."
                        } else {
                            listOfNotNull(
                                outcome.added.takeIf { it > 0 }?.let { countOf(it, "entry", "entries") + " added" },
                                outcome.updated.takeIf { it > 0 }?.let { countOf(it, "entry", "entries") + " updated" },
                                outcome.removed.takeIf { it > 0 }?.let { countOf(it, "entry", "entries") + " removed" }
                            ).joinToString(", ").replaceFirstChar { it.uppercase() } +
                                " for ${state.personName}."
                        }

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

/**
 * Where the two ledgers disagree, and what to do about each difference.
 *
 * Only shown when there is something to say — a link that is purely new entries is the ordinary
 * case and gets no section at all, because "3 new entries, none of which conflict" is exactly what
 * someone tapping a ledger link already assumes.
 *
 * Edits and deletions arrive unticked. They overwrite or destroy something already in the ledger,
 * and nothing about a share link establishes that the sender is who they say — so accepting one is
 * a decision, made here, by a person, every time.
 */
@Composable
private fun DifferencesSection(
    plan: ReconcilePlan,
    selected: Set<String>,
    currency: String,
    onToggleItem: (String) -> Unit
) {
    val theme = LocalAppTheme.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = theme.fillStrong)

        Text(
            "Compared with what you have",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.textPrimary
        )

        if (plan.edited.isNotEmpty()) {
            DifferenceGroup(
                heading = "They changed " + countOf(plan.edited.size, "entry", "entries"),
                explanation = "Tick to take their version. Left unticked, yours stays as it is."
            ) {
                plan.edited.take(MAX_ENTRIES_SHOWN).forEach { item ->
                    DifferenceRow(
                        checked = item.uid in selected,
                        onToggle = { onToggleItem(item.uid) },
                        title = item.theirNote.ifBlank { "No note" },
                        detail = buildString {
                            if (item.amountDiffers) {
                                append(amountString(item.localAmountMinor, currency))
                                append(" \u2192 ")
                                append(amountString(item.theirAmountMinor, currency))
                            }
                            if (item.amountDiffers && item.noteDiffers) append(" \u00B7 ")
                            if (item.noteDiffers) {
                                append("note was ")
                                append(item.localNote.ifBlank { "empty" })
                            }
                        },
                        // Their date, not the one currently stored: accepting an edit takes their
                        // version of the entry whole, the date included. Showing the local date
                        // beside an amount that is about to change would name a row this tick is
                        // going to alter in a way the row does not admit to.
                        date = dateOf(item.theirTimestamp)
                    )
                }
                MoreThanShown(plan.edited.size)
            }
        }

        if (plan.deletedBySender.isNotEmpty()) {
            DifferenceGroup(
                heading = "They removed " + countOf(plan.deletedBySender.size, "entry", "entries"),
                explanation = "Tick to remove it here too. This deletes it from your ledger."
            ) {
                plan.deletedBySender.take(MAX_ENTRIES_SHOWN).forEach { item ->
                    DifferenceRow(
                        checked = item.uid in selected,
                        onToggle = { onToggleItem(item.uid) },
                        title = item.note.ifBlank { "No note" },
                        detail = amountString(item.amountMinor, currency),
                        date = dateOf(item.timestamp)
                    )
                }
                MoreThanShown(plan.deletedBySender.size)
            }
        }

        // No tick boxes below this point: both are things to know, not decisions to make.
        if (plan.onlyYours.isNotEmpty()) {
            DifferenceGroup(
                heading = countOf(plan.onlyYours.size, "entry", "entries") + " they have not seen",
                explanation = "Yours, and not in their ledger. Send them a link to even it up."
            ) {
                plan.onlyYours.take(MAX_ENTRIES_SHOWN).forEach { item ->
                    Text(
                        "${item.note.ifBlank { "No note" }} \u00B7 ${amountString(item.amountMinor, currency)}",
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MoreThanShown(plan.onlyYours.size)
            }
        }

        if (plan.unchanged.isNotEmpty()) {
            Text(
                countOf(plan.unchanged.size, "entry", "entries") + " already match, and stay as they are.",
                fontSize = 12.sp,
                color = theme.textSecondary
            )
        }
    }
}

/**
 * Says so when a section is showing fewer rows than its heading counts.
 *
 * The heading says "they removed 12 entries" and the list shows eight, and the four with no row
 * have no tick box either — so they are staying whether or not that is what anyone wanted. Left
 * unsaid, the heading is simply a lie about what the screen is offering. The payload preview above
 * has always disclosed its own cap; these sections did not.
 */
@Composable
private fun MoreThanShown(total: Int) {
    if (total <= MAX_ENTRIES_SHOWN) return
    val theme = LocalAppTheme.current
    Text(
        "and ${total - MAX_ENTRIES_SHOWN} more, left as they are",
        fontSize = 11.sp,
        color = theme.textSecondary
    )
}

@Composable
private fun DifferenceGroup(
    heading: String,
    explanation: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(theme.fillStrong)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(heading, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
        Text(explanation, fontSize = 11.sp, color = theme.textSecondary)
        content()
    }
}

@Composable
private fun DifferenceRow(
    checked: Boolean,
    onToggle: () -> Unit,
    title: String,
    detail: String,
    date: String
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 12.sp,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("$detail \u00B7 $date", fontSize = 11.sp, color = theme.textSecondary)
        }
    }
}

private fun countOf(n: Int, one: String, many: String): String = "$n " + if (n == 1) one else many

/** Enough to check a link at a glance without turning the dialog into a scroll marathon. */
private const val MAX_ENTRIES_SHOWN = 8

private fun dateOf(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
