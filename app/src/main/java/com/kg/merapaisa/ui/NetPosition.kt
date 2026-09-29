package com.kg.merapaisa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.CurrencyTotal
import com.kg.merapaisa.ui.format.AmountText
import com.kg.merapaisa.ui.format.amountSpoken
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Spacing

/**
 * Where you stand overall, set the way a ledger closes a page.
 *
 * It was a filled rounded tile of exactly the shape and colour of the person rows beneath it, so
 * the summary of the list looked like one more item in it. It now sits on the background and is
 * closed with a double rule, which is the one piece of ornament this design allows itself: in a
 * hand-kept book, two lines under a figure mean the column is totalled and the total is final.
 *
 * One line per currency, always. Adding them would need a rate, and a converted sum is a number
 * nobody owes.
 */
@Composable
fun NetPosition(totals: List<CurrencyTotal>, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        when {
            totals.isEmpty() -> {
                Text("Overall", style = MeraPaisaType.label, color = theme.textSecondary)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "You're even with everyone",
                    style = MeraPaisaType.screenTitle,
                    color = theme.textPrimary
                )
            }

            totals.size == 1 -> {
                val total = totals.single()
                Text(
                    if (total.amountMinor > 0) "Owed to you" else "You owe",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary
                )
                Spacer(Modifier.height(Spacing.xs))
                AmountText(
                    amountMinor = total.amountMinor,
                    currencyCode = total.currency,
                    style = MeraPaisaType.amountHero,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            else -> {
                Text("Overall", style = MeraPaisaType.label, color = theme.textSecondary)
                Spacer(Modifier.height(Spacing.sm))
                totals.forEach { total ->
                    LabelAndAmount(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs)
                            .clearAndSetSemantics {
                                contentDescription = amountSpoken(total.amountMinor, total.currency)
                            },
                        keepLabelOnOneLine = true,
                        label = {
                            Text(
                                if (total.amountMinor > 0) "owed to you" else "you owe",
                                style = MeraPaisaType.body,
                                color = theme.textSecondary
                            )
                        },
                        // Aligned as a column, so several currencies line their decimal points up.
                        amount = {
                            AmountText(
                                amountMinor = total.amountMinor,
                                currencyCode = total.currency,
                                style = MeraPaisaType.amount,
                                columnAligned = true
                            )
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        DoubleRule()
    }
}

/**
 * Two hairlines with a thread of background between them.
 *
 * The accountant's mark for a closed total. Drawn rather than taken from HorizontalDivider so the
 * gap between the lines stays exactly one hairline at any density: at the sizes involved, letting
 * a component decide its own padding is the difference between a double rule and a thick line.
 */
@Composable
private fun DoubleRule() {
    val theme = LocalAppTheme.current
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.textSecondary))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.textSecondary))
    }
}
