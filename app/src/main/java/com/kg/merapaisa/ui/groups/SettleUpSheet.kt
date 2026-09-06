package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.MemberBalance
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.Transfer
import com.kg.merapaisa.data.formatMinor
import com.kg.merapaisa.data.settleUp

/**
 * The fewest payments that square the group up. Balances that pass through a member — A owes
 * B, B owes C — collapse, so nobody hands money over just to hand it straight on.
 */
@Composable
fun SettleUpSheet(
    balances: List<MemberBalance>,
    members: List<Person>,
    currency: String,
    onRecord: (Transfer) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val transfers = settleUp(balances)
    val nameOf = { id: Long -> members.firstOrNull { it.id == id }?.name ?: "Someone" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settle up", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            if (transfers.isEmpty()) {
                Text("Everyone is square — nothing to pay.", color = theme.textSecondary, fontSize = 14.sp)
            } else {
                Column {
                    Text(
                        if (transfers.size == 1) "One payment settles the group:"
                        else "${transfers.size} payments settle the group:",
                        color = theme.textSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transfers) { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.fill)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${nameOf(t.fromPersonId)} pays ${nameOf(t.toPersonId)}",
                                        color = theme.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        formatMinor(t.amountMinor, currency),
                                        color = theme.positive,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(onClick = { onRecord(t) }) { Text("Record") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
