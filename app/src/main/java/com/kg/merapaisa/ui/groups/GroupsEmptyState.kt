package com.kg.merapaisa.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing

/**
 * What the Groups tab says before there is a group, and what it offers.
 *
 * It used to end with "Tap + to start one", which is a set of directions to a control instead of
 * the control, and it went stale the moment the plus became a named button. The first action is
 * here now, under one line saying what is missing, aligned left with the gutter so the line starts
 * where the first group row will start.
 */
@Composable
fun GroupsEmptyState(modifier: Modifier = Modifier, onCreateGroup: () -> Unit = {}) {
    val theme = LocalAppTheme.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text("No groups yet.", style = MeraPaisaType.screenTitle, color = theme.textPrimary)
        Button(
            onClick = onCreateGroup,
            shape = Shapes.small,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.primary,
                contentColor = theme.onAccent
            )
        ) {
            Text("New group", style = MeraPaisaType.action)
        }
    }
}
