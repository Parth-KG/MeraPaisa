package com.kg.merapaisa.ui.share

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.formatMinor
import com.kg.merapaisa.ui.ShareFlowState

/**
 * The outgoing half of the two-sided ledger: what this link will tell the other phone, shown before
 * it goes anywhere.
 *
 * The name field is not decoration. The self row ships called "You", and a link whose sender is
 * "You" gives the recipient nothing to match against — so this asks once and remembers.
 */
@Composable
fun ShareLedgerSheet(
    state: ShareFlowState,
    onSenderNameChange: (String) -> Unit,
    onFullHistoryChange: (Boolean) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = {
            Text(
                "Share with ${state.personName}",
                color = theme.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                Text(
                    "They open the link and their app records the other side of these entries, so " +
                        "both ledgers agree.",
                    fontSize = 13.sp,
                    color = theme.textSecondary
                )

                OutlinedTextField(
                    value = state.senderName,
                    onValueChange = onSenderNameChange,
                    label = { Text("Your name") },
                    supportingText = {
                        Text(
                            "How you appear on their phone, so they know who the link is from.",
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.hasNothingToSend) {
                    // The common confusing case, named plainly rather than left as a dead button.
                    Text(
                        if (state.fullHistory) {
                            "There are no entries for ${state.personName} yet, so there is nothing to send."
                        } else {
                            "Nothing new since you last shared with ${state.personName}. " +
                                "Turn on \"send everything\" below to re-send their full history."
                        },
                        fontSize = 13.sp,
                        color = theme.textSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${state.entryCount} ${if (state.entryCount == 1) "entry" else "entries"}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.textPrimary
                        )
                        Text(
                            netSentence(state.netMinor, state.currency, state.personName),
                            fontSize = 13.sp,
                            color = when {
                                state.netMinor > 0 -> theme.positive
                                state.netMinor < 0 -> theme.negative
                                else -> theme.textSecondary
                            }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(checked = state.fullHistory, onCheckedChange = onFullHistoryChange)
                    Column {
                        Text("Send everything", fontSize = 13.sp, color = theme.textPrimary)
                        Text(
                            "Use this if a link never arrived. Their app ignores anything it has " +
                                "already applied.",
                            fontSize = 11.sp,
                            color = theme.textSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onShare, enabled = !state.busy && !state.hasNothingToSend) {
                Text("Share", color = theme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

/**
 * Says what the link means for the recipient, since they are the one who has to act on it. Phrasing
 * it from the sender's side would put the debt the wrong way round in the one place it is read
 * aloud.
 */
private fun netSentence(netMinor: Long, currency: String, personName: String): String = when {
    netMinor > 0 -> "$personName will record owing you ${formatMinor(netMinor, currency)}"
    netMinor < 0 -> "$personName will record you owing them ${formatMinor(-netMinor, currency)}"
    else -> "These cancel out — nothing will be outstanding either way"
}
