package com.kg.merapaisa.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Spacer
import com.kg.merapaisa.data.currencyDecimals
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.isUsableAmount
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.format.TypedAmountText

/** The key that clears one digit, told apart from the digits by name rather than by a glyph. */
private const val BACKSPACE = "backspace"

/**
 * Recording an amount against one person.
 *
 * Keys stay tiles: this is the one place where a grid of identical rounded shapes is right, since
 * they really are identical controls doing the same job at the same weight. Everything around
 * them moved to the token radius so the keys read as a keypad rather than as more cards.
 *
 * The figure being typed is grouped as you type, so the display matches the row it is about to
 * become. It showed ₹1234.50 while the ledger behind it showed ₹1,234.50.
 */
@Composable
fun NumPad(
    person: PersonWithBalance,
    input: String,
    onKey: (String) -> Unit,
    onSettleToggle: () -> Unit,
    onAdd: () -> Unit,
    onSubtract: () -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    showNote: Boolean,
    onToggleNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    val amountIsUsable = isUsableAmount(input)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.card)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Shapes.medium)
                .background(theme.fillStrong)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                person.name,
                style = MeraPaisaType.body,
                color = theme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TypedAmount(input, person.currency)
        }

        // The whole row toggles, words included, and TalkBack hears one switch. Only the small
        // switch at the end used to respond.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = showNote, onValueChange = { onToggleNote() }, role = Role.Switch)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Add a note", style = MeraPaisaType.body, color = theme.textSecondary)
            Switch(checked = showNote, onCheckedChange = null)
        }

        if (showNote) {
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                placeholder = {
                    Text("Dinner, cab fare", style = MeraPaisaType.body, color = theme.textSecondary)
                },
                textStyle = MeraPaisaType.body,
                singleLine = true,
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // No point for a currency with no fractions: yen typed as 12.5 was stored as ¥12.50 and shown
        // as ¥13. The slot stays, empty, so the 0 keeps its place under the 8.
        val point = if (currencyDecimals(person.currency) == 0) "" else "."
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", point, "0", BACKSPACE)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { key ->
                        if (key.isEmpty()) Spacer(Modifier.weight(1f))
                        else Key(key, onKey, Modifier.weight(1f))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DirectionButton(
                label = "You paid them",
                ink = theme.textPrimary,
                enabled = amountIsUsable,
                onClick = onAdd,
                modifier = Modifier.weight(1f)
            )
            DirectionButton(
                label = "They paid you",
                ink = theme.textPrimary,
                enabled = amountIsUsable,
                onClick = onSubtract,
                modifier = Modifier.weight(1f)
            )
        }

        // Settling files a person away and closes their balance; reopening brings them back.
        // Someone already at zero can still be settled, which is how you file them away.
        TextButton(
            onClick = onSettleToggle,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(
                if (person.isSettled) "Reopen" else "Settle up",
                style = MeraPaisaType.action,
                color = theme.textPrimary
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

@Composable
private fun Key(key: String, onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val isBackspace = key == BACKSPACE
    Button(
        onClick = { onKey(if (isBackspace) "⌫" else key) },
        modifier = modifier.heightIn(min = 52.dp),
        shape = Shapes.small,
        contentPadding = PaddingZero,
        colors = ButtonDefaults.buttonColors(
            containerColor = theme.fillStrong,
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

/**
 * Which way the money went, named rather than signed.
 *
 * These were a "+" and a "−" key, which say what the app will do to a number rather than what
 * happened between two people. The words tell them apart, not colour: green and red are the
 * amount inks, kept for amounts, so both buttons wear the same neutral tile.
 */
@Composable
private fun DirectionButton(
    label: String,
    ink: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = Shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = theme.fillStrong,
            contentColor = ink,
            disabledContainerColor = theme.fillStrong.copy(alpha = 0.5f),
            disabledContentColor = ink.copy(alpha = 0.4f)
        )
    ) {
        // Two lines at most rather than one: cut to a single line at large type, the buttons read
        // "You paid" and "They", which is the one place a missing word changes where money goes.
        Text(label, style = MeraPaisaType.action, maxLines = 2, textAlign = TextAlign.Center)
    }
}

private val PaddingZero = androidx.compose.foundation.layout.PaddingValues(0.dp)
