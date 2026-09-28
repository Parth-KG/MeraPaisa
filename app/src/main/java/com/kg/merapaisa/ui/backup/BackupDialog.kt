package com.kg.merapaisa.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.RestoreMode
import com.kg.merapaisa.ui.BackupFlowState
import com.kg.merapaisa.ui.RestoreSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup and restore.
 *
 * The restore half exists to answer one question before anything happens: *what is this about to
 * do to my ledger?* A restore can delete every person and entry, so the counts are on screen, in
 * the selected mode, before the button is live — and the two things a file cannot put back (groups
 * from a CSV, photos from anywhere) are stated rather than discovered afterwards.
 */
@Composable
fun BackupDialog(
    state: BackupFlowState,
    onSaveBackup: () -> Unit,
    onRestore: () -> Unit,
    onPickFolder: () -> Unit,
    onTurnOffAuto: () -> Unit,
    onBackUpNow: () -> Unit,
    onModeChange: (RestoreMode) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is BackupFlowState.Menu -> MenuDialog(
            state, onSaveBackup, onRestore, onPickFolder, onTurnOffAuto, onBackUpNow, onDismiss
        )
        BackupFlowState.Working -> WorkingDialog()
        is BackupFlowState.Reviewing -> ReviewDialog(state, onModeChange, onApply, onDismiss)
        is BackupFlowState.Unreadable -> MessageDialog(state.title, state.detail, onDismiss)
        is BackupFlowState.Done -> MessageDialog(state.title, state.detail, onDismiss)
    }
}

@Composable
private fun MenuDialog(
    state: BackupFlowState.Menu,
    onSaveBackup: () -> Unit,
    onRestore: () -> Unit,
    onPickFolder: () -> Unit,
    onTurnOffAuto: () -> Unit,
    onBackUpNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val automatic = state.folderName != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = { Text("Back up & restore", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActionRow(
                    title = "Save a backup",
                    subtitle = "One file with everything — people, entries, groups and expenses.",
                    enabled = !state.busy,
                    onClick = onSaveBackup
                )
                ActionRow(
                    title = "Restore from a file",
                    subtitle = "A backup, or a ledger CSV this app exported.",
                    enabled = !state.busy,
                    onClick = onRestore
                )

                HorizontalDivider(color = theme.outline, modifier = Modifier.padding(vertical = 6.dp))

                Text(
                    "Weekly backup",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary
                )

                if (automatic) {
                    Text(
                        "Saving to ${state.folderName} once a week, keeping the last 12.",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                    if (state.lastRun > 0) {
                        Text(
                            "Last run ${dateTimeOf(state.lastRun)} — ${state.lastResult.orEmpty()}",
                            fontSize = 11.sp,
                            // An automatic job runs when nobody is watching, so a failure has to be
                            // legible here or it looks identical to never having been set up.
                            color = if (state.lastResult?.startsWith("Backed up") == true) theme.textSecondary else theme.negative
                        )
                    } else {
                        Text("It has not run yet.", fontSize = 11.sp, color = theme.textSecondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onBackUpNow, enabled = !state.busy) {
                            Text(if (state.busy) "Working…" else "Back up now", color = theme.primary)
                        }
                        TextButton(onClick = onPickFolder, enabled = !state.busy) {
                            Text("Change folder", color = theme.textSecondary)
                        }
                        TextButton(onClick = onTurnOffAuto, enabled = !state.busy) {
                            Text("Turn off", color = theme.negative)
                        }
                    }
                } else {
                    Text(
                        "Off. Pick a folder and Mera Paisa will save a backup there every week, " +
                            "keeping the last 12.",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                    TextButton(onClick = onPickFolder, enabled = !state.busy) {
                        Text("Choose a folder", color = theme.primary)
                    }
                }

                HorizontalDivider(color = theme.outline, modifier = Modifier.padding(vertical = 6.dp))

                // Said here rather than discovered on a new phone, where it is too late.
                Text(
                    "Profile photos are not included in a backup — restoring on another phone " +
                        "shows initials for those people instead.",
                    fontSize = 11.sp,
                    color = theme.textSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = theme.primary) }
        }
    )
}

@Composable
private fun ActionRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = theme.textSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun WorkingDialog() {
    val theme = LocalAppTheme.current
    AlertDialog(
        onDismissRequest = {},
        containerColor = theme.card,
        title = { Text("Working", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("Nothing has changed yet.", fontSize = 13.sp, color = theme.textSecondary)
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ReviewDialog(
    state: BackupFlowState.Reviewing,
    onModeChange: (RestoreMode) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val plan = state.plan
    val inserts = plan.inserts

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = { Text("Restore", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    when (state.source) {
                        RestoreSource.Json ->
                            "A full backup" +
                                (state.exportedAt?.let { ", saved ${dateTimeOf(it)}" } ?: "") +
                                (state.appVersion?.let { " by version $it" } ?: "") + "."
                        RestoreSource.Csv ->
                            "A ledger CSV. It holds people and entries only — no groups or expenses."
                    },
                    fontSize = 13.sp,
                    color = theme.textPrimary
                )

                Text("How should it be applied?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)

                ModeOption(
                    selected = state.mode == RestoreMode.Merge,
                    title = "Add what is missing",
                    subtitle = "Keeps everything you already have. Safe to run twice.",
                    onClick = { onModeChange(RestoreMode.Merge) }
                )
                ModeOption(
                    selected = state.mode == RestoreMode.Replace,
                    title = "Replace everything",
                    subtitle = "Deletes your current ledger first, then restores the file exactly.",
                    onClick = { onModeChange(RestoreMode.Replace) }
                )

                HorizontalDivider(color = theme.outline)

                Text("What this will do", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)

                if (state.mode == RestoreMode.Replace && !plan.deletes.isZero) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.fillStrong)
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Deletes ${countPhrase(plan.deletes.people, "person", "people")}, " +
                                    "${countPhrase(plan.deletes.transactions, "entry", "entries")}" +
                                    (if (plan.deletes.groups > 0) " and ${countPhrase(plan.deletes.groups, "group", "groups")}" else "") +
                                    ". This cannot be undone.",
                                fontSize = 12.sp,
                                color = theme.negative,
                                fontWeight = FontWeight.Medium
                            )
                            if (state.replaceWouldLoseGroups) {
                                // The trap this whole flag exists for: a CSV has no groups in it,
                                // so replacing from one destroys them and restores none.
                                Text(
                                    "A CSV contains no groups, so those " +
                                        "${if (plan.deletes.groups == 1) "group is" else "groups are"} " +
                                        "deleted and not restored. Use a full backup if you need them.",
                                    fontSize = 12.sp,
                                    color = theme.negative
                                )
                            }
                        }
                    }
                }

                Text(
                    "Adds ${countPhrase(inserts.people, "person", "people")}, " +
                        "${countPhrase(inserts.transactions, "entry", "entries")}" +
                        (if (inserts.groups > 0) ", ${countPhrase(inserts.groups, "group", "groups")}" else "") +
                        (if (inserts.expenses > 0) ", ${countPhrase(inserts.expenses, "expense", "expenses")}" else "") +
                        ".",
                    fontSize = 13.sp,
                    color = theme.textPrimary
                )

                if (state.mode == RestoreMode.Merge && !plan.alreadyPresent.isZero) {
                    Text(
                        "Already here and left alone: " +
                            "${countPhrase(plan.alreadyPresent.people, "person", "people")}, " +
                            "${countPhrase(plan.alreadyPresent.transactions, "entry", "entries")}" +
                            (if (plan.alreadyPresent.groups > 0) ", ${countPhrase(plan.alreadyPresent.groups, "group", "groups")}" else "") +
                            ".",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                }

                if (plan.changesNothing) {
                    Text(
                        "Your ledger already contains everything in this file, so nothing would change.",
                        fontSize = 12.sp,
                        color = theme.textSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply, enabled = !state.busy && !plan.changesNothing) {
                Text(
                    if (state.mode == RestoreMode.Replace) "Replace my ledger" else "Add them",
                    color = if (state.mode == RestoreMode.Replace) theme.negative else theme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textSecondary) }
        }
    )
}

@Composable
private fun ModeOption(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = theme.textPrimary)
            Text(subtitle, fontSize = 11.sp, color = theme.textSecondary)
        }
    }
}

@Composable
private fun MessageDialog(title: String, detail: String, onDismiss: () -> Unit) {
    val theme = LocalAppTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = { Text(title, color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = { Text(detail, fontSize = 13.sp, color = theme.textSecondary) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = theme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

private fun countPhrase(n: Int, singular: String, plural: String): String =
    "$n ${if (n == 1) singular else plural}"

private fun dateTimeOf(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy 'at' HH:mm", Locale.getDefault()).format(Date(timestamp))
