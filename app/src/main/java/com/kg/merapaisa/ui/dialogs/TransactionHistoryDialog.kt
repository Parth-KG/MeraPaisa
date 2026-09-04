package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.data.formatMinor
import com.kg.merapaisa.ui.MainViewModel
import androidx.compose.foundation.clickable

@Composable
fun TransactionHistoryDialog(person: PersonWithBalance, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val transactions by viewModel.getTransactions(person.id).collectAsState(initial = emptyList())
    val theme = LocalAppTheme.current
    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingRollback by remember { mutableStateOf<Transaction?>(null) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${person.name}'s History",
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (transactions.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Clear log", color = theme.negative, fontSize = 13.sp)
                    }
                }
            }
        },
        text = {
            if (transactions.isEmpty()) {
                Text("No transactions yet.", color = theme.textSecondary, fontSize = 14.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(transactions) { t ->
                        val date = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
                            .format(java.util.Date(t.timestamp))
                        // Tapping an entry opens it for correction or removal.
                        Column(modifier = Modifier.clickable { editingTransaction = t }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(date, color = theme.textSecondary, fontSize = 12.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        formatMinor(t.amountMinor, person.currency),
                                        color = if (t.amountMinor > 0) theme.positive else theme.negative,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(
                                        onClick = { pendingRollback = t },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Undo,
                                            contentDescription = "Rollback",
                                            tint = theme.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            if (t.note.isNotBlank()) {
                                Text(
                                    t.note,
                                    color = theme.textSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                                )
                            }
                            HorizontalDivider(color = theme.outline)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = theme.positive) }
        }
    )

    editingTransaction?.let { target ->
        EditTransactionDialog(
            transaction = target,
            currency = person.currency,
            onSave = {
                viewModel.editTransaction(it)
                editingTransaction = null
            },
            onDelete = {
                viewModel.deleteTransaction(it)
                editingTransaction = null
            },
            onDismiss = { editingTransaction = null }
        )
    }

    pendingRollback?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRollback = null },
                title = { Text("Rollback?", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will undo this transaction and all newer ones. The history entries will be kept.",
                    color = theme.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rollbackToTransaction(person, target)
                    pendingRollback = null
                }) { Text("Rollback", color = theme.negative) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRollback = null }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
                title = { Text("Clear log?", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This deletes all transaction history for ${person.name}. The current balance won't change. This can't be undone.",
                    color = theme.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearTransactionsForPerson(person.id)
                    showClearConfirm = false
                }) {
                    Text("Clear", color = theme.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }
}
