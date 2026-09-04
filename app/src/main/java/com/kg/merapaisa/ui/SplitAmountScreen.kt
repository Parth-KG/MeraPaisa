package com.kg.merapaisa.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.parseAmountToMinor

@Composable
fun SplitAmountScreen(
    amount: String,
    onAmountChange: (String) -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit
) {
    val theme = LocalAppTheme.current

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Top bar with cancel + title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 48.dp, 16.dp, 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = theme.textPrimary)
                    }
                    Text(
                        "Split amount",
                        color = theme.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.size(48.dp)) // balances the close button
                }

                Spacer(modifier = Modifier.weight(1f))

                // Amount display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (amount.isNotEmpty()) amount else "0",
                        color = theme.textPrimary,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Light
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Numpad keys
                val keys = listOf("1","2","3","4","5","6","7","8","9",".","0","⌫")
                Column(
                    modifier = Modifier.padding(20.dp, 0.dp, 20.dp, 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keys.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { k ->
                                Button(
                                    onClick = {
                                        onAmountChange(
                                            when {
                                                k == "⌫" -> amount.dropLast(1)
                                                k == "." && amount.contains(".") -> amount
                                                k == "." && amount.isEmpty() -> "0."
                                                else -> amount + k
                                            }
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(60.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (k == "⌫") lerp(theme.card, theme.negative, 0.18f) else theme.fillStrong,
                                        contentColor = if (k == "⌫") theme.negative else theme.textPrimary
                                    )
                                ) {
                                    Text(k, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // Next button
                val amtValue = parseAmountToMinor(amount) ?: 0L
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 8.dp, 20.dp, 24.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = amtValue > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.positive,
                        contentColor = theme.background,
                        disabledContainerColor = theme.positive.copy(alpha = 0.3f),
                        disabledContentColor = theme.background.copy(alpha = 0.5f)
                    )
                ) {
                    Text("Next", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
