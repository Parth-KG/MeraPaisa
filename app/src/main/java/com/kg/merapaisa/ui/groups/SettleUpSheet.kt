package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.Transfer
import com.kg.merapaisa.data.normaliseCurrency
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.TextRowInset
import com.kg.merapaisa.ui.format.AmountText
import com.kg.merapaisa.ui.format.SignStyle
import com.kg.merapaisa.ui.format.amountParts
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues

/**
 * The payments that make everyone in the group even, worked out the way the group is set to: the
 * fewest payments, or each debt kept with the expense that created it.
 *
 * It used to work out its own plan, always the fewest payments, whatever the group was set to. With
 * that setting off, the group screen listed one set of payments and this sheet offered to record a
 * different set. The plan now comes in from the same place the group screen gets it.
 *
 * It was called a sheet and built as an AlertDialog, which put a scrolling list of payments inside
 * a box sized for a question. A dialog is for a decision; a list you work down one line at a time
 * belongs on a sheet that can take the height, so this is one now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpSheet(
    transfers: List<Transfer>,
    members: List<Person>,
    currency: String,
    onRecord: (Transfer) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val names = remember(members) { MemberNames(members) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = Shapes.sheet,
        containerColor = theme.surface,
        contentColor = theme.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.outline) }
    ) {
        Text(
            "Settle up",
            style = MeraPaisaType.screenTitle,
            color = theme.textPrimary,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            when {
                transfers.isEmpty() -> "Everyone is even, so there is nothing to pay."
                transfers.size == 1 -> "One payment makes everyone even."
                else -> "${transfers.size} payments make everyone even."
            },
            style = MeraPaisaType.body,
            color = theme.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
        Spacer(Modifier.height(Spacing.lg))

        // fill = false so a group with two payments makes a short sheet rather than a tall one
        // with an empty half.
        // A recorded payment stays on screen until the plan comes back without it, and a second
        // tap in that gap wrote the payment twice, flipping the debt the other way. Reset whenever
        // the plan changes.
        var recorded by remember(transfers) { mutableStateOf(emptySet<String>()) }
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            itemsIndexed(
                transfers,
                key = { _, t -> "transfer-${t.fromPersonId}-${t.toPersonId}" }
            ) { index, t ->
                if (index > 0) RowDivider(TextRowInset)
                TransferRow(
                    line = names.pays(t.fromPersonId, t.toPersonId),
                    amountMinor = t.amountMinor,
                    currency = currency
                ) {
                    // Recording leaves the sheet open on purpose: the plan shrinks as each payment
                    // is written down, and closing after the first one would hide the rest.
                    // No padding on the trailing side, so the label ends where the figure above
                    // it ends rather than a button's inset short of it.
                    TextButton(
                        onClick = {
                            val key = "${t.fromPersonId}-${t.toPersonId}"
                            if (key !in recorded) { recorded = recorded + key; onRecord(t) }
                        },
                        enabled = "${t.fromPersonId}-${t.toPersonId}" !in recorded,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(start = Spacing.md, end = 0.dp)
                    ) {
                        Text("Record payment", style = MeraPaisaType.action)
                    }
                }
            }
        }

        // The way out, outlined and full width like the foot of every other sheet. Recording
        // happens on each line above, so there is no second button here to pair it with.
        OutlinedButton(
            onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
            shape = Shapes.small,
            border = BorderStroke(1.dp, theme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
                .heightIn(min = 52.dp)
        ) {
            Text("Back to the group", style = MeraPaisaType.action)
        }
    }
}

/**
 * One line of the settle-up plan, shared by this sheet and the group screen so the plan reads the
 * same wherever you meet it.
 *
 * The sentence carries the direction, so the figure is drawn with no sign and no ink of its own: a
 * payment between two members is neither owed to you nor owed by you, and colouring it either way
 * would state something untrue. It stays column aligned because these amounts sit under each other.
 *
 * The line and the figure are merged into one description rather than cleared into one, so the row
 * is a single stop for TalkBack while the words it is made of stay where they were written.
 */
@Composable
internal fun TransferRow(
    line: String,
    amountMinor: Long,
    currency: String,
    action: (@Composable () -> Unit)? = null
) {
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "$line, ${spokenFigure(amountMinor, currency)}"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                line,
                style = MeraPaisaType.body,
                color = theme.textPrimary,
                modifier = Modifier.weight(1f)
            )
            // The figure's own description is dropped: it can only say whether an amount is owed
            // to you or by you, and this one is owed between two other people.
            Box(Modifier.clearAndSetSemantics { }) {
                AmountText(
                    amountMinor = amountMinor,
                    currencyCode = currency,
                    style = MeraPaisaType.amount,
                    signStyle = SignStyle.None,
                    colourByDirection = false,
                    columnAligned = true
                )
            }
        }
        // The button sits under the pair rather than beside it: "Chaitanya pays Bilal" and a
        // lakh figure already fill the row, and squeezing a third thing in ellipsised the names.
        if (action != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { action() }
        }
    }
}

/**
 * A figure in words, for TalkBack.
 *
 * `amountSpoken` says an amount from your side of the ledger, which is the wrong side inside a
 * group: a position here belongs to a member and a payment runs between two of them, neither of
 * whom is necessarily you. The code stands in for the symbol because "₹" and U+2212 have no
 * spoken name, and a group has one currency, so naming it once per line is enough.
 */
internal fun spokenFigure(amountMinor: Long, currency: String): String =
    "${amountParts(amountMinor, currency, SignStyle.None).digits} ${normaliseCurrency(currency)}"

/**
 * How a sentence in a group names a member.
 *
 * You are a member of every group, as the row called "You", so a plain name in a sentence produced
 * "xyz pays You", "You is owed ₹2,000" and "paid by You". These put "you" where grammar wants it:
 * "You pay Asha", "Asha pays you", "paid by you".
 */
internal class MemberNames(private val members: List<Person>) {
    private fun member(id: Long) = members.firstOrNull { it.id == id }

    fun isYou(id: Long): Boolean = member(id)?.isSelf == true

    /** At the start of a sentence. */
    fun subject(id: Long): String = if (isYou(id)) "You" else member(id)?.name ?: "Someone"

    /** Anywhere after the start. */
    fun objectOf(id: Long): String = if (isYou(id)) "you" else member(id)?.name ?: "someone"

    fun pays(from: Long, to: Long): String =
        if (isYou(from)) "You pay ${objectOf(to)}" else "${subject(from)} pays ${objectOf(to)}"
}
