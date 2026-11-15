package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.currencySymbol
import com.kg.merapaisa.data.evenShares
import com.kg.merapaisa.ui.mismatchMessage
import com.kg.merapaisa.ui.SplitParticipant
import com.kg.merapaisa.ui.SplitShareRow
import com.kg.merapaisa.data.redistribute
import androidx.compose.material3.TextButton
import com.kg.merapaisa.data.formatMinorPlain
import com.kg.merapaisa.data.isTypableAmount
import com.kg.merapaisa.data.parseAmountToMinor
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.format.PlainAmountText
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.SecondaryAction
import kotlinx.coroutines.launch

/**
 * What somebody paid for, who paid, and who it is split between.
 *
 * This was an AlertDialog with a whole form inside it, and inside that two lazy lists: one of radio
 * buttons and one of checkboxes, each capped at a height that showed three members of six. A dialog
 * is for a decision, so a form of four fields goes on a sheet, where the members are plain rows
 * that scroll with everything else rather than two little windows scrolling inside a third.
 */
/** An expense as it stands, to open the sheet on for editing (v3.7.0). */
data class ExpenseDraft(
    val description: String,
    val amountMinor: Long,
    val paidByPersonId: Long,
    /** The stored shares, by person, which add up to [amountMinor]. */
    val shares: Map<Long, Long>
)

/**
 * The shares a sheet would save: the stored ones while the amount and the people sharing are as
 * they were when an edit opened (so an edit never quietly resets shares to equal), otherwise an
 * equal split, the remainder to the earliest member.
 */
internal fun sharesToSave(amountMinor: Long, sharedWith: Set<Long>, editing: ExpenseDraft?): Map<Long, Long> =
    if (editing != null && editing.amountMinor == amountMinor && editing.shares.keys == sharedWith) {
        editing.shares
    } else {
        evenShares(amountMinor, sharedWith.toList().sorted())
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    members: List<Person>,
    currency: String,
    selfId: Long,
    onDismiss: () -> Unit,
    onSave: (description: String, amountMinor: Long, paidByPersonId: Long, sharesByPerson: Map<Long, Long>) -> Unit,
    /** The expense being edited, or null to add one. */
    editing: ExpenseDraft? = null
) {
    val theme = LocalAppTheme.current
    var description by remember { mutableStateOf(editing?.description.orEmpty()) }
    var amount by remember {
        mutableStateOf(editing?.let { formatMinorPlain(it.amountMinor, currency).removeSuffix(".00") }.orEmpty())
    }
    // selfId is filled asynchronously and starts at 0, which is nobody. Keying on it stops the
    // field latching onto that placeholder and writing an expense whose payer does not exist.
    var paidBy by remember(selfId, members) {
        mutableLongStateOf(
            editing?.paidByPersonId?.takeIf { id -> members.any { it.id == id } }
                ?: members.firstOrNull { it.id == selfId }?.id ?: members.firstOrNull()?.id ?: 0L
        )
    }
    var sharedWith by remember(members) {
        mutableStateOf(editing?.shares?.keys?.filter { id -> members.any { it.id == id } }?.toSet()
            ?: members.map { it.id }.toSet())
    }

    var adding by remember { mutableStateOf(false) }
    val amountMinor = parseAmountToMinor(amount)
    // The stored shares count until "Split equally" is chosen, which means equal from then on.
    var keepStored by remember { mutableStateOf(true) }
    val shares = if (amountMinor != null && amountMinor > 0 && sharedWith.isNotEmpty()) {
        sharesToSave(amountMinor, sharedWith, editing.takeIf { keepStored })
    } else emptyMap()

    // "Adjust the shares" (v3.7.0): equal unless asked, as on Balances. An expense saved with
    // unequal shares opens adjusted, with those shares in the fields. A new amount or a change in
    // who shares it starts the fields again from the shares above.
    var adjusting by remember {
        mutableStateOf(editing != null && editing.shares != evenShares(editing.amountMinor, editing.shares.keys.sorted()))
    }
    var custom by remember(amountMinor, sharedWith) { mutableStateOf(shares) }
    var locked by remember(amountMinor, sharedWith) { mutableStateOf(setOf<Long>()) }
    var lastEdited by remember(amountMinor, sharedWith) { mutableStateOf<Long?>(null) }
    val customAddsUp = amountMinor != null && custom.values.sum() == amountMinor && custom.keys == sharedWith

    val valid = description.isNotBlank() && amountMinor != null && amountMinor > 0 &&
        sharedWith.isNotEmpty() && members.any { it.id == paidBy } && (!adjusting || customAddsUp)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val scroll = rememberScrollState()

    SheetFrame(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scroll)
        ) {
            Text(
                if (editing == null) "Add an expense" else "Edit expense",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.lg))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("What for?") },
                placeholder = { Text("Hotel, dinner, cab") },
                singleLine = true,
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = amount,
                onValueChange = { if (isTypableAmount(it, currency)) amount = it },
                label = { Text("Amount in ${currencySymbol(currency)}") },
                singleLine = true,
                isError = amount.isNotEmpty() && amountMinor == null,
                supportingText = if (amount.isNotEmpty() && amountMinor == null) {
                    { Text("That is not an amount this app can read. Digits and one decimal point.") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )

            FormHeading("Paid by")
            members.forEachIndexed { index, m ->
                if (index > 0) RowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = paidBy == m.id,
                            onClick = { paidBy = m.id },
                            role = Role.RadioButton
                        )
                        .heightIn(min = 48.dp)
                        .padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // The control is not separately clickable: the row already carries the click
                    // and the role, so TalkBack hears one member rather than a button and a name.
                    RadioButton(selected = paidBy == m.id, onClick = null)
                    Text(
                        m.name,
                        style = MeraPaisaType.body,
                        color = theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            FormHeading("Split between ${sharedWith.size} of ${members.size}")
            members.forEachIndexed { index, m ->
                if (index > 0) RowDivider()
                val isIn = m.id in sharedWith
                // While adjusting, the figures are in the fields below instead.
                val share = if (isIn && !adjusting) shares[m.id] else null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = isIn,
                            onValueChange = { sharedWith = if (isIn) sharedWith - m.id else sharedWith + m.id },
                            role = Role.Checkbox
                        )
                        .heightIn(min = 48.dp)
                        .padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Checkbox(checked = isIn, onCheckedChange = null)
                    Text(
                        m.name,
                        style = MeraPaisaType.body,
                        color = theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Their share of what has been typed so far. No sign and no ink: the heading
                    // above already says these are shares of one expense.
                    if (share != null) {
                        PlainAmountText(
                            amountMinor = share,
                            currencyCode = currency,
                            style = MeraPaisaType.amountSmall,
                            colour = theme.textSecondary
                        )
                    }
                }
            }

            if (amountMinor != null && amountMinor > 0 && sharedWith.size > 1) {
                if (!adjusting) {
                    TextButton(
                        onClick = { adjusting = true; custom = shares; locked = emptySet() },
                        modifier = Modifier.padding(horizontal = Spacing.sm).heightIn(min = 48.dp)
                    ) {
                        Text("Adjust the shares", style = MeraPaisaType.bodyStrong, color = theme.primary)
                    }
                } else {
                    FormHeading("Shares")
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        members.filter { it.id in sharedWith }.forEach { m ->
                            SplitShareRow(
                                participant = SplitParticipant(m.id, m.name, currency),
                                sourceCurrency = currency,
                                amountInSourceMinor = custom[m.id] ?: 0L,
                                convertedAmountMinor = null,
                                locked = m.id in locked,
                                animateShare = m.id != lastEdited,
                                onAmountChange = { typed ->
                                    lastEdited = m.id
                                    custom = redistribute(custom, locked + m.id, m.id, typed, amountMinor)
                                    locked = locked + m.id
                                },
                                onToggleLock = {
                                    if (m.id in locked) {
                                        locked = locked - m.id
                                        if (lastEdited == m.id) lastEdited = null
                                    } else {
                                        locked = locked + m.id
                                    }
                                }
                            )
                        }
                    }
                    if (!customAddsUp && custom.keys == sharedWith) {
                        Text(
                            mismatchMessage(
                                total = custom.values.sum(),
                                target = amountMinor,
                                currency = currency,
                                lockedSum = custom.filterKeys { it in locked }.values.sum(),
                                everyShareLocked = locked.containsAll(sharedWith)
                            ),
                            style = MeraPaisaType.label,
                            color = theme.negative,
                            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm)
                        )
                    }
                    TextButton(
                        onClick = { adjusting = false; keepStored = false },
                        modifier = Modifier.padding(horizontal = Spacing.sm).heightIn(min = 48.dp)
                    ) {
                        Text("Split equally", style = MeraPaisaType.bodyStrong, color = theme.primary)
                    }
                }
            }
        }

        SheetFoot(above = scroll) {
            SecondaryAction(if (editing == null) "Don't add" else "Keep it as it was", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
            PrimaryAction(if (editing == null) "Add expense" else "Save expense", enabled = valid && !adding) {
                val minor = amountMinor ?: return@PrimaryAction
                // Once only: the button stays live while the sheet slides away, and a second
                // tap in that time added the expense twice.
                if (adding) return@PrimaryAction
                adding = true
                val saved = if (adjusting) custom else sharesToSave(minor, sharedWith, editing.takeIf { keepStored })
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onSave(description.trim(), minor, paidBy, saved)
                }
            }
        }
    }
}

/** A quiet label over a block of the form, with air above it and none below. */
@Composable
private fun FormHeading(text: String) {
    val theme = LocalAppTheme.current
    Text(
        text,
        style = MeraPaisaType.sectionTitle,
        color = theme.textSecondary,
        modifier = Modifier.padding(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.xl,
            bottom = Spacing.sm
        )
    )
}
