package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.data.Expense
import com.kg.merapaisa.data.Group
import com.kg.merapaisa.data.MemberBalance
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.Transfer

/**
 * One group: where everybody stands, what has been spent, and a way to square it up.
 * Balances are derived from the expenses below them, so the two can never disagree.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    val nameOf = { id: Long -> members.firstOrNull { it.id == id }?.name ?: "Someone" }
    val everyoneSquare = balances.all { it.amountMinor == 0L }
    // Repayments are expenses in the arithmetic but not spending, and showing them in one list
    // made a 500 dinner and a 500 repayment look identical.
    val purchases = expenses.filterNot { it.isSettlement }
    val settlements = expenses.filter { it.isSettlement }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(8.dp, 8.dp, 16.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.textPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${members.size} members • ${purchases.size} expenses",
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = onSettleUp, enabled = !everyoneSquare) { Text("Settle up") }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // The plan comes first. "Where everyone stands" tells you the state; this tells
                // you what to actually do about it, which is what anybody opens a group for.
                item {
                    Text(
                        if (everyoneSquare) "Everyone is square" else "Who pays whom",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (!everyoneSquare) {
                    items(transfers, key = { "transfer-${it.fromPersonId}-${it.toPersonId}" }) { t ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.fillStrong)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${nameOf(t.fromPersonId)} pays ${nameOf(t.toPersonId)}",
                                color = theme.textPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                amountString(t.amountMinor, group.currency),
                                color = theme.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(checked = simplifyDebts, onCheckedChange = onSimplifyChange)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fewest payments", color = theme.textPrimary, fontSize = 13.sp)
                                Text(
                                    if (simplifyDebts)
                                        "Everyone's position is netted across the group, so there are as few payments as possible."
                                    else
                                        "Each debt stays with the expense that created it — more payments, but every one traces back to something that happened.",
                                    color = theme.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Where everyone stands",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                // Balances and expenses share this LazyColumn, so they share one key space.
                // Person ids and expense ids both start at 1, so a bare id collides and Compose
                // throws. The prefix keeps the two ranges apart.
                items(balances, key = { "balance-${it.personId}" }) { b ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.fill)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(nameOf(b.personId), color = theme.textPrimary, fontSize = 14.sp)
                        Text(
                            if (b.amountMinor == 0L) "square"
                            else amountString(b.amountMinor, group.currency),
                            color = when {
                                b.amountMinor > 0 -> theme.positive
                                b.amountMinor < 0 -> theme.negative
                                else -> theme.textSecondary
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                item {
                    Text(
                        "Expenses",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                if (purchases.isEmpty()) {
                    item {
                        Text(
                            "Nothing spent yet. Tap + to add what somebody paid for.",
                            color = theme.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                items(purchases, key = { "expense-${it.id}" }) { e ->
                    var showMenu by remember(e.id) { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.fill)
                                .combinedClickable(onClick = {}, onLongClick = { showMenu = true })
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(e.description, color = theme.textPrimary, fontSize = 14.sp)
                                Text("paid by ${nameOf(e.paidByPersonId)}", color = theme.textSecondary, fontSize = 12.sp)
                            }
                            Text(
                                amountString(e.amountMinor, group.currency),
                                color = theme.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete expense", color = theme.negative) },
                                onClick = { showMenu = false; onDeleteExpense(e.id) }
                            )
                        }
                    }
                }

                // Repayments, kept apart from spending. They square positions rather than adding
                // to what the group spent, and reading them in one list with real expenses made a
                // 500 dinner and a 500 repayment indistinguishable.
                if (settlements.isNotEmpty()) {
                    item {
                        Text(
                            "Payments between members",
                            color = theme.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    items(settlements, key = { "settlement-${it.id}" }) { e ->
                        var showMenu by remember(e.id) { mutableStateOf(false) }
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.background)
                                    .border(1.dp, theme.outline, RoundedCornerShape(12.dp))
                                    .combinedClickable(onClick = {}, onLongClick = { showMenu = true })
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${nameOf(e.paidByPersonId)} paid back",
                                        color = theme.textSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    amountString(e.amountMinor, group.currency),
                                    color = theme.textSecondary,
                                    fontSize = 14.sp
                                )
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete payment", color = theme.negative) },
                                    onClick = { showMenu = false; onDeleteExpense(e.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = onAddExpense,
                modifier = Modifier.size(65.dp).background(theme.positive, RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add expense", tint = theme.background)
            }
        }
    }
}
