package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.currencySymbol
import com.kg.merapaisa.data.formatMinor
import com.kg.merapaisa.data.evenShares
import com.kg.merapaisa.data.parseAmountToMinor

@Composable
fun AddExpenseDialog(
    members: List<Person>,
    currency: String,
    selfId: Long,
    onDismiss: () -> Unit,
    onAdd: (description: String, amountMinor: Long, paidByPersonId: Long, sharedWith: List<Long>) -> Unit
) {
    val theme = LocalAppTheme.current
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    // selfId is filled asynchronously and starts at 0, which is nobody. Keying on it stops the
    // field latching onto that placeholder and writing an expense whose payer does not exist.
    var paidBy by remember(selfId, members) {
        mutableStateOf(members.firstOrNull { it.id == selfId }?.id ?: members.firstOrNull()?.id ?: 0L)
    }
    var sharedWith by remember(members) { mutableStateOf(members.map { it.id }.toSet()) }

    val amountMinor = parseAmountToMinor(amount)
    val valid = description.isNotBlank() && amountMinor != null && amountMinor > 0 &&
        sharedWith.isNotEmpty() && members.any { it.id == paidBy }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("What for?") },
                    placeholder = { Text("Hotel, dinner, cab...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount in ${currencySymbol(currency)}") },
                    singleLine = true,
                    isError = amount.isNotEmpty() && amountMinor == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Paid by", color = theme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                    items(members, key = { it.id }) { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { paidBy = m.id }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = paidBy == m.id, onClick = { paidBy = m.id })
                            Text(m.name, color = theme.textPrimary, fontSize = 14.sp)
                        }
                    }
                }

                Text(
                    "Split between ${sharedWith.size} of ${members.size}",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                    items(members, key = { it.id }) { m ->
                        val isIn = m.id in sharedWith
                        val share = if (isIn && amountMinor != null) {
                            evenShares(amountMinor, sharedWith.toList().sorted())[m.id]
                        } else null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { sharedWith = if (isIn) sharedWith - m.id else sharedWith + m.id }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isIn, onCheckedChange = {
                                sharedWith = if (isIn) sharedWith - m.id else sharedWith + m.id
                            })
                            Text(m.name, color = theme.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (share != null) {
                                Text(formatMinor(share, currency), color = theme.textSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(description.trim(), amountMinor!!, paidBy, sharedWith.toList().sorted()) },
                enabled = valid
            ) { Text("Add", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textSecondary) } }
    )
}
