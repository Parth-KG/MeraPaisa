package com.kg.merapaisa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.ui.format.AmountText
import com.kg.merapaisa.ui.format.amountSpoken
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing

/**
 * One person in the list: a row on the background, not a tile.
 *
 * It used to be a filled, bordered, 16dp-rounded card, stacked with gaps, which is the card-kit
 * look: every item the same shape, so nothing reads as more or less important than anything else.
 * A ledger is a list of lines, and lines are what this is now. Selection is a wash of fill rather
 * than a change of shape, so the row does not jump when you tap it.
 *
 * The amount is given a floor and the name a ceiling, because neither can be allowed to push the
 * other off the row. With the real fixtures, "Chaitanya Venkataraman" and ₹12,34,567.50 collided
 * and shoved the history button off the screen entirely.
 */
@Composable
fun PersonRow(
    person: PersonWithBalance,
    isSelected: Boolean,
    onHistoryClick: () -> Unit,
    onClick: () -> Unit,
    onSendReminder: () -> Unit,
    onDelete: () -> Unit,
    onEditClick: () -> Unit,
    onSettleToggle: () -> Unit,
    onShareSummary: () -> Unit,
    onShareLedger: () -> Unit,
    onMoveDebt: () -> Unit
) {
    val theme = LocalAppTheme.current
    var showMenu by remember { mutableStateOf(false) }

    val direction = when {
        person.balanceMinor > 0 -> "owes you"
        person.balanceMinor < 0 -> "you owe"
        else -> "even"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) theme.fillStrong else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            // One description for the whole row, so TalkBack reads "Asha owes you 1,200 rupees"
            // rather than spelling out a name, a label and a figure as three separate stops.
            .clearAndSetSemantics {
                contentDescription = amountSpoken(person.balanceMinor, person.currency, person.name)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        PfpView(person = person.person, size = 40)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                person.name,
                style = MeraPaisaType.bodyStrong,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(direction, style = MeraPaisaType.label, color = theme.textSecondary)
        }

        // The amount never shrinks to make room for a long name: it is the thing being read.
        AmountText(
            amountMinor = person.balanceMinor,
            currencyCode = person.currency,
            style = MeraPaisaType.amount,
            columnAligned = true,
            spokenOwner = person.name
        )

        // The menu anchors to the history button rather than to an empty, zero-size Box.
        Box {
            IconButton(
                onClick = onHistoryClick,
                modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = "History for ${person.name}",
                    tint = theme.textSecondary
                )
            }
            PersonMenu(
                person = person,
                expanded = showMenu,
                onDismiss = { showMenu = false },
                onSettleToggle = onSettleToggle,
                onSendReminder = onSendReminder,
                onShareSummary = onShareSummary,
                onShareLedger = onShareLedger,
                onMoveDebt = onMoveDebt,
                onEditClick = onEditClick,
                onDelete = onDelete
            )
        }
    }
}

/** Everything you can do to one person. Pulled out so the row itself stays readable. */
@Composable
private fun PersonMenu(
    person: PersonWithBalance,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSettleToggle: () -> Unit,
    onSendReminder: () -> Unit,
    onShareSummary: () -> Unit,
    onShareLedger: () -> Unit,
    onMoveDebt: () -> Unit,
    onEditClick: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalAppTheme.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = Shapes.medium) {
        MenuRow(if (person.isSettled) "Reopen" else "Settle up", theme.textPrimary) { onDismiss(); onSettleToggle() }
        MenuRow("Send a reminder", theme.textPrimary) { onDismiss(); onSendReminder() }
        MenuRow("Share a summary", theme.textPrimary) { onDismiss(); onShareSummary() }
        // Distinct from a summary: that sends text a human reads, this sends a link their app
        // records, so both ledgers end up agreeing.
        MenuRow("Send an update link", theme.textPrimary) { onDismiss(); onShareLedger() }
        // Only when they owe you something. There is nothing to hand on otherwise, and an
        // always-visible item that always refuses is worse than no item.
        if (person.balanceMinor > 0) {
            MenuRow("Move this debt", theme.textPrimary) { onDismiss(); onMoveDebt() }
        }
        MenuRow("Edit", theme.textPrimary) { onDismiss(); onEditClick() }
        MenuRow("Delete", theme.negative) { onDismiss(); onDelete() }
    }
}

@Composable
private fun MenuRow(label: String, colour: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MeraPaisaType.body, color = colour) },
        onClick = onClick
    )
}
