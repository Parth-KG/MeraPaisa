package com.kg.merapaisa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.ui.format.AmountText
import com.kg.merapaisa.ui.format.amountSpoken
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Spacing

/**
 * Step two of a split: who it is between.
 *
 * Every row shows the person's real balance, signed, through AmountText. It used to show
 * abs(balance), so Asha owing you ₹500 and you owing Asha ₹500 were the same row, and the one
 * piece of information you need before adding someone to a split was the one thing removed.
 *
 * The button no longer doubles as a warning. It read "Select at least 2 people" while being
 * enabled at one, so the one state it explained was the state it would not act on. It is now
 * enabled only when the split is possible, and what is missing is said above it, in a sentence,
 * where an explanation belongs.
 */
@Composable
fun SplitPickerScreen(
    allPersons: List<PersonWithBalance>,
    selectedIds: Set<Long>,
    includeMe: Boolean,
    onToggleMe: () -> Unit,
    onTogglePerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit
) {
    val theme = LocalAppTheme.current
    val chosen = selectedIds.size + if (includeMe) 1 else 0
    val enoughPeople = chosen >= 2

    Column(modifier = Modifier.fillMaxSize().background(theme.background).coversLedger()) {

        SplitStepBar(onCancel = onCancel, onBack = onBack)

        SplitStepHeading(
            title = "Choose people",
            supporting = when (chosen) {
                0 -> "Nobody chosen yet"
                1 -> "1 person chosen"
                else -> "$chosen people chosen"
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.sm)
        ) {
            item {
                AddPersonRow(onClick = onAddPerson)
                RowDivider()
            }

            // "You" is in the split on the same terms as anybody else, so it is the same row,
            // without a balance: you cannot owe yourself anything.
            item {
                SplitPickerRow(
                    name = "You",
                    person = null,
                    selected = includeMe,
                    onToggle = onToggleMe
                )
            }

            items(allPersons, key = { it.id }) { person ->
                RowDivider()
                SplitPickerRow(
                    name = person.name,
                    person = person,
                    selected = person.id in selectedIds,
                    onToggle = { onTogglePerson(person.id) }
                )
            }
        }

        // Said here rather than on the button, and only while it is true.
        if (!enoughPeople) {
            Text(
                "A split needs at least two people. Tap a name to bring them in.",
                style = MeraPaisaType.label,
                color = theme.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )
        }

        SplitPrimaryButton(
            label = "Adjust the shares",
            enabled = enoughPeople,
            onClick = onNext
        )
    }
}

/** The way out of an empty picker: the person you want to split with may not be in the ledger yet. */
@Composable
private fun AddPersonRow(onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            Icons.Outlined.PersonAdd,
            contentDescription = null,
            tint = theme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text("Add a person", style = MeraPaisaType.bodyStrong, color = theme.primary)
    }
}

/**
 * One name you can bring into the split: a row on the background, not a tile.
 *
 * Selection is a wash of fill rather than a change of shape, so a row does not jump when you tap
 * it, and the whole row is the target rather than the checkbox alone. The checkbox keeps its
 * toggle state for TalkBack, which is why this merges its semantics instead of clearing them the
 * way a plain row does.
 */
@Composable
private fun SplitPickerRow(
    name: String,
    person: PersonWithBalance?,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val theme = LocalAppTheme.current
    val direction = when {
        person == null -> null
        person.balanceMinor > 0 -> "owes you"
        person.balanceMinor < 0 -> "you owe"
        else -> "even"
    }
    val spoken =
        if (person == null) name
        else amountSpoken(person.balanceMinor, person.currency, person.name)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) theme.highlight else Color.Transparent)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Not clickable itself: the row already is, and two targets in one row means two stops
        // for TalkBack and a dead strip between them for everyone else.
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            modifier = Modifier.size(24.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = theme.primary,
                checkmarkColor = theme.onAccent,
                uncheckedColor = theme.textSecondary
            )
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MeraPaisaType.bodyStrong,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (direction != null) {
                Text(direction, style = MeraPaisaType.label, color = theme.textSecondary)
            }
        }

        // The balance never shrinks to make room for a long name: it is what the choice is made on.
        if (person != null) {
            AmountText(
                amountMinor = person.balanceMinor,
                currencyCode = person.currency,
                style = MeraPaisaType.amount,
                columnAligned = true,
                spokenOwner = person.name
            )
        }
    }
}
