package com.kg.merapaisa.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.formatSignedAmount

@Composable
fun PersonRow(person: PersonWithBalance, isSelected: Boolean, onHistoryClick: () -> Unit, onClick: () -> Unit,onSendReminder: () -> Unit, onDelete: () -> Unit, onEditClick: () -> Unit) {
    val theme = LocalAppTheme.current
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) theme.fillStrong else theme.fill)
            .border(1.dp, if (isSelected) theme.outline else Color.Transparent, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { showMenu = true }
            )
            .padding(12.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PfpView(person = person.person, size = 44)
            Column {
                Text(person.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                Text(
                    if (person.balanceMinor > 0) "owes you" else if (person.balanceMinor < 0) "you owe" else "settled",
                    fontSize = 12.sp, color = theme.textSecondary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                formatSignedAmount(person.balanceMinor, person.currency),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (person.balanceMinor > 0) theme.positive else if (person.balanceMinor < 0) theme.negative else theme.textSecondary
            )
            // The menu anchors to the history button rather than to an empty, zero-size Box.
            Box {
                IconButton(onClick = onHistoryClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Info, contentDescription = "History", tint = theme.textSecondary, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Send reminder", color = theme.textPrimary) },
                        onClick = { showMenu = false; onSendReminder() }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit", color = theme.textPrimary) },
                        onClick = { showMenu = false; onEditClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = theme.negative) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}
