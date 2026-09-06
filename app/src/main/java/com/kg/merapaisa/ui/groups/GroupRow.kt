package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.kg.merapaisa.data.GroupSummary
import com.kg.merapaisa.data.formatSignedAmount

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GroupRow(summary: GroupSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    val theme = LocalAppTheme.current
    var showMenu by remember { mutableStateOf(false) }
    val balance = summary.yourBalanceMinor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.fill)
            .border(1.dp, Color.Transparent, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(summary.group.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
            Text(
                buildString {
                    append(if (summary.memberCount == 1) "1 member" else "${summary.memberCount} members")
                    append(" • ")
                    append(
                        when {
                            balance > 0 -> "you are owed"
                            balance < 0 -> "you owe"
                            else -> "all square"
                        }
                    )
                },
                fontSize = 12.sp,
                color = theme.textSecondary
            )
        }

        Box {
            Text(
                if (balance == 0L) "—" else formatSignedAmount(balance, summary.group.currency),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    balance > 0 -> theme.positive
                    balance < 0 -> theme.negative
                    else -> theme.textSecondary
                }
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Delete group", color = theme.negative) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}
