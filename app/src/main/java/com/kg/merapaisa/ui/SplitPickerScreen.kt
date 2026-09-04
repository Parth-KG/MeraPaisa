package com.kg.merapaisa.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.AppTheme
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.formatMinor

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(16.dp, 16.dp, 16.dp, 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.textPrimary)
                }
                Text(
                    "Select people",
                    color = theme.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = theme.textPrimary)
                }
            }

            // Selected count
            Text(
                "${selectedIds.size + (if (includeMe) 1 else 0)} selected",
                color = theme.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            // List
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {

                // "Add person" row at top
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onAddPerson)
                            .padding(16.dp, 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = theme.positive,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "Add new person",
                            color = theme.positive,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // "You" row
                item {
                    SplitPickerRow(
                        label = "You",
                        sublabel = null,
                        selected = includeMe,
                        theme = theme,
                        onClick = onToggleMe
                    )
                }

                // All persons
                items(allPersons, key = { it.id }) { person ->
                    SplitPickerRow(
                        label = person.name,
                        sublabel = formatMinor(kotlin.math.abs(person.balanceMinor), person.currency),
                        selected = person.id in selectedIds,
                        theme = theme,
                        onClick = { onTogglePerson(person.id) }
                    )
                }
            }

            // Next button
            val totalSelected = selectedIds.size + (if (includeMe) 1 else 0)
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(20.dp, 8.dp, 20.dp, 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = totalSelected >= 1,
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.positive,
                    contentColor = theme.background,
                    disabledContainerColor = theme.positive.copy(alpha = 0.3f),
                    disabledContentColor = theme.background.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    if (totalSelected < 2) "Select at least 2 people" else "Next",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SplitPickerRow(
    label: String,
    sublabel: String?,
    selected: Boolean,
    theme: AppTheme,    // adjust this type to match your theme class name
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) theme.positive.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(16.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onClick() }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = theme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (sublabel != null) {
                Text(sublabel, color = theme.textSecondary, fontSize = 12.sp)
            }
        }
    }
}
