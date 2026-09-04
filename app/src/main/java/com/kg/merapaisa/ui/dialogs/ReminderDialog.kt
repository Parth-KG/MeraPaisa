package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.buildActivityLog
import com.kg.merapaisa.data.formatMinor
import com.kg.merapaisa.ui.MainViewModel
import com.kg.merapaisa.ui.shareText

@Composable
fun ReminderDialog(
    person: PersonWithBalance,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val transactions by viewModel.getTransactions(person.id).collectAsState(initial = emptyList())

    var message by remember(person.id) { mutableStateOf(buildReminderText(person)) }
    var includeLog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send reminder", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeLog = !includeLog }
                ) {
                    Checkbox(
                        checked = includeLog,
                        onCheckedChange = { includeLog = it }
                                )
                    Text("Include transaction history", color = theme.textSecondary, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalText = if (includeLog && transactions.isNotEmpty()) {
                    message + "\n\nTransaction history:\n" + buildActivityLog(transactions, person.currency)
                } else {
                    message
                }
                shareText(context, finalText, "Send reminder via")
                onDismiss()
            }) {
                Text("Share", color = theme.positive)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

private fun buildReminderText(person: PersonWithBalance): String {
    val absAmt = formatMinor(kotlin.math.abs(person.balanceMinor), person.currency)
    return when {
        person.balanceMinor > 0 ->
            "Hey ${person.name}, friendly reminder — you owe me $absAmt."
        person.balanceMinor < 0 ->
            "Hey ${person.name}, friendly reminder — I owe you $absAmt."
        else ->
            "Hey ${person.name}, we're all settled up — thanks!"
    }
}
