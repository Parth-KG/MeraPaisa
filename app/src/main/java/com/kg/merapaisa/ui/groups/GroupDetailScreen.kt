package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Expense
import com.kg.merapaisa.data.Group
import com.kg.merapaisa.data.MemberBalance
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.Transfer
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.TextRowInset
import com.kg.merapaisa.ui.format.AmountText
import com.kg.merapaisa.ui.format.SignStyle
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.coversLedger
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import com.kg.merapaisa.ui.DecisionDialog
import com.kg.merapaisa.ui.format.amountString

/**
 * One group: where everybody stands, what has been spent, and a way to square it up.
 * Balances are derived from the expenses below them, so the two can never disagree.
 *
 * Everything in the list is a row on the background now, divided by hairlines, where it used to be
 * a stack of identical filled tiles with a gap between each one. The two actions are named and sit
 * together at the foot of the screen: adding an expense was a 65dp tile with a plus in it,
 * floating over the list, and the list carried 100dp of invisible padding to keep its last row out
 * from under it.
 */
@Composable
fun GroupDetailScreen(
    group: Group,
    members: List<Person>,
    expenses: List<Expense>,
    balances: List<MemberBalance>,
    transfers: List<Transfer>,
    simplifyDebts: Boolean,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onSettleUp: () -> Unit,
    onDeleteExpense: (Long) -> Unit,
    onSimplifyChange: (Boolean) -> Unit
) {
    val theme = LocalAppTheme.current
    val names = remember(members) { MemberNames(members) }
    val everyoneEven = balances.all { it.amountMinor == 0L }
    // Repayments are expenses in the arithmetic but not spending, and showing them in one list
    // made a 500 dinner and a 500 repayment look identical.
    val purchases = expenses.filterNot { it.isSettlement }
    val settlements = expenses.filter { it.isSettlement }

    val memberCount = if (members.size == 1) "1 member" else "${members.size} members"
    val expenseCount = if (purchases.size == 1) "1 expense" else "${purchases.size} expenses"

    Column(modifier = Modifier.fillMaxSize().background(theme.background).coversLedger()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.textPrimary
                )
            }
        }

        // The name gets the full width under the back button rather than a slot between two
        // controls, so a group called "Manali, October" is read rather than ellipsised. The count
        // line was "6 members • 5 expenses": a middle dot is the one piece of punctuation nobody
        // says out loud, and a comma does the same work.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = Spacing.lg)
        ) {
            Text(
                group.name,
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text("$memberCount, $expenseCount", style = MeraPaisaType.label, color = theme.textSecondary)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(bottom = Spacing.lg)
        ) {
            // The plan comes first. "Where everyone stands" tells you the state; this tells
            // you what to actually do about it, which is what anybody opens a group for.
            item {
                SectionHeading(if (everyoneEven) "Everyone is even" else "Who pays whom")
            }

            if (!everyoneEven) {
                itemsIndexed(
                    transfers,
                    key = { _, t -> "transfer-${t.fromPersonId}-${t.toPersonId}" }
                ) { index, t ->
                    if (index > 0) RowDivider(TextRowInset)
                    TransferRow(
                        line = names.pays(t.fromPersonId, t.toPersonId),
                        amountMinor = t.amountMinor,
                        currency = group.currency
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fewest payments", style = MeraPaisaType.bodyStrong, color = theme.textPrimary)
                            Text(
                                if (simplifyDebts)
                                    "No one gets money just to pass it on, so there are as few payments as possible."
                                else
                                    "Each debt stays with the expense that created it: more payments, but every one traces back to something that happened.",
                                style = MeraPaisaType.label,
                                color = theme.textSecondary
                            )
                        }
                        Switch(checked = simplifyDebts, onCheckedChange = onSimplifyChange)
                    }
                }
            }

            item { SectionHeading("Where everyone stands") }
            // Balances and expenses share this LazyColumn, so they share one key space.
            // Person ids and expense ids both start at 1, so a bare id collides and Compose
            // throws. The prefix keeps the two ranges apart.
            itemsIndexed(balances, key = { _, b -> "balance-${b.personId}" }) { index, b ->
                if (index > 0) RowDivider(TextRowInset)
                MemberBalanceRow(
                    name = names.subject(b.personId),
                    isYou = names.isYou(b.personId),
                    amountMinor = b.amountMinor,
                    currency = group.currency
                )
            }

            item { SectionHeading("Expenses") }
            if (purchases.isEmpty()) {
                item {
                    Text(
                        "Nothing spent yet.",
                        style = MeraPaisaType.body,
                        color = theme.textSecondary,
                        modifier = Modifier.padding(horizontal = Spacing.lg)
                    )
                }
            }
            itemsIndexed(purchases, key = { _, e -> "expense-${e.id}" }) { index, e ->
                if (index > 0) RowDivider(TextRowInset)
                EntryRow(
                    title = e.description,
                    subtitle = "paid by ${names.objectOf(e.paidByPersonId)}",
                    amountMinor = e.amountMinor,
                    currency = group.currency,
                    deleteLabel = "Delete expense",
                    onDelete = { onDeleteExpense(e.id) }
                )
            }

            // Repayments, kept apart from spending. They square positions rather than adding
            // to what the group spent, and reading them in one list with real expenses made a
            // 500 dinner and a 500 repayment indistinguishable.
            if (settlements.isNotEmpty()) {
                item { SectionHeading("Payments between members") }
                itemsIndexed(settlements, key = { _, e -> "settlement-${e.id}" }) { index, e ->
                    if (index > 0) RowDivider(TextRowInset)
                    EntryRow(
                        title = "${names.subject(e.paidByPersonId)} paid back",
                        subtitle = null,
                        amountMinor = e.amountMinor,
                        currency = group.currency,
                        deleteLabel = "Delete payment",
                        onDelete = { onDeleteExpense(e.id) },
                        quiet = true
                    )
                }
            }
        }

        // Both actions named, side by side, the way the people list carries its own two. Settling
        // up is the second one because it is what you reach for once, at the end of a trip.
        Row(
            modifier = Modifier.height(IntrinsicSize.Min)
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddExpense,
                shape = Shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.background
                )
            ) {
                Text("Add an expense", style = MeraPaisaType.action)
            }
            OutlinedButton(
                onClick = onSettleUp,
                enabled = !everyoneEven,
                shape = Shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
                border = BorderStroke(1.dp, theme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textPrimary)
            ) {
                Text("Settle up", style = MeraPaisaType.action)
            }
        }
    }
}

/** A heading inside the list. Sentence case, quiet, with air above it and none below. */
@Composable
private fun SectionHeading(text: String) {
    val theme = LocalAppTheme.current
    Text(
        text,
        style = MeraPaisaType.sectionTitle,
        color = theme.textSecondary,
        modifier = Modifier.padding(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.xl,
            bottom = Spacing.sm
        )
    )
}

/**
 * One member's position in the group.
 *
 * Zero used to read "square", which is a word this app does not use and is not a figure either.
 * It is an amount at zero now, with "even" written beside it, so the row has the same shape
 * whatever the number says.
 */
@Composable
private fun MemberBalanceRow(name: String, isYou: Boolean, amountMinor: Long, currency: String) {
    val theme = LocalAppTheme.current
    // Your own row is "You", so it takes "are owed" and "owe" rather than "is owed" and "owes".
    val standing = when {
        amountMinor > 0 -> if (isYou) "are owed" else "is owed"
        amountMinor < 0 -> if (isYou) "owe" else "owes"
        else -> "even"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            // Merged into one stop for TalkBack, with the words written here rather than taken
            // from the figure: `amountSpoken` can only say owed to you or owed by you, and a
            // member's position in a group is owed to the member.
            .semantics(mergeDescendants = true) {
                contentDescription = if (amountMinor == 0L) "$name, even"
                else "$name $standing ${spokenFigure(amountMinor, currency)}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MeraPaisaType.bodyStrong,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(standing, style = MeraPaisaType.label, color = theme.textSecondary)
        }
        Box(Modifier.clearAndSetSemantics { }) {
            AmountText(
                amountMinor = amountMinor,
                currencyCode = currency,
                style = MeraPaisaType.amount,
                columnAligned = true
            )
        }
    }
}

/**
 * One line of the group's history: something bought, or somebody paying somebody back.
 *
 * An expense has no direction, so the figure carries no sign and no ink. A repayment is set in the
 * quiet colour instead of being boxed in an outline, because what separates it from spending is
 * that it is not news.
 */
@Composable
private fun EntryRow(
    title: String,
    subtitle: String?,
    amountMinor: Long,
    currency: String,
    deleteLabel: String,
    onDelete: () -> Unit,
    quiet: Boolean = false
) {
    val theme = LocalAppTheme.current
    var showMenu by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val ink = if (quiet) theme.textSecondary else theme.textPrimary

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (showMenu) theme.fillStrong else Color.Transparent)
                // A tap opens the menu too. It did nothing, so TalkBack offered a tap that went
                // nowhere, and deleting an expense could only be reached by a long press.
                .combinedClickable(
                    onClickLabel = "Options",
                    onClick = { showMenu = true },
                    onLongClickLabel = "Options",
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(title, subtitle)
                        .joinToString(", ") + ", " + spokenFigure(amountMinor, currency)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MeraPaisaType.bodyStrong,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(subtitle, style = MeraPaisaType.label, color = theme.textSecondary)
                }
            }
            Box(Modifier.clearAndSetSemantics { }) {
                AmountText(
                    amountMinor = amountMinor,
                    currencyCode = currency,
                    style = MeraPaisaType.amount,
                    signStyle = SignStyle.None,
                    colourByDirection = false,
                    columnAligned = true
                )
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = Shapes.medium
        ) {
            DropdownMenuItem(
                text = { Text(deleteLabel, style = MeraPaisaType.body, color = theme.textPrimary) },
                onClick = { showMenu = false; confirming = true }
            )
        }
    }

    // Asked, like deleting a person or a group. It went straight through from the menu, and an
    // expense or a payment is part of what every member owes: one mis-tap moved several balances
    // with no way back.
    if (confirming) {
        val figure = amountString(amountMinor, currency, SignStyle.None)
        DecisionDialog(
            title = if (quiet) "Delete this payment?" else "Delete $title?",
            body = (if (quiet) "Removes this $figure payment" else "Removes this $figure expense") +
                " from the group, and what its members owe each other changes to match. " +
                "This can't be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Keep it",
            onConfirm = { confirming = false; onDelete() },
            onDismiss = { confirming = false }
        )
    }
}
