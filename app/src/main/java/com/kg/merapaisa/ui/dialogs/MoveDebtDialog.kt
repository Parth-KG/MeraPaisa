package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.normaliseCurrency
import com.kg.merapaisa.ui.MoveDebtFlowState
import com.kg.merapaisa.ui.format.AmountText
import com.kg.merapaisa.ui.format.amountSpoken
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import kotlinx.coroutines.launch
import com.kg.merapaisa.ui.format.TypedAmountText
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.SecondaryAction

/**
 * Moving part of the debt between you and one person onto somebody else, whichever way it runs.
 *
 * "Rondu owes you ₹624, move ₹100 of that to Sasti." Or the mirror: "You owe Rondu ₹500, and he
 * says pay ₹200 of it to Sasti." Two entries, equal and opposite, so the total between you and
 * everyone does not change; only who it is with.
 *
 * Anyone can take it. Someone kept in another currency takes it in theirs, converted at today's
 * rate when the move is saved, and the sheet says so once they are picked: moving ₹100 onto a
 * dollar balance must never silently claim $100.
 *
 * It was an AlertDialog holding a keypad, a list of people and two fields, in a box sized for a
 * question. It is a sheet now, which is what a form this size belongs on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveDebtDialog(
    state: MoveDebtFlowState,
    people: List<PersonWithBalance>,
    onKey: (String) -> Unit,
    onTargetChange: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val eligible = people.filter { it.id != state.fromPersonId }
    val target = eligible.firstOrNull { it.id == state.toPersonId }
    val converting = target != null && normaliseCurrency(target.currency) != state.currency
    val typed = state.amountMinor
    val overAvailable = typed != null && typed > state.availableMinor

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val scroll = rememberScrollState()

    SheetFrame(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scroll)
        ) {
            Text(
                if (state.youOwe) "Move what you owe" else "Move a debt",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.xs))
            val owed = amountString(state.availableMinor, state.currency)
            Text(
                if (state.youOwe) "You owe ${state.fromName} $owed. Move part of that onto somebody else."
                else "${state.fromName} owes you $owed. Move part of that onto somebody else.",
                style = MeraPaisaType.body,
                color = theme.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.lg))

            // The figure is grouped as it is typed, so what is being entered matches the row it is
            // about to become rather than showing 1234.50 against a ledger reading 1,234.50.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .clip(Shapes.medium)
                    .background(theme.highlight)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Amount",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                TypedAmount(state.amount, state.currency)
            }

            if (overAvailable) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    (if (state.youOwe) "That's more than you owe ${state.fromName} ($owed). "
                    else "That is more than ${state.fromName} owes you ($owed). ") +
                        "Take a digit off, or move the whole balance.",
                    style = MeraPaisaType.label,
                    color = theme.negative,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }

            Spacer(Modifier.height(Spacing.md))

            // The same keypad the rest of the app enters money with, so the rules about decimal
            // points and leading zeros are the ones people already know.
            AmountKeypad(currency = state.currency, onKey = onKey)

            SheetHeading("Move it to")

            if (eligible.isEmpty()) {
                Text(
                    "There is nobody else to move this to. Add a person first, then move it.",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }

            eligible.forEach { person ->
                TargetRow(
                    person = person,
                    selected = state.toPersonId == person.id,
                    onSelect = { onTargetChange(person.id) }
                )
            }

            if (converting) {
                Text(
                    "${target!!.name} is kept in ${normaliseCurrency(target.currency)}, so this " +
                        "moves as ${normaliseCurrency(target.currency)} at today's rate. Both " +
                        "entries say what it was converted to.",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text("Why, if it helps") },
                placeholder = { Text("Sasti covered it") },
                textStyle = MeraPaisaType.body,
                singleLine = true,
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )

            // Why the last attempt was refused. It stays on the sheet rather than closing it, so
            // the amount and the person picked are still there to correct.
            state.problem?.let { problem ->
                Spacer(Modifier.height(Spacing.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .clip(Shapes.medium)
                        .background(theme.highlight)
                        .padding(Spacing.md)
                ) {
                    Text(problem, style = MeraPaisaType.body, color = theme.negative)
                }
            }
        }

        SheetFoot(above = scroll) {
            SecondaryAction("Don't move", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
            // No hide before the callback here, unlike every other button on a sheet in this
            // app: the move can come back refused, and the sentence saying why is written on
            // this sheet. Playing it out first would hide the answer.
            PrimaryAction("Move the debt", enabled = state.canMove, onClick = onConfirm)
        }
    }
}

/**
 * Somebody the debt could go to, and where they stand right now.
 *
 * Their current balance is shown because moving a debt changes it, and a row that only offered a
 * name gave no way to tell whether you were about to pile a third debt onto the person who already
 * owes you the most.
 */
@Composable
private fun TargetRow(person: PersonWithBalance, selected: Boolean, onSelect: () -> Unit) {
    val theme = LocalAppTheme.current
    val direction = when {
        person.balanceMinor > 0 -> "owes you"
        person.balanceMinor < 0 -> "you owe"
        else -> "even"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            // One stop for TalkBack, so the row reads as a person and a position rather than as a
            // radio button, a name, a word and a figure.
            .semantics {
                contentDescription = amountSpoken(person.balanceMinor, person.currency, person.name)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // The row carries the click and the role, so the button is not separately reachable.
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                person.name,
                style = MeraPaisaType.bodyStrong,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(direction, style = MeraPaisaType.label, color = theme.textSecondary)
        }
        // The figure's own description is dropped so the row is read once rather than twice: it
        // says the same sentence the row above already carries.
        Box(Modifier.clearAndSetSemantics { }) {
            AmountText(
                amountMinor = person.balanceMinor,
                currencyCode = person.currency,
                style = MeraPaisaType.amount,
                columnAligned = true
            )
        }
    }
}

/**
 * What has been typed so far, grouped.
 *
 * Read back through the formatter while a trailing "." or a lone "0" is still being typed, which
 * neither parses nor groups, so those fall back to the raw text rather than blanking the display
 * mid-keystroke.
 */
@Composable
private fun TypedAmount(input: String, currency: String) {
    TypedAmountText(entry = input, currencyCode = currency)
}

/** The keypad, built the same way the one on the balances screen is, down to the backspace key. */
@Composable
private fun AmountKeypad(currency: String, onKey: (String) -> Unit) {
    // As on the other keypads: no point for a currency with no fractions.
    val point = if (com.kg.merapaisa.data.currencyDecimals(currency) == 0) "" else "."
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", point, "0", BACKSPACE)
    Column(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { key ->
                    if (key.isEmpty()) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    else Key(key, onKey, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Key(key: String, onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val isBackspace = key == BACKSPACE
    Button(
        // The keypad talks to the amount parser in the shape it expects, which is the glyph.
        onClick = { onKey(if (isBackspace) "⌫" else key) },
        modifier = modifier.heightIn(min = 52.dp),
        shape = Shapes.small,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            // highlight, not fill: fill is defined as card, which on a sheet sits too close to
            // the surface behind it, and the keys rendered as bare text with no visible shape.
            containerColor = theme.highlight,
            contentColor = if (isBackspace) theme.textSecondary else theme.textPrimary
        )
    ) {
        if (isBackspace) {
            Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete a digit")
        } else {
            Text(key, style = MeraPaisaType.body, modifier = Modifier.clearAndSetSemantics {
                contentDescription = if (key == ".") "decimal point" else key
            })
        }
    }
}

/** The key that clears one digit, told apart from the digits by name rather than by a glyph. */
private const val BACKSPACE = "backspace"
