package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.ui.dialogs.AddPersonDialog
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Spacing

/**
 * Adds someone to the ledger from inside a group sheet: name, avatar type, avatar value, colour and
 * currency, then [onAdded] with their new id once they exist.
 */
typealias CreatePerson = (
    name: String,
    pfpType: String,
    pfpValue: String,
    pfpColor: String,
    currency: String,
    onAdded: (Long) -> Unit
) -> Unit

/**
 * "Add a new person", the last row of a group sheet's people list (v3.7.0). A group's members are
 * people in the ledger, and before this the only way to add someone was to close the sheet, add
 * them on Balances and start again, losing what was typed (Parth, 15 Nov 2026).
 *
 * Words in the accent, set where the names start, so it reads as the next row of the list and as
 * something to press.
 */
@Composable
internal fun AddNewPersonRow(onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Add a new person", style = MeraPaisaType.bodyStrong, color = theme.primary)
    }
}

/**
 * Add a person, over a group sheet. They start in the group's [currency] and are created in the
 * ledger like anyone else, on Even until they owe something; [onAdded] then ticks them in the sheet
 * underneath, which stays as it was.
 */
@Composable
internal fun AddPersonOverGroup(
    people: List<PersonWithBalance>,
    currency: String,
    onCreate: CreatePerson,
    onDismiss: () -> Unit,
    onAdded: (Long) -> Unit
) {
    AddPersonDialog(
        existingNames = people.map { it.name },
        existingColours = people.map { it.person.pfpColor },
        initialCurrency = currency,
        onDismiss = onDismiss,
        onAdd = { name, pfpType, pfpValue, pfpColor, personCurrency ->
            onCreate(name, pfpType, pfpValue, pfpColor, personCurrency, onAdded)
        }
    )
}
