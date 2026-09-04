package com.kg.merapaisa.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.currencySymbol
import com.kg.merapaisa.data.isUsableAmount

@Composable
fun NumPad(person: PersonWithBalance, input: String, onKey: (String) -> Unit,onSettleToggle: () -> Unit, onAdd: () -> Unit, onSubtract: () -> Unit, note: String, onNoteChange: (String) -> Unit, showNote: Boolean, onToggleNote: () -> Unit){
    val theme = LocalAppTheme.current
    val amountIsUsable = isUsableAmount(input)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.card)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(20.dp, 16.dp, 20.dp, 16.dp)
    ) {
        // Display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(theme.fillStrong)
                .padding(16.dp, 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(person.name, color = theme.textSecondary, fontSize = 16.sp)
            Text(
                "${currencySymbol(person.currency)}${if (input.isNotEmpty()) input else "0"}",
                color = theme.textPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium
            )
        }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Add description", color = theme.textSecondary, fontSize = 13.sp)
        Switch(
            checked = showNote,
            onCheckedChange = { onToggleNote() }
        )
    }

    if (showNote) {
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            placeholder = { Text("e.g. dinner, cab fare...", color = theme.textSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

        // Keys
        val keys = listOf("1","2","3","4","5","6","7","8","9",".","0","⌫")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Button(
                            onClick = { onKey(k) },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (k == "⌫") lerp(theme.card, theme.negative, 0.18f) else theme.fillStrong,
                                contentColor = if (k == "⌫") theme.negative else theme.textPrimary
                            )
                        ) {
                            Text(k, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // +/- buttons
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onSubtract,
                enabled = amountIsUsable,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.negative.copy(alpha = 0.15f),
                    contentColor = theme.negative,
                    disabledContainerColor = theme.negative.copy(alpha = 0.05f),
                    disabledContentColor = theme.negative.copy(alpha = 0.4f)
                )
            ) { Text("−", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = onAdd,
                enabled = amountIsUsable,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.positive.copy(alpha = 0.15f),
                    contentColor = theme.positive,
                    disabledContainerColor = theme.positive.copy(alpha = 0.05f),
                    disabledContentColor = theme.positive.copy(alpha = 0.4f)
                )
            ) { Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Settling files a person away and closes their balance; reopening brings them back.
        // A person already at zero can still be settled, which is how you file someone away.
        Button(
            onClick = onSettleToggle,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.textSecondary.copy(alpha = 0.15f),
                contentColor = theme.textPrimary
            )
        ) {
            Text(
                if (person.isSettled) "Reopen" else "Settle up",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
