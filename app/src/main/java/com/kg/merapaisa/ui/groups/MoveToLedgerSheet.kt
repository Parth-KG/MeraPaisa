package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.LIST_STACK_FONT_SCALE
import com.kg.merapaisa.ui.LabelAndAmount
import com.kg.merapaisa.ui.MoveFlowState
import com.kg.merapaisa.ui.MoveLineState
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.SecondaryAction
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.format.AmountText
import com.kg.merapaisa.ui.format.amountSpoken
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.listTitleMaxLines
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * What moving your part of a group to your ledger will write, before anything is (v3.7.0): every
 * person you have a pair with, which way it runs, and the entry their ledger gets, at today's rate
 * for anyone kept in another currency. The move waits until every rate is in, as a split does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToLedgerSheet(
    groupName: String,
    groupCurrency: String,
    flow: MoveFlowState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onMove: () -> Unit
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scroll = rememberScrollState()
    val note = "From ${groupName.trim()}"

    SheetFrame(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scroll)
        ) {
            Text(
                "Move to your ledger",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Each person below gets one entry in your ledger, noted \"$note\", and your part of " +
                    "this group comes to even. What the others owe each other stays here.",
                style = MeraPaisaType.body,
                color = theme.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            if (flow.groupChanged) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Something in the group changed while this was open, so these figures were worked out again.",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }
            Spacer(Modifier.height(Spacing.md))

            flow.lines.forEachIndexed { index, line ->
                if (index > 0) RowDivider()
                MoveLineRow(line, groupCurrency, converting = flow.converting)
            }

            if (flow.rateMissing) {
                val missing = flow.lines.filter { it.ledgerAmountMinor == null }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "Couldn't get today's rate for ${missing.map { it.currency }.distinct().joinToString(" or ")}, " +
                        "so ${missing.joinToString(" and ") { it.name }}'s entry can't be worked out. " +
                        "Nothing moves until it can. Try again once you're connected.",
                    style = MeraPaisaType.body,
                    color = theme.negative,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.padding(horizontal = Spacing.sm).heightIn(min = 48.dp)
                ) {
                    Text("Try again", style = MeraPaisaType.bodyStrong, color = theme.primary)
                }
            }
        }

        SheetFoot(above = scroll) {
            SecondaryAction("Don't move", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
            PrimaryAction(
                "Move to ledger",
                enabled = flow.ready,
                busyLabel = if (flow.busy) "Moving" else null,
                onClick = onMove
            )
        }
    }
}

/**
 * One person: their name and which way the pair runs, the figure in the group's currency, and,
 * when their ledger is kept in another currency, what it is saved as there.
 */
@Composable
private fun MoveLineRow(line: MoveLineState, groupCurrency: String, converting: Boolean) {
    val theme = LocalAppTheme.current
    val standing = if (line.groupAmountMinor > 0) "owes you" else "you owe"
    val foreign = line.currency != com.kg.merapaisa.data.normaliseCurrency(groupCurrency)
    val savedAs = when {
        !foreign -> null
        line.ledgerAmountMinor != null -> "Saved as ${amountString(kotlin.math.abs(line.ledgerAmountMinor), line.currency)}"
        converting -> "Getting today's rate"
        else -> null
    }
    LabelAndAmount(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    "${line.name}, " + amountSpoken(line.groupAmountMinor, groupCurrency),
                    savedAs
                ).joinToString(", ")
            },
        stackFromFontScale = LIST_STACK_FONT_SCALE,
        label = {
            Column {
                Text(
                    line.name,
                    style = MeraPaisaType.bodyStrong,
                    color = theme.textPrimary,
                    maxLines = listTitleMaxLines(),
                    overflow = TextOverflow.Ellipsis
                )
                Text(standing, style = MeraPaisaType.label, color = theme.textSecondary)
                if (savedAs != null) Text(savedAs, style = MeraPaisaType.label, color = theme.textSecondary)
            }
        },
        amount = {
            androidx.compose.foundation.layout.Box(Modifier.clearAndSetSemantics { }) {
                AmountText(amountMinor = line.groupAmountMinor, currencyCode = groupCurrency, style = MeraPaisaType.amount)
            }
        }
    )
}
