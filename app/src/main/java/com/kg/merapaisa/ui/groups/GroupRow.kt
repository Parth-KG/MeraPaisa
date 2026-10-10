package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.GroupSummary
import com.kg.merapaisa.ui.format.AmountText
import com.kg.merapaisa.ui.format.amountSpoken
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.format.columnShowsFraction

/**
 * One group in the list: a row on the background, the same line a person gets.
 *
 * It was a filled 16dp tile with a transparent border, which is the card kit the people list was
 * taken off: every item the same shape, so a group and a person read as different species. The
 * position was drawn as a literal em dash whenever it was zero, which left the one state worth
 * reaching with no figure against it at all. It is an amount at zero now, with "even" written
 * beside it, because that is what the rest of the app says when nothing is owed.
 */
@Composable
fun GroupRow(
    summary: GroupSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    /** Whether another group in the list shows paise. From [columnShowsFraction], by the list. */
    reserveFraction: Boolean = false
) {
    val theme = LocalAppTheme.current
    var showMenu by remember { mutableStateOf(false) }
    val balance = summary.yourBalanceMinor

    val members = if (summary.memberCount == 1) "1 member" else "${summary.memberCount} members"
    val standing = when {
        balance > 0 -> "owed to you"
        balance < 0 -> "you owe"
        else -> "even"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Named, so TalkBack says what a long press does.
            .combinedClickable(onClick = onClick, onLongClickLabel = "More options", onLongClick = { showMenu = true })
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            // One description for the whole row, so TalkBack reads "Goa trip, 6 members, owed to
            // you, 1,200 rupees" instead of stopping three times on one line. Merged rather than
            // cleared, so the name stays on the node that carries it.
            .semantics(mergeDescendants = true) {
                contentDescription = "${summary.group.name}, $members, " +
                    amountSpoken(balance, summary.group.currency)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                summary.group.name,
                style = MeraPaisaType.bodyStrong,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("$members, $standing", style = MeraPaisaType.label, color = theme.textSecondary)
        }

        // The menu hangs off the amount because it is the only other thing on the row: an empty
        // Box would give the popup no position to open from.
        // The figure's own description is dropped in favour of the row's, which names the group
        // the position belongs to.
        Box(Modifier.clearAndSetSemantics { }) {
            AmountText(
                amountMinor = balance,
                currencyCode = summary.group.currency,
                style = MeraPaisaType.amount,
                reserveFraction = reserveFraction
            )
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = Shapes.medium
            ) {
                DropdownMenuItem(
                    text = { Text("Delete group", style = MeraPaisaType.body, color = theme.textPrimary) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}
