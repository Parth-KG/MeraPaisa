package com.kg.merapaisa.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.CurrencySides
import com.kg.merapaisa.data.CurrencyTotal
import com.kg.merapaisa.ui.format.AmountText
import com.kg.merapaisa.ui.format.amountSpoken
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Motion
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.format.columnShowsFraction

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
 *
 * A tap anywhere on it switches between the net and both sides: what you are owed and what you
 * owe, kept apart, per currency. The label does not name the view; a small swap mark at the end of
 * the label line says the total can be turned over. With everyone even there is nothing to turn,
 * so there is no mark and no tap.
 */
@Composable
fun NetPosition(
    totals: List<CurrencyTotal>,
    modifier: Modifier = Modifier,
    sides: List<CurrencySides> = emptyList(),
    bothSides: Boolean = false,
    onToggle: (() -> Unit)? = null
) {
    val theme = LocalAppTheme.current
    // Everyone even, not merely a net of zero: owed ₹100 and owing ₹100 nets to nothing, but the
    // two sides still have something to show.
    val canTurn = onToggle != null && sides.isNotEmpty()
    val showSides = canTurn && bothSides
    val label = when {
        showSides || totals.size != 1 -> "Overall"
        totals.single().amountMinor > 0 -> "Owed to you"
        else -> "You owe"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (canTurn) {
                    // One stop for TalkBack: the label and every figure, which view it is, and
                    // what a tap will show instead.
                    Modifier
                        .clickable(
                            onClickLabel = if (showSides) "Show the net" else "Show both sides",
                            onClick = onToggle!!
                        )
                        .semantics(mergeDescendants = true) {
                            stateDescription = if (showSides) "Both sides" else "Net"
                        }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = Spacing.lg)
            // The whole total is the target for turning it over, never less than a fingertip.
            .then(if (canTurn) Modifier.heightIn(min = 48.dp) else Modifier)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crossfade(targetState = label, animationSpec = tween(Motion.medium), label = "label", modifier = Modifier.weight(1f)) {
                Text(it, style = MeraPaisaType.label, color = theme.textSecondary)
            }
            if (canTurn) {
                // As tall as the label's line, so it grows with the type beside it. A fixed 16dp
                // drew a glyph of about 13 that stayed that size while the words doubled.
                val markSize = with(LocalDensity.current) { MeraPaisaType.label.lineHeight.toDp() }
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = null,
                    tint = theme.textSecondary,
                    modifier = Modifier.size(markSize)
                )
            }
        }
        Crossfade(targetState = showSides, animationSpec = tween(Motion.medium), label = "figures") { sidesShown ->
            Column {
                if (sidesShown) BothSides(sides) else Net(totals)
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        DoubleRule()
    }
}

/** The net, as it has always read: a hero figure for one currency, a line each for several. */
@Composable
private fun Net(totals: List<CurrencyTotal>) {
    val theme = LocalAppTheme.current
    when {
        totals.isEmpty() -> {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "You're even with everyone",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary
            )
        }

        totals.size == 1 -> {
            val total = totals.single()
            Spacer(Modifier.height(Spacing.xs))
            AmountText(
                amountMinor = total.amountMinor,
                currencyCode = total.currency,
                style = MeraPaisaType.amountHero,
                modifier = Modifier.fillMaxWidth()
            )
        }

        else -> {
            Spacer(Modifier.height(Spacing.sm))
            val reserve = columnShowsFraction(totals.map { it.amountMinor to it.currency })
            totals.forEach { total ->
                TotalLine(
                    words = if (total.amountMinor > 0) "owed to you" else "you owe",
                    amountMinor = total.amountMinor,
                    currency = total.currency,
                    reserveFraction = reserve
                )
            }
        }
    }
}

/** What you are owed and what you owe, a line each per currency, leaving out a side at zero. */
@Composable
private fun BothSides(sides: List<CurrencySides>) {
    Spacer(Modifier.height(Spacing.sm))
    val reserve = columnShowsFraction(
        sides.flatMap { listOf(it.owedToYouMinor to it.currency, it.youOweMinor to it.currency) }
    )
    sides.forEach { side ->
        if (side.owedToYouMinor != 0L) {
            TotalLine("owed to you", side.owedToYouMinor, side.currency, reserveFraction = reserve)
        }
        if (side.youOweMinor != 0L) {
            TotalLine("you owe", side.youOweMinor, side.currency, reserveFraction = reserve)
        }
    }
}

@Composable
private fun TotalLine(words: String, amountMinor: Long, currency: String, reserveFraction: Boolean) {
    val theme = LocalAppTheme.current
    LabelAndAmount(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .clearAndSetSemantics {
                contentDescription = amountSpoken(amountMinor, currency)
            },
        keepLabelOnOneLine = true,
        // Every line stacks from the same scale as the lists, together, rather than one line at a
        // time as each ran out of room: a long figure stacked while a short one beside it did not.
        stackFromFontScale = LIST_STACK_FONT_SCALE,
        label = {
            Text(words, style = MeraPaisaType.body, color = theme.textSecondary)
        },
        // Aligned as a column, so several currencies line their decimal points up.
        amount = {
            AmountText(
                amountMinor = amountMinor,
                currencyCode = currency,
                style = MeraPaisaType.amount,
                reserveFraction = reserveFraction
            )
        }
    )
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
