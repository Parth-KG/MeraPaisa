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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
import com.kg.merapaisa.data.Group
import androidx.compose.material3.TextButton
import com.kg.merapaisa.data.involvementOf
import com.kg.merapaisa.data.Involvement
import com.kg.merapaisa.data.ExpenseShare
import com.kg.merapaisa.data.Expense
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.SecondaryAction
import kotlinx.coroutines.launch

/**
 * Renames a group and brings more people into it.
 *
 * A group was fixed the moment it was made: a typo in its name stayed, and someone who joined the
 * trip a day late could not be added. Nobody is taken out here. Their share is already part of
 * past expenses, and removing them would have to move money someone else is owed, which is a
 * decision this sheet cannot make for anyone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGroupSheet(
    group: Group,
    members: List<Person>,
    people: List<PersonWithBalance>,
    onDismiss: () -> Unit,
    onSave: (name: String, newMemberIds: List<Long>, removedMemberIds: List<Long>) -> Unit,
    /**
     * People ticked when the sheet opens. Empty in the app, since nobody is added until ticked. It
     * exists so the gallery can show the sheet with people chosen, which is otherwise only
     * reachable by tapping and therefore never appears in a screenshot.
     */
    initiallySelectedIds: Set<Long> = emptySet(),
    /** Adds someone new to the ledger without leaving the sheet. No-op in the gallery. */
    onCreatePerson: CreatePerson = { _, _, _, _, _, _ -> },
    /** The group's rows, to tell who can be removed: only someone nothing here involves. */
    expenses: List<Expense> = emptyList(),
    shares: List<ExpenseShare> = emptyList(),
    /** You, who are in every group of yours and are never offered for removal. */
    selfId: Long = 0
) {
    val theme = LocalAppTheme.current
    var name by remember(group.id) { mutableStateOf(group.name) }
    var selected by remember(group.id) { mutableStateOf(initiallySelectedIds) }
    var addingPerson by remember { mutableStateOf(false) }
    var removing by remember(group.id) { mutableStateOf(setOf<Long>()) }

    val memberIds = members.map { it.id }.toSet()
    val candidates = people.filter { it.id !in memberIds }

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
                "Edit group",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.lg))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )

            Text(
                "Members",
                style = MeraPaisaType.sectionTitle,
                color = theme.textSecondary,
                modifier = Modifier.padding(
                    start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.sm
                )
            )
            // You first, then everyone else as the group lists them. You can't leave your own
            // group; anyone else can go only while nothing here names them (Parth, 15 Nov 2026).
            val ordered = members.sortedBy { if (it.id == selfId) 0 else 1 }
            ordered.forEachIndexed { index, member ->
                if (index > 0) RowDivider()
                MemberRow(
                    name = if (member.id == selfId) "You" else member.name,
                    involvement = if (member.id == selfId) null else involvementOf(member.id, expenses, shares),
                    leaving = member.id in removing,
                    onToggle = {
                        removing = if (member.id in removing) removing - member.id else removing + member.id
                    }
                )
            }

            Text(
                if (selected.isEmpty()) "Add people" else "${selected.size} to add",
                style = MeraPaisaType.sectionTitle,
                color = theme.textSecondary,
                modifier = Modifier.padding(
                    start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.sm
                )
            )

            if (candidates.isEmpty()) {
                Text(
                    "Everyone you track is already in this group.",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            } else {
                candidates.forEachIndexed { index, person ->
                    if (index > 0) RowDivider()
                    val isIn = person.id in selected
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
                RowDivider()
            }
            AddNewPersonRow(onClick = { addingPerson = true })

            Text(
                "New members owe nothing for what was spent before they joined.",
                style = MeraPaisaType.label,
                color = theme.textSecondary,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md)
            )
        }

        SheetFoot(above = scroll) {
            SecondaryAction("Keep it as it was", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
            PrimaryAction(
                "Save group",
                enabled = name.isNotBlank() &&
                    (name.trim() != group.name || selected.isNotEmpty() || removing.isNotEmpty())
            ) {
                val newName = name.trim()
                val added = selected.toList()
                val removed = removing.toList()
                scope.launch { sheetState.hide() }.invokeOnCompletion { onSave(newName, added, removed) }
            }
        }
    }

    if (addingPerson) {
        AddPersonOverGroup(
            people = people,
            currency = group.currency,
            onCreate = onCreatePerson,
            onDismiss = { addingPerson = false },
            onAdded = { id ->
                selected = selected + id
                addingPerson = false
            }
        )
    }
}

/**
 * One member in Edit group. You have no action. Anyone nothing in the group involves can be taken
 * out, which happens when the sheet is saved ("Remove", then "Keep" to change your mind); anyone
 * else has a line saying why they stay.
 */
@Composable
private fun MemberRow(name: String, involvement: Involvement?, leaving: Boolean, onToggle: () -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MeraPaisaType.body,
                color = if (leaving) theme.textSecondary else theme.textPrimary,
                // Two lines at large type, as list titles get, so a long name beside "Remove" is read.
                maxLines = com.kg.merapaisa.ui.listTitleMaxLines(),
                overflow = TextOverflow.Ellipsis
            )
            val line = when {
                leaving -> "Leaves the group when you save."
                involvement == Involvement.Paid -> "Paid for something here, so they stay."
                involvement == Involvement.Share -> "Has a share of something here, so they stay."
                else -> null
            }
            if (line != null) Text(line, style = MeraPaisaType.label, color = theme.textSecondary)
        }
        if (involvement == Involvement.None) {
            TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (leaving) "Keep" else "Remove", style = MeraPaisaType.bodyStrong, color = theme.primary)
            }
        }
    }
}

