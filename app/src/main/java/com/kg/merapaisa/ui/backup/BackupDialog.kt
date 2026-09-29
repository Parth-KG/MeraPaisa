package com.kg.merapaisa.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.RestoreCounts
import com.kg.merapaisa.data.RestoreMode
import com.kg.merapaisa.ui.BackupFlowState
import com.kg.merapaisa.ui.DecisionDialog
import com.kg.merapaisa.ui.RestoreSource
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.TextRowInset
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.FootActions
import com.kg.merapaisa.ui.Paragraph
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.ScreenFrame
import com.kg.merapaisa.ui.SecondaryAction
import com.kg.merapaisa.ui.SectionHeading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup and restore.
 *
 * The restore half exists to answer one question before anything happens: *what is this about to
 * do to my ledger?* A restore can delete every person and entry, so the counts are on screen, in
 * the selected mode, before the button is live, and the two things a file cannot put back (groups
 * from a CSV, photos from anywhere) are stated rather than discovered afterwards.
 *
 * All of that used to live in AlertDialogs: a scrolling menu, a scrolling review with two radio
 * options and a warning block, and two more for the result. A dialog is a box sized for a
 * question, and none of these are questions. They are screens now, switched by [BackupFlowState]
 * the way the group screen is, with a back arrow and their actions named at the foot.
 *
 * One AlertDialog is left, and it is the only genuine decision here: replacing a ledger destroys
 * what is already on the phone, so it is asked as a question, in a box, with the count in it.
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
    onDismiss: () -> Unit,
    /** Where closing goes, named: Settings when it was opened from there, the ledger otherwise. */
    backLabel: String = "Back to your ledger"
) {
    when (state) {
        is BackupFlowState.Menu -> MenuScreen(
            state, onSaveBackup, onRestore, onPickFolder, onTurnOffAuto, onBackUpNow, onDismiss
        )
        BackupFlowState.Working -> WorkingScreen()
        is BackupFlowState.Reviewing -> ReviewScreen(state, onModeChange, onApply, onDismiss)
        // A failure's sentence is in the negative ink, the app's one colour for "this went wrong".
        is BackupFlowState.Unreadable ->
            MessageScreen(state.title, state.detail, backLabel, onDismiss, LocalAppTheme.current.negative)
        is BackupFlowState.Done -> MessageScreen(state.title, state.detail, backLabel, onDismiss)
    }
}

@Composable
private fun MenuScreen(
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

    ScreenFrame(
        title = "Back up and restore",
        onBack = onDismiss,
        footer = {
            // The two things anyone opens this screen for, named, one tap away, the way the group
            // screen carries its own two.
            FootActions {
                SecondaryAction("Restore from a file", enabled = !state.busy, onClick = onRestore)
                PrimaryAction("Save a backup", enabled = !state.busy, onClick = onSaveBackup)
            }
        }
    ) {
        item {
            Paragraph(
                "A backup is one file with everything: people, entries, groups and expenses. " +
                    "Restoring reads one back, or a CSV export from this app."
            )
        }

        item { SectionHeading("Weekly backup") }

        if (automatic) {
            item {
                Paragraph("Saving to ${state.folderName} once a week, keeping the last 12.")
            }
            item {
                if (state.lastRun > 0) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            "Last run ${dateTimeOf(state.lastRun)}",
                            style = MeraPaisaType.label,
                            color = theme.textSecondary
                        )
                        val result = state.lastResult.orEmpty()
                        if (result.isNotBlank()) {
                            Text(
                                result,
                                style = MeraPaisaType.label,
                                // A weekly job runs when nobody is watching, so a failure has to be
                                // legible here or it looks identical to never having been set up.
                                color = if (lastRunSucceeded(result)) theme.textSecondary
                                else theme.negative
                            )
                        }
                    }
                } else {
                    Paragraph("It has not run yet.")
                }
            }
            item { Spacer(Modifier.height(Spacing.sm)) }
            item {
                ActionRow(
                    title = if (state.busy) "Backing up…" else "Back up now",
                    subtitle = "Writes one straight away, without waiting for the week.",
                    enabled = !state.busy,
                    onClick = onBackUpNow
                )
            }
            item { RowDivider(TextRowInset) }
            item {
                ActionRow(
                    title = "Change folder",
                    subtitle = "Later backups go to the new folder. The ones already saved stay where they are.",
                    enabled = !state.busy,
                    onClick = onPickFolder
                )
            }
            item { RowDivider(TextRowInset) }
            item {
                ActionRow(
                    title = "Turn off weekly backups",
                    subtitle = "Nothing saved is deleted, and you can still save a backup by hand.",
                    enabled = !state.busy,
                    onClick = onTurnOffAuto,
                    ink = theme.textPrimary
                )
            }
        } else {
            item {
                Paragraph(
                    "Off. Pick a folder and Mera Paisa saves a backup there every week, " +
                        "keeping the last 12."
                )
            }
            item { Spacer(Modifier.height(Spacing.sm)) }
            item {
                ActionRow(
                    title = "Choose a folder",
                    subtitle = "Any folder on this phone that the app can write to.",
                    enabled = !state.busy,
                    onClick = onPickFolder
                )
            }
        }

        item { SectionHeading("What a backup leaves out") }
        item {
            // Said here rather than discovered on a new phone, where it is too late.
            Paragraph(
                "Profile photos. Restoring on another phone shows initials for those people instead."
            )
        }
    }
}

@Composable
private fun WorkingScreen() {
    val theme = LocalAppTheme.current
    ScreenFrame(title = "Working on the file", onBack = null) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = theme.primary)
                Text(
                    "Your ledger hasn't changed.",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary
                )
            }
        }
    }
}

@Composable
private fun ReviewScreen(
    state: BackupFlowState.Reviewing,
    onModeChange: (RestoreMode) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val plan = state.plan
    val inserts = plan.inserts
    val replacing = state.mode == RestoreMode.Replace
    // Replace deletes a ledger, so it is asked rather than assumed. Merge is not asked, because
    // adding what is missing takes nothing away and can be run twice with the same result.
    val destructive = replacing && !plan.deletes.isZero
    var confirming by remember { mutableStateOf(false) }

    ScreenFrame(
        title = "Restore",
        onBack = if (state.busy) null else onDismiss,
        footer = {
            FootActions {
                SecondaryAction("Don't restore", enabled = !state.busy, onClick = onDismiss)
                PrimaryAction(
                    label = if (replacing) "Replace my ledger" else "Add what is missing",
                    enabled = !state.busy && !plan.changesNothing,
                    onClick = { if (destructive) confirming = true else onApply() }
                )
            }
        }
    ) {
        item {
            Paragraph(
                when (state.source) {
                    RestoreSource.Json ->
                        "A full backup" +
                            (state.exportedAt?.let { ", saved ${dateTimeOf(it)}" } ?: "") +
                            (state.appVersion?.let { " by version $it" } ?: "") + "."
                    RestoreSource.Csv ->
                        "A CSV export. It holds people and entries only, with no groups or expenses."
                },
                colour = theme.textPrimary
            )
        }

        item { SectionHeading("How should it be applied?") }
        item {
            ModeOption(
                selected = state.mode == RestoreMode.Merge,
                title = "Add what is missing",
                subtitle = "Keeps everything you already have. Safe to run twice.",
                onClick = { onModeChange(RestoreMode.Merge) }
            )
        }
        item { RowDivider(TextRowInset) }
        item {
            ModeOption(
                selected = replacing,
                title = "Replace everything",
                subtitle = "Deletes your current ledger first, then restores the file exactly.",
                onClick = { onModeChange(RestoreMode.Replace) }
            )
        }

        item { SectionHeading("What this will do") }

        if (destructive) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .clip(Shapes.medium)
                        .background(theme.fillStrong)
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        deletesSentence(plan.deletes),
                        style = MeraPaisaType.bodyStrong,
                        color = theme.negative
                    )
                    if (state.replaceWouldLoseGroups) {
                        // The trap this whole flag exists for: a CSV has no groups in it, so
                        // replacing from one destroys them and restores none.
                        Text(
                            "A CSV export contains no groups, so those " +
                                "${if (plan.deletes.groups == 1) "group is" else "groups are"} " +
                                "deleted and not restored. Use a full backup if you need them.",
                            style = MeraPaisaType.body,
                            color = theme.negative
                        )
                    }
                }
            }
            // Air between the warning and the count under it, which sat against the box's edge.
            item { Spacer(Modifier.height(Spacing.md)) }
        }

        item {
            Paragraph(
                "Adds ${countPhrase(inserts.people, "person", "people")}, " +
                    "${countPhrase(inserts.transactions, "entry", "entries")}" +
                    (if (inserts.groups > 0) ", ${countPhrase(inserts.groups, "group", "groups")}" else "") +
                    (if (inserts.expenses > 0) ", ${countPhrase(inserts.expenses, "expense", "expenses")}" else "") +
                    ".",
                colour = theme.textPrimary
            )
        }

        if (state.mode == RestoreMode.Merge && !plan.alreadyPresent.isZero) {
            item {
                Paragraph(
                    "Already here and left alone: " +
                        "${countPhrase(plan.alreadyPresent.people, "person", "people")}, " +
                        "${countPhrase(plan.alreadyPresent.transactions, "entry", "entries")}" +
                        (if (plan.alreadyPresent.groups > 0) ", ${countPhrase(plan.alreadyPresent.groups, "group", "groups")}" else "") +
                        "."
                )
            }
        }

        if (plan.changesNothing) {
            item {
                Paragraph(
                    "Your ledger already holds everything in this file, so nothing would change."
                )
            }
        }
    }

    if (confirming) {
        ReplaceConfirmDialog(
            deletes = plan.deletes,
            losesGroups = state.replaceWouldLoseGroups,
            onConfirm = { confirming = false; onApply() },
            onDismiss = { confirming = false }
        )
    }
}

/**
 * The one decision in this flow, and the one thing here still shaped like a dialog.
 *
 * The review screen can be read at leisure and scrolled past; this cannot. It names the count it
 * is about to destroy rather than the file it is about to restore, because the count is the part
 * that cannot be got back.
 */
@Composable
private fun ReplaceConfirmDialog(
    deletes: RestoreCounts,
    losesGroups: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    DecisionDialog(
        title = "Replace your ledger?",
        body = deletesSentence(deletes),
        warning = if (losesGroups) "A CSV export puts no groups back." else null,
        confirmLabel = "Replace my ledger",
        dismissLabel = "Keep my ledger",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
private fun MessageScreen(
    title: String,
    detail: String,
    action: String,
    onDismiss: () -> Unit,
    colour: Color = LocalAppTheme.current.textSecondary
) {
    ScreenFrame(
        title = title,
        onBack = onDismiss,
        footer = { FootActions { PrimaryAction(action, enabled = true, onClick = onDismiss) } }
    ) {
        item { Paragraph(detail, colour = colour) }
    }
}

/**
 * One thing you can do to the weekly backup: a row on the background, not a tile.
 *
 * The subtitle says what the tap costs, because none of these three are guessable from a verb:
 * changing the folder leaves the old backups behind, and turning the job off deletes nothing.
 */
@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    ink: Color = LocalAppTheme.current.textPrimary
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MeraPaisaType.bodyStrong,
                color = if (enabled) ink else theme.textSecondary
            )
            Text(subtitle, style = MeraPaisaType.label, color = theme.textSecondary)
        }
    }
}

/**
 * One of the two ways a restore can be applied.
 *
 * Selectable as a whole row rather than as a button with a label beside it, so the target is the
 * full width and TalkBack reads the pair as one choice instead of a control and some stray text.
 */
@Composable
private fun ModeOption(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MeraPaisaType.bodyStrong, color = theme.textPrimary)
            Text(subtitle, style = MeraPaisaType.label, color = theme.textSecondary)
        }
    }
}

/** What Replace is about to destroy, in the same words wherever it is said. */
/**
 * Whether the stored result of the last backup run is a success.
 *
 * The result is a sentence, written by AutoExportWorker or by Back up now and kept in DataStore,
 * so this matches its wording. Both wordings count: "Saved a backup." is current, and "Backed up
 * successfully." is what earlier versions stored, which a phone keeps until the next run. Matching
 * only the old one turned every successful backup red the moment the sentence was reworded.
 */
internal fun lastRunSucceeded(result: String): Boolean =
    result.startsWith("Saved a backup") || result.startsWith("Backed up")

private fun deletesSentence(deletes: RestoreCounts): String =
    "Deletes ${countPhrase(deletes.people, "person", "people")}, " +
        "${countPhrase(deletes.transactions, "entry", "entries")}" +
        (if (deletes.groups > 0) " and ${countPhrase(deletes.groups, "group", "groups")}" else "") +
        ". This can't be undone."

private fun countPhrase(n: Int, singular: String, plural: String): String =
    "$n ${if (n == 1) singular else plural}"

private fun dateTimeOf(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy 'at' HH:mm", Locale.getDefault()).format(Date(timestamp))
