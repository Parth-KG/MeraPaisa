package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.data.currencySymbol
import com.kg.merapaisa.data.formatMinorPlain
import com.kg.merapaisa.data.parseAmountToMinor

/**
 * Corrects or removes a single entry. Before this, a mistyped amount could only be papered
 * over with a compensating entry, leaving the mistake in the history for good.
 */
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    currency: String,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    // A leading minus is how you flip which way this entry runs.
    var amount by remember(transaction.id) {
        mutableStateOf(formatMinorPlain(transaction.amountMinor, currency))
    }
    var note by remember(transaction.id) { mutableStateOf(transaction.note) }

    val amountMinor = parseAmountToMinor(amount)
    val isValid = amountMinor != null && amountMinor != 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount in ${currencySymbol(currency)}") },
                    supportingText = {
                        Text(
                            if (isValid) {
                                if (amountMinor!! > 0) "They owe you this" else "You owe them this"
                            } else {
                                "Enter an amount, with a leading − to reverse it"
                            },
                            color = if (isValid) theme.textSecondary else theme.negative,
                            fontSize = 12.sp
                        )
                    },
                    isError = !isValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    placeholder = { Text("e.g. dinner, cab fare...") },
                    singleLine = true
                )
                TextButton(onClick = { onDelete(transaction) }) {
                    Text("Delete this entry", color = theme.negative)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(transaction.copy(amountMinor = amountMinor!!, note = note)) },
                enabled = isValid
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textSecondary) }
        }
    )
}
