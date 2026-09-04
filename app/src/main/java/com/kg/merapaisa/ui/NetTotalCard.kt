package com.kg.merapaisa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.CurrencyTotal
import com.kg.merapaisa.data.formatSignedAmount

/**
 * Where you stand overall. One currency gets the headline treatment; several get a line each,
 * because adding them together would need a rate and would be a number nobody owes.
 */
@Composable
fun NetTotalCard(totals: List<CurrencyTotal>, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.fill)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        when {
            totals.isEmpty() -> {
                Text(
                    "All settled",
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Nothing outstanding",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textSecondary
                )
            }

            totals.size == 1 -> {
                val total = totals.single()
                Text(
                    if (total.amountMinor > 0) "Net owed to you" else "Net you owe",
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    formatSignedAmount(total.amountMinor, total.currency),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (total.amountMinor > 0) theme.positive else theme.negative
                )
            }

            else -> {
                Text(
                    "Overall",
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                totals.forEach { total ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (total.amountMinor > 0) "owed to you" else "you owe",
                            fontSize = 13.sp,
                            color = theme.textSecondary
                        )
                        Text(
                            formatSignedAmount(total.amountMinor, total.currency),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (total.amountMinor > 0) theme.positive else theme.negative
                        )
                    }
                }
            }
        }
    }
}
