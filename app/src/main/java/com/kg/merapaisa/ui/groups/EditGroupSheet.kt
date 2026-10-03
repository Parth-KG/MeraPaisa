package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
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
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.SheetFrame
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
    onSave: (name: String, newMemberIds: List<Long>) -> Unit
) {
    val theme = LocalAppTheme.current
    var name by remember(group.id) { mutableStateOf(group.name) }
    var selected by remember(group.id) { mutableStateOf(setOf<Long>()) }

    val memberIds = members.map { it.id }.toSet()
    val candidates = people.filter { it.id !in memberIds }

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
            }

            Text(
                "New members owe nothing for what was spent before they joined.",
                style = MeraPaisaType.label,
                color = theme.textSecondary,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md)
            )
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
                Text("Keep it as it was", style = MeraPaisaType.action)
            }
            Button(
                onClick = {
                    val newName = name.trim()
                    val added = selected.toList()
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onSave(newName, added) }
                },
                enabled = name.isNotBlank() && (name.trim() != group.name || selected.isNotEmpty()),
                shape = Shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.background
                )
            ) {
                Text("Save group", style = MeraPaisaType.action)
            }
        }
    }
}
