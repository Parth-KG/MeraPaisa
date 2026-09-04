package com.kg.merapaisa.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kg.merapaisa.AppTheme
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.currencySymbol
import com.kg.merapaisa.data.formatMinor
import com.kg.merapaisa.data.formatMinorPlain
import com.kg.merapaisa.data.parseAmountToMinor
import com.kg.merapaisa.ui.MainViewModel

data class SplitParticipant(
    val id: Long,
    val name: String,
    val currency: String
)

private fun equalSplit(amountMinor: Long, participants: List<SplitParticipant>): Map<Long, Long> {
    if (participants.isEmpty()) return emptyMap()
    val base = amountMinor / participants.size
    val remainder = amountMinor - (base * participants.size)
    return participants.mapIndexed { i, p ->
        p.id to if (i == 0) base + remainder else base
    }.toMap()
}

private fun redistribute(
    current: Map<Long, Long>,
    locked: Set<Long>,
    changedId: Long,
    newValue: Long,
    total: Long
): Map<Long, Long> {
    val updated = current.toMutableMap()
    updated[changedId] = newValue

    val lockedSum = updated.filterKeys { it in locked }.values.sum()
    val unlockedIds = updated.keys.filter { it !in locked }
    if (unlockedIds.isEmpty()) return updated

    val remaining = total - lockedSum
    val perUnlocked = remaining / unlockedIds.size
    // Integer division leaves a few minor units over; give them to the first row so the
    // parts still add up to the whole rather than tripping the totals-mismatch warning.
    val leftover = remaining - perUnlocked * unlockedIds.size

    unlockedIds.forEachIndexed { i, id ->
        val share = if (i == 0) perUnlocked + leftover else perUnlocked
        updated[id] = kotlin.math.max(0L, share)
    }
    return updated
}

@Composable
fun SplitAdjustmentsScreen(
    viewModel: MainViewModel,
    amountMinor: Long,
    sourceCurrency: String,
    selectedPersons: List<PersonWithBalance>,
    includeMe: Boolean,
    note: String,
    onNoteChange: (String) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (Map<Long, Long>) -> Unit
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()

    // Build participant list. "You" is represented by id = -1L (won't conflict with any DB id).
    val youId = -1L
    val participants = remember(selectedPersons, includeMe) {
        val list = mutableListOf<SplitParticipant>()
        if (includeMe) {
            list.add(SplitParticipant(id = youId, name = "You", currency = sourceCurrency))
        }
        selectedPersons.forEach {
            list.add(SplitParticipant(id = it.id, name = it.name, currency = it.currency))
        }
        list
    }

    // Per-person amounts in source currency (we convert at confirm time only for display)
    var amountsInSource by remember(participants, amountMinor) {
        mutableStateOf(equalSplit(amountMinor, participants))
    }
    var lockedIds by remember(participants) { mutableStateOf(setOf<Long>()) }

    // Converted amounts (in each person's own currency) — recalculated when amountsInSource changes
    var convertedAmounts by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var conversionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(amountsInSource, participants) {
        conversionError = null
        val result = mutableMapOf<Long, Long>()
        for (p in participants) {
            val srcAmt = amountsInSource[p.id] ?: 0L
            if (p.currency == sourceCurrency) {
                result[p.id] = srcAmt
            } else {
                val converted = viewModel.convertCurrency(srcAmt, sourceCurrency, p.currency)
                if (converted == null) {
                    conversionError = "Couldn't convert to ${p.currency} for ${p.name}. Check internet or remove this person."
                    result[p.id] = srcAmt   // fallback, but warning is shown
                } else {
                    result[p.id] = converted
                }
            }
        }
        convertedAmounts = result
    }

    val total = amountsInSource.values.sum()
    val totalsMatch = total == amountMinor

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(theme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 48.dp, 16.dp, 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.textPrimary)
                    }
                    Text("Adjust split", color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = theme.textPrimary)
                    }
                }

                // Total amount header
                Text(
                    "${formatMinor(amountMinor, sourceCurrency)} total",
                    color = theme.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                // Optional note
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    placeholder = { Text("Description (optional)", color = theme.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(20.dp, 12.dp, 20.dp, 8.dp)
                )

                // Per-person rows
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(participants, key = { it.id }) { p ->
                        SplitAdjustmentRow(
                            participant = p,
                            sourceCurrency = sourceCurrency,
                            amountInSourceMinor = amountsInSource[p.id] ?: 0L,
                            convertedAmountMinor = convertedAmounts[p.id],
                            locked = p.id in lockedIds,
                            onAmountChange = { newAmt ->
                                amountsInSource = redistribute(
                                    current = amountsInSource,
                                    locked = lockedIds + p.id,   // editing locks this row
                                    changedId = p.id,
                                    newValue = newAmt,
                                    total = amountMinor
                                )
                                lockedIds = lockedIds + p.id
                            },
                            onToggleLock = {
                                lockedIds = if (p.id in lockedIds) lockedIds - p.id else lockedIds + p.id
                            },
                            theme = theme
                        )
                    }
                }

                // Total + warning
                Column(modifier = Modifier.padding(20.dp, 8.dp, 20.dp, 0.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total", color = theme.textSecondary, fontSize = 14.sp)
                        Text(
                            "${formatMinor(total, sourceCurrency)} of ${formatMinor(amountMinor, sourceCurrency)}",
                            color = if (totalsMatch) theme.textPrimary else theme.negative,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!totalsMatch) {
                        Text(
                            "Warning: totals don't match.",
                            color = theme.negative,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (conversionError != null) {
                        Text(
                            conversionError!!,
                            color = theme.negative,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Confirm
                Button(
                    onClick = {
                        // Build final map, excluding "You"
                        val finalMap = convertedAmounts
                            .filterKeys { it != youId }
                        onConfirm(finalMap)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 8.dp, 20.dp, 24.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = conversionError == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.positive,
                        contentColor = theme.background,
                        disabledContainerColor = theme.positive.copy(alpha = 0.3f),
                        disabledContentColor = theme.background.copy(alpha = 0.5f)
                    )
                ) {
                    Text("Confirm split", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SplitAdjustmentRow(
    participant: SplitParticipant,
    sourceCurrency: String,
    amountInSourceMinor: Long,
    convertedAmountMinor: Long?,
    locked: Boolean,
    onAmountChange: (Long) -> Unit,
    onToggleLock: () -> Unit,
    theme: AppTheme
) {
    var text by remember(amountInSourceMinor) { mutableStateOf(formatMinorPlain(amountInSourceMinor, sourceCurrency)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.fill)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(participant.name, color = theme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (participant.currency != sourceCurrency && convertedAmountMinor != null) {
                Text(
                    "= ${formatMinor(convertedAmountMinor, participant.currency)}",
                    color = theme.textSecondary,
                    fontSize = 11.sp
                )
            }
        }

        BasicTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                parseAmountToMinor(newText)?.let { onAmountChange(it) }
            },
            textStyle = LocalTextStyle.current.copy(
                color = theme.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            cursorBrush = SolidColor(theme.positive),
            modifier = Modifier.width(80.dp).padding(end = 4.dp)
        )

        Text(currencySymbol(sourceCurrency), color = theme.textSecondary, fontSize = 12.sp)

        IconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (locked) "Unlock" else "Lock",
                tint = if (locked) theme.positive else theme.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
