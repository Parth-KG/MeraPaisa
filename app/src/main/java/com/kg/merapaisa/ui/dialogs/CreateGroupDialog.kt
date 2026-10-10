package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.SUPPORTED_CURRENCIES
import com.kg.merapaisa.data.currencySymbol
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.choiceChipBorder
import com.kg.merapaisa.ui.choiceChipColors
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.SecondaryAction
import kotlinx.coroutines.launch

/**
 * A name, a currency, and who you are splitting with.
 *
 * You are always in your own groups, so there is no checkbox for yourself, only for the people you
 * are sharing costs with. It was an AlertDialog around four fields and a list of everybody you
 * know; a dialog is for a decision, so a form this size goes on a sheet that can take the height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupDialog(
    people: List<PersonWithBalance>,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, currency: String, memberIds: List<Long>, simplifyDebts: Boolean) -> Unit
) {
    val theme = LocalAppTheme.current
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    // Defaults to on, which is what every group has done since groups shipped.
    var simplifyDebts by remember { mutableStateOf(true) }

    // Anyone can join, whatever currency their own balance is kept in. A group is its own
    // ledger in its own currency and moves nothing on the main screen. Filtered against the list
    // so someone deleted while the sheet is open drops out.
    val chosen = selected.filterTo(mutableSetOf()) { id -> people.any { it.id == id } }

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
                "New group",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.lg))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("Goa trip, Flat 402") },
                singleLine = true,
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = simplifyDebts,
                        onValueChange = { simplifyDebts = it },
                        role = Role.Switch
                    )
                    .heightIn(min = 48.dp)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fewest payments", style = MeraPaisaType.bodyStrong, color = theme.textPrimary)
                    Text(
                        if (simplifyDebts)
                            "No one gets money just to pass it on, so there are as few payments " +
                                "as possible."
                        else
                            "Each debt stays with the expense that created it.",
                        style = MeraPaisaType.label,
                        color = theme.textSecondary
                    )
                }
                Switch(checked = simplifyDebts, onCheckedChange = null)
            }

            FormHeading("Currency")
            Row(
                modifier = Modifier.padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SUPPORTED_CURRENCIES.forEach { code ->
                    FilterChip(
                        selected = currency == code,
                        onClick = { currency = code },
                        shape = Shapes.small,
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text(currencySymbol(code), style = MeraPaisaType.body) },
                        colors = choiceChipColors(),
                        border = choiceChipBorder(currency == code)
                    )
                }
            }

            FormHeading(
                if (chosen.isEmpty()) "Who else is in it?"
                else "${chosen.size} selected, plus you"
            )

            if (people.isEmpty()) {
                Text(
                    "Add some people first. A group needs somebody to split with.",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            } else {
                people.forEachIndexed { index, person ->
                    if (index > 0) RowDivider()
                    val isIn = person.id in chosen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = isIn,
                                onValueChange = {
                                    selected = if (isIn) selected - person.id else selected + person.id
                                },
                                role = Role.Checkbox
                            )
                            .heightIn(min = 48.dp)
                            .padding(horizontal = Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        // The row carries the click and the role, so the box is not separately
                        // reachable: TalkBack hears one person, not a checkbox and then a name.
                        Checkbox(checked = isIn, onCheckedChange = null)
                        Text(
                            person.name,
                            style = MeraPaisaType.body,
                            color = theme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        SheetFoot(above = scroll) {
            SecondaryAction("Don't create", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
            PrimaryAction("Create group", enabled = name.isNotBlank() && chosen.isNotEmpty()) {
                val members = chosen.toList()
                val groupName = name.trim()
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onCreate(groupName, currency, members, simplifyDebts)
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
