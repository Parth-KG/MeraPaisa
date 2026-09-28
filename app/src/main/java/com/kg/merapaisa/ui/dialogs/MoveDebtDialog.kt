package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.formatMinor
import com.kg.merapaisa.data.normaliseCurrency
import com.kg.merapaisa.ui.MoveDebtFlowState

/**
 * Moving part of what one person owes onto somebody else.
 *
 * "Rondu owes you ₹624 — move ₹100 of that to Sasti." Two entries, equal and opposite, so the
 * total owed to you does not change; only who owes it.
 *
 * Only people in the same currency are offered. Moving ₹100 onto a dollar balance would silently
 * claim $100, and converting would mean inventing a rate nobody agreed to — the same reasoning
 * that makes an incoming share link in the wrong currency refuse rather than convert.
 */
@Composable
fun MoveDebtDialog(
    state: MoveDebtFlowState,
    people: List<PersonWithBalance>,
    onKey: (String) -> Unit,
    onTargetChange: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val eligible = people.filter {
        it.id != state.fromPersonId && normaliseCurrency(it.currency) == state.currency
    }
    val typed = state.amountMinor

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = { Text("Move a debt", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "${state.fromName} owes you ${formatMinor(state.availableMinor, state.currency)}. " +
                        "Move part of that onto somebody else.",
                    fontSize = 13.sp,
                    color = theme.textSecondary
                )

                OutlinedTextField(
                    value = state.amount,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Amount") },
                    placeholder = { Text("0") },
                    prefix = { Text(formatMinor(0, state.currency).filterNot { it.isDigit() }) },
                    isError = typed != null && typed > state.availableMinor,
                    supportingText = {
                        if (typed != null && typed > state.availableMinor) {
                            Text(
                                "More than ${state.fromName} owes you.",
                                fontSize = 11.sp,
                                color = theme.negative
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // The same keypad the rest of the app enters money with, so the rules about
                // decimal points and leading zeros are the ones people already know.
                AmountKeypad(onKey = onKey)

                HorizontalDivider(color = theme.outline)

                Text("Move it to", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)

                if (eligible.isEmpty()) {
                    Text(
                        "Nobody else is tracked in ${state.currency}, so there is nowhere for this " +
                            "to go. A debt can only move between people in the same currency.",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                }

                eligible.forEach { person ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onTargetChange(person.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = state.toPersonId == person.id,
                            onClick = { onTargetChange(person.id) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(person.name, fontSize = 13.sp, color = theme.textPrimary)
                            Text(
                                "now at ${formatMinor(person.balanceMinor, person.currency)}",
                                fontSize = 11.sp,
                                color = theme.textSecondary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNoteChange,
                    label = { Text("Why (optional)") },
                    placeholder = { Text("Sasti covered it") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                state.problem?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.fillStrong)
                            .padding(10.dp)
                    ) {
                        Text(it, fontSize = 12.sp, color = theme.negative)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.canMove) {
                Text("Move", color = theme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textSecondary) }
        }
    )
}

/** A compact numeric keypad, matching how amounts are entered everywhere else in the app. */
@Composable
private fun AmountKeypad(onKey: (String) -> Unit) {
    val theme = LocalAppTheme.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            // fillStrong, not fill: `fill` is defined as `card`, which is exactly
                            // the dialog's own container colour — the keys rendered as bare text
                            // with no visible shape at all.
                            .background(theme.fillStrong)
                            .clickable { onKey(key) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(key, color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
