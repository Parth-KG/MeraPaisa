package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.SUPPORTED_CURRENCIES
import com.kg.merapaisa.data.currencySymbol

/**
 * You are always in your own groups, so there is no checkbox for yourself — only for the
 * people you are sharing costs with.
 */
@Composable
fun CreateGroupDialog(
    people: List<PersonWithBalance>,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, currency: String, memberIds: List<Long>) -> Unit
) {
    val theme = LocalAppTheme.current
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Goa trip, Flat 402...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Currency", color = theme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUPPORTED_CURRENCIES.forEach { code ->
                        FilterChip(
                            selected = currency == code,
                            onClick = { currency = code },
                            label = { Text(currencySymbol(code), fontSize = 14.sp) }
                        )
                    }
                }

                Text(
                    if (selected.isEmpty()) "Who else is in it?" else "${selected.size} selected, plus you",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                if (people.isEmpty()) {
                    Text(
                        "Add some people first — a group needs somebody to split with.",
                        color = theme.textSecondary,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(people, key = { it.id }) { person ->
                            val isIn = person.id in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selected = if (isIn) selected - person.id else selected + person.id
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isIn, onCheckedChange = {
                                    selected = if (isIn) selected - person.id else selected + person.id
                                })
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(person.name, color = theme.textPrimary, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.trim(), currency, selected.toList()) },
                enabled = name.isNotBlank() && selected.isNotEmpty()
            ) { Text("Create", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textSecondary) }
        }
    )
}
