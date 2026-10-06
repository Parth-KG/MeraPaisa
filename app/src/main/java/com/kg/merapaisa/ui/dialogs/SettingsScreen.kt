package com.kg.merapaisa.ui.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.TextRowInset
import com.kg.merapaisa.ui.coversLedger
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Spacing

/**
 * Everything about the app itself rather than about who owes whom.
 *
 * It was an AlertDialog, and it had outgrown one badly: a switch, three places to go, an export,
 * every theme and a credit line, stacked inside a box sized for a question and scrolling against
 * its own buttons. A dialog is for a decision. This is a screen now, built like the group screen:
 * a back arrow, the title in the gutter, and sections of rows on the background.
 *
 * The theme is one row naming the theme in use. Every theme used to unfold here as a list, under
 * a button at the foot that was dead until you picked one; now the row opens [ThemeSheet], where a
 * theme is tried on your own ledger before it is used.
 */
@Composable
fun SettingsScreen(
    people: List<PersonWithBalance>,
    appLockEnabled: Boolean,
    appLockAvailable: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    onImportLink: () -> Unit,
    onBackupRestore: () -> Unit,
    onExportCsv: () -> Unit,
    canExport: Boolean,
    onCheckUpdates: () -> Unit,
    appVersion: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    // Turning the phone keeps the sheet open, and the sheet keeps the theme being tried.
    var trying by rememberSaveable { mutableStateOf(false) }

    // This is part of the main tree rather than a Dialog window, so back has to be caught here.
    // Left alone it falls through to the people list underneath and closes the app.
    BackHandler(enabled = true) { onDismiss() }

    Column(modifier = Modifier.fillMaxSize().background(theme.background).coversLedger()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.textPrimary
                )
            }
        }

        Text(
            "Settings",
            style = MeraPaisaType.screenTitle,
            color = theme.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = Spacing.lg)
        )

        // No button at the foot, so the list itself has to clear the navigation bar.
        val clearOfBar = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(bottom = Spacing.lg + clearOfBar.calculateBottomPadding())
        ) {
            item { SectionHeading("App lock") }
            item {
                AppLockRow(
                    enabled = appLockEnabled,
                    available = appLockAvailable,
                    onChange = onAppLockChange
                )
            }

            item { SectionHeading("Your ledger") }
            item {
                // An action rather than a setting, so it acts at once and leaves the screen. Until
                // assetlinks.json is live at the domain root, a tapped link does not reach the
                // app, which makes this the only way one can get in.
                ActionRow(title = "Record an update link", onClick = onImportLink)
            }
            item { RowDivider(TextRowInset) }
            item {
                // Moved here from the main screen's top bar, where it was an unlabelled share
                // icon beside Settings. Writing a CSV is a filing job that belongs with backup,
                // not something to reach for while reading a balance. Disabled rather than hidden
                // when there is nothing to write, so it does not appear and vanish.
                ActionRow(
                    title = "Export as CSV",
                    subtitle = if (canExport) null else "Nothing to export yet. Add a person first.",
                    enabled = canExport,
                    onClick = onExportCsv
                )
            }
            item { RowDivider(TextRowInset) }
            item {
                ActionRow(title = "Back up and restore", onClick = onBackupRestore)
            }
            item {
                // The manifest allows Android's own backup, so the database leaves the phone
                // whether or not anybody saves a file. Better said here, plainly, than discovered
                // on a new phone: it is the system's feature, and this app has no account of its
                // own to confuse it with.
                Text(
                    "Android's own backup also copies your ledger to your Google account. " +
                        "Mera Paisa has no account or server of its own.",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                )
            }

            item { SectionHeading("This app") }
            item {
                ActionRow(
                    title = "Check for updates",
                    subtitle = "You're on $appVersion.",
                    onClick = onCheckUpdates
                )
            }
            item { RowDivider(TextRowInset) }
            item {
                ActionRow(title = "Theme", subtitle = theme.name, onClick = { trying = true })
            }

            item {
                Text(
                    "Made for fun by Parth, @Parth-KG on GitHub.",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .padding(top = Spacing.xxl)
                )
            }
        }
    }

    if (trying) {
        ThemeSheet(
            inUse = theme,
            people = people,
            onDismiss = { trying = false },
            onUse = { name ->
                trying = false
                onApply(name)
            }
        )
    }
}

/** A heading inside the list. Sentence case, quiet, with air above it and none below. */
@Composable
private fun SectionHeading(text: String) {
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

/**
 * The lock, and what to do when the phone cannot offer one.
 *
 * The whole row is the switch, not just the switch itself, because a 32dp control at the far edge
 * of a phone is the hardest thing on the screen to hit. When there is no screen lock set the row
 * stops being toggleable rather than silently doing nothing, and the sentence says what to set.
 */
@Composable
private fun AppLockRow(enabled: Boolean, available: Boolean, onChange: (Boolean) -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (available) {
                    Modifier.toggleable(value = enabled, onValueChange = onChange, role = Role.Switch)
                } else {
                    Modifier
                }
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Lock the app",
                style = MeraPaisaType.bodyStrong,
                color = if (available) theme.textPrimary else theme.textSecondary
            )
            // Only when it cannot be turned on: then the row needs to say why, and what to set.
            if (!available) {
                Text(
                    "Your phone has no screen lock, so there is nothing to ask for. Set one in " +
                        "Android settings and this can be turned on.",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary
                )
            }
        }
        // The row owns the toggle, so the switch itself takes no callback: with both live, a tap
        // on the switch would be handled twice.
        Switch(checked = enabled, enabled = available, onCheckedChange = null)
    }
}

/**
 * A place to go or a thing to do, written as a row on the background like every other list in the
 * app. Disabled states keep the row where it was and grey the words, so nothing shifts under the
 * thumb when the ledger fills up.
 */
@Composable
private fun ActionRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MeraPaisaType.bodyStrong,
                color = if (enabled) theme.textPrimary else theme.textSecondary
            )
            if (subtitle != null) Text(subtitle, style = MeraPaisaType.label, color = theme.textSecondary)
        }
    }
}
