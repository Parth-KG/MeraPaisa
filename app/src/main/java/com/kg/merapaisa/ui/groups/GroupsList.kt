package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.GroupSummary
import com.kg.merapaisa.ui.ListClearance
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.TextRowInset
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Spacing

/**
 * The Groups tab: groups in use, then the archived ones in a section of their own at the bottom,
 * closed until opened (v3.7.0). An archived group still opens and edits; only where it is listed
 * changes. With every group archived, the line at the top says so and still offers a new group.
 */
@Composable
fun GroupsList(
    groups: List<GroupSummary>,
    archived: List<GroupSummary>,
    showArchived: Boolean,
    onToggleArchived: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onArchive: (groupId: Long, archive: Boolean) -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    // No horizontal padding here: GroupRow pads to the gutter itself, as PersonRow does, and
    // RowDivider measures its inset from the list edge.
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = ListClearance)) {
        if (groups.isEmpty()) {
            item(key = "all-archived") {
                GroupsEmptyState(onCreateGroup = onCreateGroup, title = "Every group is archived.")
            }
        }
        itemsIndexed(groups, key = { _, it -> it.group.id }) { index, summary ->
            if (index > 0) RowDivider(TextRowInset)
            GroupRow(
                summary = summary,
                onClick = { onOpen(summary.group.id) },
                onDelete = { onDelete(summary.group.id) },
                onArchive = { onArchive(summary.group.id, true) }
            )
        }
        if (archived.isNotEmpty()) {
            item(key = "archived-heading") { ArchivedHeading(archived.size, showArchived, onToggleArchived) }
            if (showArchived) {
                itemsIndexed(archived, key = { _, it -> "archived-${it.group.id}" }) { index, summary ->
                    if (index > 0) RowDivider(TextRowInset)
                    GroupRow(
                        summary = summary,
                        onClick = { onOpen(summary.group.id) },
                        onDelete = { onDelete(summary.group.id) },
                        onArchive = { onArchive(summary.group.id, false) }
                    )
                }
            }
        }
    }
}

/**
 * The Archived section's heading, which opens and closes it. The count is in the words, and the
 * action on the right says what a tap does, so neither depends on an arrow.
 */
@Composable
private fun ArchivedHeading(count: Int, open: Boolean, onToggle: () -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg)
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide" else "Show", onClick = onToggle)
            .semantics { stateDescription = if (open) "Shown" else "Hidden" }
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("$count archived", style = MeraPaisaType.sectionTitle, color = theme.textSecondary)
        Text(if (open) "Hide" else "Show", style = MeraPaisaType.bodyStrong, color = theme.primary)
    }
}
