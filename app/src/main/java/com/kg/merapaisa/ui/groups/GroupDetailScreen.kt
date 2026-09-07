package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.background
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
import com.kg.merapaisa.data.Expense
import com.kg.merapaisa.data.Group
import com.kg.merapaisa.data.MemberBalance
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.formatMinor
import com.kg.merapaisa.data.formatSignedAmount

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
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onSettleUp: () -> Unit,
    onDeleteExpense: (Long) -> Unit
) {
    val theme = LocalAppTheme.current
    val nameOf = { id: Long -> members.firstOrNull { it.id == id }?.name ?: "Someone" }
    val everyoneSquare = balances.all { it.amountMinor == 0L }

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
                        "${members.size} members • ${expenses.size} expenses",
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
                item {
                    Text(
                        if (everyoneSquare) "Everyone is square" else "Where everyone stands",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
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
                            else formatSignedAmount(b.amountMinor, group.currency),
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
                if (expenses.isEmpty()) {
                    item {
                        Text(
                            "Nothing spent yet. Tap + to add what somebody paid for.",
                            color = theme.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                items(expenses, key = { "expense-${it.id}" }) { e ->
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
                                formatMinor(e.amountMinor, group.currency),
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
