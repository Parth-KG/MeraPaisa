package com.kg.merapaisa.ui.share

import com.kg.merapaisa.data.ShareScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.ImportOutcome
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.ReconcilePlan
import com.kg.merapaisa.data.SharePayload
import com.kg.merapaisa.data.claimedNameForDisplay
import com.kg.merapaisa.data.normaliseCurrency
import com.kg.merapaisa.ui.ImportFlowState
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.TextRowInset
import com.kg.merapaisa.ui.UnreadableReason
import com.kg.merapaisa.ui.format.AmountText
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.DecisionDialog
import com.kg.merapaisa.ui.FootActions
import com.kg.merapaisa.ui.Paragraph
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.ScreenFrame
import com.kg.merapaisa.ui.SecondaryAction
import com.kg.merapaisa.ui.SectionHeading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The incoming half of the two-sided ledger: what a link is about to write, and the chance to refuse.
 *
 * **This screen is the only check that a link is genuine.** There is no server, no account and no
 * signature, so anyone can craft a payload naming anyone. It shows the amounts before writing them,
 * names the claim as a claim, and never applies anything without a tap. Removing the confirmation
 * step to save that tap would remove the feature's only safeguard.
 *
 * It was five AlertDialogs, and the largest of them held a preview of the link, a security notice,
 * a list of people and a per-entry reconcile list inside a box sized for a question. Everything
 * worth reading was behind a scroll bar a few lines tall. They are screens now, with the reading
 * in a LazyColumn and the two answers named at the foot.
 *
 * One AlertDialog is left, for the one thing here that destroys something: ticking entries the
 * sender deleted removes them from this ledger too, so that is asked as a question.
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
    onDismiss: () -> Unit,
    /** Where closing goes, named: Settings when it was opened from there, the ledger otherwise. */
    backLabel: String = "Back to your ledger"
) {
    when (state) {
        is ImportFlowState.Pasting -> PastingScreen(state, onPasteChange, onPasteSubmit, onDismiss)
        ImportFlowState.Reading -> ReadingScreen()
        is ImportFlowState.Unreadable -> UnreadableScreen(state.reason, onDismiss, backLabel)
        is ImportFlowState.Confirming -> ConfirmingScreen(
            state = state,
            persons = persons,
            onTargetChange = onTargetChange,
            onNewPersonNameChange = onNewPersonNameChange,
            onToggleItem = onToggleItem,
            onApply = onApply,
            onDismiss = onDismiss
        )
        is ImportFlowState.Done -> DoneScreen(state, onDismiss, backLabel)
    }
}

@Composable
private fun PastingScreen(
    state: ImportFlowState.Pasting,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    ScreenFrame(
        title = "Record an update link",
        onBack = onDismiss,
        footer = {
            FootActions {
                PrimaryAction("Check the link", enabled = state.text.isNotBlank(), onClick = onSubmit)
            }
        }
    ) {
        item {
            Paragraph(
                "Paste the whole message someone sent you. You will see exactly what it would " +
                    "record before anything changes."
            )
        }
        item { Spacer(Modifier.height(Spacing.lg)) }
        item {
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                label = { Text("Link or message", style = MeraPaisaType.label) },
                textStyle = MeraPaisaType.body,
                shape = Shapes.medium,
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )
        }
        item {
            Column(modifier = Modifier.padding(top = Spacing.xl)) {
                Paragraph(
                    "A link only counts once, so pasting the same one twice changes nothing.",
                    colour = theme.textSecondary
                )
            }
        }
    }
}

@Composable
private fun ReadingScreen() {
    val theme = LocalAppTheme.current
    ScreenFrame(title = "Reading the link", onBack = null) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = theme.primary)
                Text(
                    "Nothing has changed yet.",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary
                )
            }
        }
    }
}

@Composable
private fun UnreadableScreen(reason: UnreadableReason, onDismiss: () -> Unit, backLabel: String) {
    ScreenFrame(
        title = when (reason) {
            UnreadableReason.NotALink -> "Not a Mera Paisa link"
            UnreadableReason.Damaged -> "This link is damaged"
            UnreadableReason.NewerVersion -> "This link is too new"
        },
        onBack = onDismiss,
        footer = { FootActions { PrimaryAction(backLabel, enabled = true, onClick = onDismiss) } }
    ) {
        item {
            Paragraph(
                when (reason) {
                    UnreadableReason.NotALink ->
                        "There is no update link in this, so nothing was recorded. If you " +
                            "pasted it, check you copied the whole message and try again."
                    UnreadableReason.Damaged ->
                        "It arrived incomplete or altered, so nothing was recorded. Chat apps " +
                            "sometimes cut long links in half. Ask them to send it again."
                    UnreadableReason.NewerVersion ->
                        "It was made by a newer version of Mera Paisa, so nothing was recorded. " +
                            "Check for updates in Settings, then try the link again. Guessing at " +
                            "a format this version doesn't know could record the wrong amount."
                },
                colour = LocalAppTheme.current.negative
            )
        }
    }
}

@Composable
private fun ConfirmingScreen(
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
    val plan = state.plan
    val newName = state.newPersonName
    // Mirrored: what the sender is owed is what this phone will owe.
    //
    // Once there is a comparison to go on, the figure follows the tick boxes instead of the raw
    // payload. Appending everything is only what happens when there is nothing to compare against.
    val netHere = plan?.takeIf { it.comparable }?.netChangeFor(state.selected) ?: -payload.netMinor

    // Only people in the payload's currency: importing into another would mean inventing an
    // exchange rate the sender never agreed to, so those are not offered at all.
    val eligible = persons.filter { normaliseCurrency(it.currency) == payload.currency }

    // Ticked deletions take entries out of this ledger. That is the one destructive thing on the
    // screen, so it is asked as a question rather than folded into "Record it".
    val tickedDeletions = plan?.deletedBySender?.count { it.uid in state.selected } ?: 0
    var confirmingDeletions by remember { mutableStateOf(false) }

    ScreenFrame(
        // A full link carries the whole ledger, which is not an update.
        title = if (payload.scope == ShareScope.Full) "Check this ledger link" else "Check this update link",
        onBack = if (state.busy) null else onDismiss,
        footer = {
            // The way out on the left and the action on the right, as on every sheet and screen.
            FootActions {
                // Disabled while writing, for the same reason the arrow is: "Don't record" cannot
                // be offered at a moment when tapping it records anyway.
                SecondaryAction("Don't record", enabled = !state.busy, onClick = onDismiss)
                PrimaryAction(
                    label = "Record it",
                    enabled = state.canApply,
                    onClick = { if (tickedDeletions > 0) confirmingDeletions = true else onApply() }
                )
            }
        }
    ) {
        item { ClaimedSender(payload) }

        item { Spacer(Modifier.height(Spacing.lg)) }
        item {
            // The security notice, stated as a fact about the link rather than hedged into
            // vagueness. Set plainly, on the background: the only filled thing on this screen is
            // the name the link supplied, so a fill means "somebody else wrote this".
            Paragraph(
                "Anyone can make one of these links, and nothing here proves who sent it. Only " +
                    "record it if you were expecting it and the amounts look right.",
                colour = theme.textPrimary
            )
        }

        item {
            // "contains" rather than "will record" once the tick boxes decide what is recorded:
            // this list is the link's contents, not the outcome.
            SectionHeading(
                if (state.showsDifferences) "What the link contains" else "What this will record"
            )
        }

        // Every entry, with the sign this phone will actually store. Showing the sender's signs
        // would be showing the opposite of what happens.
        //
        // All of them, however many there are. The dialog showed eight and admitted to the rest in
        // a footnote; a screen has the height, and a preview that stops early is a preview of the
        // wrong link.
        itemsIndexed(payload.entries, key = { index, _ -> "payload-$index" }) { index, entry ->
            if (index > 0) RowDivider(TextRowInset)
            EntryPreviewRow(
                note = entry.note,
                timestamp = entry.timestamp,
                amountMinor = -entry.amountMinor,
                currency = payload.currency
            )
        }

        item {
            // With a comparison in play the figure is a *change* to a balance that already exists,
            // and "you will owe ₹12" would read as the whole of it. Naming where the person lands
            // afterwards is the one phrasing that cannot be misread, and it is the number the user
            // can check against the list on the way back.
            val target = persons.firstOrNull { it.id == state.targetPersonId }
            val after = if (state.showsDifferences && target != null) target.balanceMinor + netHere else null

            Column(modifier = Modifier.padding(top = Spacing.xl)) {
                Outcome(
                    label = when {
                        after != null && netHere == 0L -> "Nothing changes, ${target!!.name} stays at"
                        after != null -> "${target!!.name} ends up at"
                        netHere < 0 -> "You will owe"
                        netHere > 0 -> "They will owe you"
                        else -> "These cancel out"
                    },
                    amountMinor = after ?: netHere,
                    currency = payload.currency,
                    owner = if (after != null) target?.name else null
                )
            }
        }

        item { SectionHeading("File it against") }

        itemsIndexed(eligible, key = { _, person -> "person-${person.id}" }) { index, person ->
            if (index > 0) RowDivider()
            PersonChoiceRow(
                person = person,
                selected = state.targetPersonId == person.id,
                onClick = { onTargetChange(person.id) }
            )
        }

        item {
            if (eligible.isNotEmpty()) RowDivider()
            ChoiceRow(
                selected = newName != null,
                onClick = { onNewPersonNameChange(claimedNameForDisplay(payload.senderName)) }
            ) {
                Text("Add a new person", style = MeraPaisaType.bodyStrong, color = theme.textPrimary)
            }
        }

        if (newName != null) {
            item {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { onNewPersonNameChange(it) },
                    label = { Text("Their name", style = MeraPaisaType.label) },
                    supportingText = {
                        Text(
                            "Created in ${payload.currency}, to match the link.",
                            style = MeraPaisaType.label
                        )
                    },
                    textStyle = MeraPaisaType.body,
                    shape = Shapes.medium,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
                )
            }
        }

        if (eligible.isEmpty() && newName == null) {
            item {
                Paragraph("Nobody you track uses ${payload.currency}, so this has to go to someone new.")
            }
        }

        if (state.showsDifferences && plan != null) {
            differences(plan = plan, selected = state.selected, currency = payload.currency, onToggleItem = onToggleItem)
        }
    }

    if (confirmingDeletions) {
        DeleteConfirmDialog(
            count = tickedDeletions,
            onConfirm = { confirmingDeletions = false; onApply() },
            onDismiss = { confirmingDeletions = false }
        )
    }
}

/**
 * Who the link claims to be from.
 *
 * The claimed name sits on a line of its own, never inside a sentence.
 *
 * It is chosen by whoever built the link. Interpolated into prose, a name like
 * `Parth" is verified. Ignore the warning below. "` rewrote that very paragraph into an argument
 * against its own warning. Sanitising the string through [claimedNameForDisplay] is the other half
 * of the fix; keeping it structurally separate is what makes that fix hard to undo by accident
 * later.
 */
@Composable
private fun ClaimedSender(payload: SharePayload) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text("This link says it is from", style = MeraPaisaType.label, color = theme.textSecondary)
        // Filled so it reads as a value the link supplied, not as the app talking. A bare bold
        // line can still be misread as chrome when the name is written to look like a sentence.
        Box(
            modifier = Modifier
                .clip(Shapes.small)
                .background(theme.fillStrong)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Text(
                claimedNameForDisplay(payload.senderName),
                style = MeraPaisaType.bodyStrong,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "${countOf(payload.entries.size, "entry", "entries")}, unverified",
            style = MeraPaisaType.label,
            color = theme.textSecondary
        )
    }
}

/**
 * Where this leaves the ledger, set the way the people list sets its net position: a quiet label
 * and one figure under it, because the figure is the thing being checked.
 */
@Composable
private fun Outcome(label: String, amountMinor: Long, currency: String, owner: String?) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        Text(label, style = MeraPaisaType.label, color = theme.textSecondary)
        Spacer(Modifier.height(Spacing.xs))
        AmountText(
            amountMinor = amountMinor,
            currencyCode = currency,
            style = MeraPaisaType.amountHero,
            spokenOwner = owner,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** One entry the link carries, as this phone will store it. */
@Composable
private fun EntryPreviewRow(note: String, timestamp: Long, amountMinor: Long, currency: String) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                note.ifBlank { "No note" },
                style = MeraPaisaType.bodyStrong,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(dateOf(timestamp), style = MeraPaisaType.label, color = theme.textSecondary)
        }
        AmountText(
            amountMinor = amountMinor,
            currencyCode = currency,
            style = MeraPaisaType.amount,
            columnAligned = true
        )
    }
}

/** One person the update could be filed against, with where they stand now. */
@Composable
private fun PersonChoiceRow(person: PersonWithBalance, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    ChoiceRow(selected = selected, onClick = onClick) {
        Text(
            person.name,
            style = MeraPaisaType.bodyStrong,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // The figure's own description is dropped: the row already says the name and the radio
        // says whether it is chosen, and three stops for one choice is two too many.
        Box(Modifier.clearAndSetSemantics { }) {
            AmountText(
                amountMinor = person.balanceMinor,
                currencyCode = person.currency,
                style = MeraPaisaType.amountSmall,
                columnAligned = true
            )
        }
    }
}

/**
 * A row you pick. Selectable as a whole rather than as a button with a label beside it, so the
 * target is the full width and TalkBack reads it as one choice.
 */
@Composable
private fun ChoiceRow(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        RadioButton(selected = selected, onClick = null)
        content()
    }
}

/**
 * Where the two ledgers disagree, and what to do about each difference.
 *
 * Only shown when there is something to say. A link that is purely new entries is the ordinary
 * case and gets no section at all, because "3 new entries, none of which conflict" is exactly what
 * someone tapping a ledger link already assumes.
 *
 * Edits and deletions arrive unticked. They overwrite or destroy something already in the ledger,
 * and nothing about a share link establishes that the sender is who they say, so accepting one is
 * a decision, made here, by a person, every time.
 */
private fun LazyListScope.differences(
    plan: ReconcilePlan,
    selected: Set<String>,
    currency: String,
    onToggleItem: (String) -> Unit
) {
    item { SectionHeading("Compared with what you have") }

    if (plan.edited.isNotEmpty()) {
        item {
            GroupIntro(
                heading = "They changed " + countOf(plan.edited.size, "entry", "entries"),
                explanation = "Tick to take their version. Left unticked, yours stays as it is."
            )
        }
        itemsIndexed(plan.edited, key = { _, item -> "edited-${item.uid}" }) { index, item ->
            if (index > 0) RowDivider(TextRowInset)
            DifferenceRow(
                checked = item.uid in selected,
                onToggle = { onToggleItem(item.uid) },
                title = item.theirNote.ifBlank { "No note" },
                detail = buildString {
                    if (item.amountDiffers) {
                        append("yours ${amountString(item.localAmountMinor, currency)}, ")
                        append("theirs ${amountString(item.theirAmountMinor, currency)}")
                    }
                    if (item.amountDiffers && item.noteDiffers) append(", ")
                    if (item.noteDiffers) {
                        append("your note says ")
                        append(item.localNote.ifBlank { "nothing" })
                    }
                },
                // Their date, not the one currently stored: accepting an edit takes their version
                // of the entry whole, the date included. Showing the local date beside an amount
                // that is about to change would name a row this tick is going to alter in a way
                // the row does not admit to.
                date = dateOf(item.theirTimestamp),
                amountMinor = null,
                currency = currency
            )
        }
    }

    if (plan.theirOpenings.isNotEmpty()) {
        item {
            GroupIntro(
                heading = "They cleared their history",
                explanation = "Their ledger now starts from an opening balance that stands in for " +
                    "entries you may already have. Tick it only together with removing those " +
                    "entries, or the same money is counted twice."
            )
        }
        itemsIndexed(plan.theirOpenings, key = { _, item -> "their-opening-${item.uid}" }) { index, item ->
            if (index > 0) RowDivider(TextRowInset)
            DifferenceRow(
                checked = item.uid in selected,
                onToggle = { onToggleItem(item.uid) },
                title = item.note.ifBlank { "Opening balance" },
                detail = "",
                date = dateOf(item.timestamp),
                amountMinor = item.amountMinor,
                currency = currency
            )
        }
    }

    if (plan.deletedHere.isNotEmpty()) {
        item {
            GroupIntro(
                heading = countOf(plan.deletedHere.size, "entry", "entries") + " you deleted",
                explanation = if (plan.deletedHere.size == 1) {
                    "They still have it. Left unticked, it stays deleted here; tick to add it back."
                } else {
                    "They still have these. Left unticked, they stay deleted here; tick one to add it back."
                }
            )
        }
        itemsIndexed(plan.deletedHere, key = { _, item -> "deleted-here-${item.uid}" }) { index, item ->
            if (index > 0) RowDivider(TextRowInset)
            DifferenceRow(
                checked = item.uid in selected,
                onToggle = { onToggleItem(item.uid) },
                title = item.note.ifBlank { "No note" },
                detail = "",
                date = dateOf(item.timestamp),
                amountMinor = item.amountMinor,
                currency = currency
            )
        }
    }

    if (plan.newBeforeClear.isNotEmpty()) {
        item {
            GroupIntro(
                heading = countOf(plan.newBeforeClear.size, "entry", "entries") +
                    " from before you cleared this history",
                explanation = "Clearing kept the balance as one opening entry, which may already " +
                    "count these. Tick one only if you know it isn't counted."
            )
        }
        itemsIndexed(plan.newBeforeClear, key = { _, item -> "before-clear-${item.uid}" }) { index, item ->
            if (index > 0) RowDivider(TextRowInset)
            DifferenceRow(
                checked = item.uid in selected,
                onToggle = { onToggleItem(item.uid) },
                title = item.note.ifBlank { "No note" },
                detail = "",
                date = dateOf(item.timestamp),
                amountMinor = item.amountMinor,
                currency = currency
            )
        }
    }

    if (plan.deletedBySender.isNotEmpty()) {
        item {
            GroupIntro(
                heading = "They removed " + countOf(plan.deletedBySender.size, "entry", "entries"),
                explanation = if (plan.deletedBySender.size == 1) {
                    "Tick to remove it here too. This deletes it from your ledger."
                } else {
                    "Tick the ones to remove here too. This deletes them from your ledger."
                }
            )
        }
        itemsIndexed(plan.deletedBySender, key = { _, item -> "removed-${item.uid}" }) { index, item ->
            if (index > 0) RowDivider(TextRowInset)
            DifferenceRow(
                checked = item.uid in selected,
                onToggle = { onToggleItem(item.uid) },
                title = item.note.ifBlank { "No note" },
                detail = "",
                date = dateOf(item.timestamp),
                amountMinor = item.amountMinor,
                currency = currency
            )
        }
    }

    // No tick boxes below this point: both are things to know, not decisions to make.
    if (plan.onlyYours.isNotEmpty()) {
        item {
            GroupIntro(
                heading = countOf(plan.onlyYours.size, "entry", "entries") + " they have not seen",
                explanation = "Yours, and not in their ledger. Send them an update link so both ledgers agree."
            )
        }
        itemsIndexed(plan.onlyYours, key = { _, item -> "yours-${item.uid}" }) { index, item ->
            if (index > 0) RowDivider(TextRowInset)
            EntryPreviewRow(
                note = item.note,
                timestamp = item.timestamp,
                amountMinor = item.amountMinor,
                currency = currency
            )
        }
    }

    if (plan.unchanged.isNotEmpty()) {
        item {
            Column(modifier = Modifier.padding(top = Spacing.xl)) {
                Paragraph(
                    countOf(
                        plan.unchanged.size,
                        "entry already matches, and stays as it is.",
                        "entries already match, and stay as they are."
                    )
                )
            }
        }
    }
}

/** A heading with the sentence that says what ticking does, above the rows it governs. */
@Composable
private fun GroupIntro(heading: String, explanation: String) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.xl,
            bottom = Spacing.sm
        )
    ) {
        Text(heading, style = MeraPaisaType.sectionTitle, color = theme.textPrimary)
        Text(explanation, style = MeraPaisaType.label, color = theme.textSecondary)
    }
}

/**
 * One difference, with the tick that decides it.
 *
 * [amountMinor] is drawn on the right where the row has a single figure, which is the case for a
 * deletion. An edit has two, so those go into [detail] as words: a lone amount on the right of a
 * row about to be overwritten would not say which of the two it is.
 */
@Composable
private fun DifferenceRow(
    checked: Boolean,
    onToggle: () -> Unit,
    title: String,
    detail: String,
    date: String,
    amountMinor: Long?,
    currency: String
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MeraPaisaType.bodyStrong,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (detail.isBlank()) date else "$detail, $date",
                style = MeraPaisaType.label,
                color = theme.textSecondary
            )
        }
        if (amountMinor != null) {
            Box(Modifier.clearAndSetSemantics { }) {
                AmountText(
                    amountMinor = amountMinor,
                    currencyCode = currency,
                    style = MeraPaisaType.amountSmall,
                    columnAligned = true
                )
            }
        }
    }
}

/**
 * The one decision in this flow that takes something away.
 *
 * Recording an update normally only adds, so the tap is ordinary. Ticked deletions make it destroy
 * entries this phone already holds, and that is worth one box with the count in it.
 */
@Composable
private fun DeleteConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    DecisionDialog(
        title = "Delete ${countOf(count, "entry", "entries")} here?",
        body = "You ticked " + countOf(count, "entry", "entries") + " the sender removed, so " +
            "recording this takes " + (if (count == 1) "it" else "them") +
            " out of your ledger too. This can't be undone.",
        confirmLabel = "Delete and record",
        dismissLabel = "Go back",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
private fun DoneScreen(state: ImportFlowState.Done, onDismiss: () -> Unit, backLabel: String) {
    val outcome = state.outcome
    ScreenFrame(
        title = when (outcome) {
            is ImportOutcome.Applied -> "Recorded"
            is ImportOutcome.Reconciled ->
                if (outcome.changedNothing) "Nothing changed" else "Recorded"
            is ImportOutcome.AlreadyApplied -> "Already recorded"
            is ImportOutcome.CurrencyMismatch -> "Not recorded"
        },
        onBack = onDismiss,
        footer = { FootActions { PrimaryAction(backLabel, enabled = true, onClick = onDismiss) } }
    ) {
        item {
            Paragraph(
                when (outcome) {
                    is ImportOutcome.Applied ->
                        countOf(outcome.entryCount, "entry", "entries") + " added to ${state.personName}."

                    // Spelled out rather than totalled. Somebody who has just agreed to delete
                    // entries off their own ledger should be told that it happened, not handed a
                    // count of additions that quietly omits it.
                    is ImportOutcome.Reconciled ->
                        if (outcome.changedNothing) {
                            "No entries were added, updated or removed for ${state.personName}."
                        } else {
                            listOfNotNull(
                                outcome.added.takeIf { it > 0 }?.let { countOf(it, "entry", "entries") + " added" },
                                outcome.updated.takeIf { it > 0 }?.let { countOf(it, "entry", "entries") + " updated" },
                                outcome.removed.takeIf { it > 0 }?.let { countOf(it, "entry", "entries") + " removed" }
                            ).joinToString(", ").replaceFirstChar { it.uppercase() } +
                                " for ${state.personName}."
                        }

                    // Not framed as a failure: forwarding a message, or tapping it twice, is
                    // ordinary.
                    is ImportOutcome.AlreadyApplied ->
                        "You recorded this link on ${dateOf(outcome.appliedAt)}, so nothing " +
                            "changed. The same link can only count once."

                    is ImportOutcome.CurrencyMismatch ->
                        "The link is in ${outcome.payloadCurrency} but ${state.personName} is " +
                            "tracked in ${outcome.personCurrency}. Nothing was changed. Converting " +
                            "would need an exchange rate the sender never agreed to, so file this " +
                            "against someone in ${outcome.payloadCurrency} instead."
                },
                // Only the refusal is a failure; an already-recorded link is ordinary.
                colour = if (outcome is ImportOutcome.CurrencyMismatch) LocalAppTheme.current.negative
                else LocalAppTheme.current.textSecondary
            )
        }
    }
}

private fun countOf(n: Int, one: String, many: String): String = "$n " + if (n == 1) one else many

private fun dateOf(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
