package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
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
import com.kg.merapaisa.data.isTypableAmount
import com.kg.merapaisa.data.parseAmountToMinor
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.format.PlainAmountText
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.SheetFrame
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight

/**
 * What somebody paid for, who paid, and who it is split between.
 *
 * This was an AlertDialog with a whole form inside it, and inside that two lazy lists: one of radio
 * buttons and one of checkboxes, each capped at a height that showed three members of six. A dialog
 * is for a decision, so a form of four fields goes on a sheet, where the members are plain rows
 * that scroll with everything else rather than two little windows scrolling inside a third.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    members: List<Person>,
    currency: String,
    selfId: Long,
    onDismiss: () -> Unit,
    onAdd: (description: String, amountMinor: Long, paidByPersonId: Long, sharedWith: List<Long>) -> Unit
) {
    val theme = LocalAppTheme.current
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    // selfId is filled asynchronously and starts at 0, which is nobody. Keying on it stops the
    // field latching onto that placeholder and writing an expense whose payer does not exist.
    var paidBy by remember(selfId, members) {
        mutableLongStateOf(members.firstOrNull { it.id == selfId }?.id ?: members.firstOrNull()?.id ?: 0L)
    }
    var sharedWith by remember(members) { mutableStateOf(members.map { it.id }.toSet()) }

    var adding by remember { mutableStateOf(false) }
    val amountMinor = parseAmountToMinor(amount)
    val valid = description.isNotBlank() && amountMinor != null && amountMinor > 0 &&
        sharedWith.isNotEmpty() && members.any { it.id == paidBy }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    SheetFrame(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Add an expense",
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
                val share = if (isIn && amountMinor != null) {
                    evenShares(amountMinor, sharedWith.toList().sorted())[m.id]
                } else null
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
        }

        Row(
            modifier = Modifier.height(IntrinsicSize.Min)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                shape = Shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
                border = BorderStroke(1.dp, theme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textPrimary)
            ) {
                Text("Don't add", style = MeraPaisaType.action)
            }
            Button(
                onClick = {
                    val minor = amountMinor ?: return@Button
                    // Once only: the button stays live while the sheet slides away, and a second
                    // tap in that time added the expense twice.
                    if (adding) return@Button
                    adding = true
                    val shares = sharedWith.toList().sorted()
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onAdd(description.trim(), minor, paidBy, shares)
                    }
                },
                enabled = valid && !adding,
                shape = Shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.background
                )
            ) {
                Text("Add expense", style = MeraPaisaType.action)
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
