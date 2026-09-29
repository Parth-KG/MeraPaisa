package com.kg.merapaisa.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.UpdateFlowState
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing

/**
 * Offering, downloading and installing a new version.
 *
 * Deliberately never automatic. The app downloads nothing until asked and installs nothing without
 * the system's own confirmation on top: an app that updates itself silently is indistinguishable,
 * from the outside, from one that has been taken over.
 *
 * This stays a dialog while the rest of the app moves its long content onto screens, because every
 * state here is a question with two answers (install it or not, grant it or not, try again or
 * not). The one piece that is genuinely long, a release's notes, scrolls inside its own box rather
 * than pushing the buttons off the bottom.
 */
@Composable
fun UpdateDialog(
    state: UpdateFlowState,
    onDownload: (version: String, url: String) -> Unit,
    onInstall: (path: String) -> Unit,
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit
) {
    val theme = LocalAppTheme.current

    when (state) {
        UpdateFlowState.Checking -> Shell(
            title = "Checking for updates",
            onDismiss = {},
            body = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = theme.primary,
                        trackColor = theme.outline
                    )
                    Text("Asking GitHub for the newest release.", style = MeraPaisaType.body, color = theme.textSecondary)
                }
            },
            confirm = {}
        )

        UpdateFlowState.UpToDate -> Shell(
            title = "You're up to date",
            onDismiss = onClose,
            body = {
                Text(
                    "You're running the newest release on GitHub.",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary
                )
            },
            confirm = { DialogButton("Back to your ledger", onClose) }
        )

        is UpdateFlowState.Unreachable -> Shell(
            title = "Couldn't check for updates",
            onDismiss = onClose,
            body = {
                Text(
                    "${state.reason} Nothing has changed. Try again once you're connected, or " +
                        "look at the releases page on GitHub yourself.",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary
                )
            },
            confirm = { DialogButton("Back to your ledger", onClose) }
        )

        is UpdateFlowState.Available -> Shell(
            title = "Version ${state.version} is available",
            onDismiss = onDismiss,
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "It installs over the top. Your ledger, groups and photos are untouched.",
                        style = MeraPaisaType.body,
                        color = theme.textPrimary
                    )
                    if (state.sizeBytes > 0) {
                        Text(
                            "${state.sizeBytes / 1024 / 1024} MB to download",
                            style = MeraPaisaType.label,
                            color = theme.textSecondary
                        )
                    }
                    if (state.notes.isNotBlank()) {
                        Text("What changed", style = MeraPaisaType.sectionTitle, color = theme.textPrimary)
                        // The notes are the only thing here that has no length anybody controls.
                        // Given a whole scroll of them they used to grow until the buttons went
                        // off the bottom of a small phone, so they scroll inside a fixed box and
                        // the decision stays on screen. No line cap: plainNotes already reflows
                        // and trims, and cutting by line count chopped mid-sentence once the
                        // paragraphs were reflowed into long ones.
                        Text(
                            state.notes,
                            style = MeraPaisaType.label,
                            color = theme.textSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirm = { DialogButton("Download", { onDownload(state.version, state.downloadUrl) }) },
            dismiss = { DialogButton("Not now", onDismiss, quiet = true) }
        )

        is UpdateFlowState.Downloading -> Shell(
            title = "Downloading ${state.version}",
            onDismiss = {},
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    if (state.percent >= 0) {
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = theme.primary,
                            trackColor = theme.outline
                        )
                        Text("${state.percent}%", style = MeraPaisaType.label, color = theme.textSecondary)
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = theme.primary,
                            trackColor = theme.outline
                        )
                    }
                    Text(
                        "The download is checked against this app's signing key before anything " +
                            "is installed.",
                        style = MeraPaisaType.label,
                        color = theme.textSecondary
                    )
                }
            },
            confirm = {}
        )

        is UpdateFlowState.ReadyToInstall -> Shell(
            title = "Ready to install",
            onDismiss = onClose,
            body = {
                Text(
                    "Version ${state.version} is downloaded, and it is signed with the same key " +
                        "as the app you're running. Android will ask you to confirm.",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary
                )
            },
            confirm = { DialogButton("Install", { onInstall(state.path) }) },
            dismiss = { DialogButton("Later", onClose, quiet = true) }
        )

        UpdateFlowState.SignatureMismatch -> Shell(
            title = "That download's signing key doesn't match",
            onDismiss = onClose,
            body = {
                Text(
                    "The file that arrived is signed with a different key from the app you're " +
                        "running, so it was deleted rather than installed. That should never " +
                        "happen. Don't install Mera Paisa from anywhere except the GitHub " +
                        "releases page, and check the APK's SHA-256 against the one in the " +
                        "release notes.",
                    style = MeraPaisaType.body,
                    color = theme.negative
                )
            },
            confirm = { DialogButton("Back to your ledger", onClose) }
        )

        is UpdateFlowState.NeedsPermission -> Shell(
            title = "Allow installs from Mera Paisa",
            onDismiss = onClose,
            body = {
                Text(
                    "To install an update itself, Mera Paisa needs \"install unknown apps\" for " +
                        "this app. You'll still confirm every install. If you would rather not " +
                        "grant it, download version ${state.version} from the GitHub releases " +
                        "page and install it yourself.",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary
                )
            },
            confirm = { DialogButton("Open Android settings", onGrantPermission) },
            dismiss = { DialogButton("Not now", onClose, quiet = true) }
        )

        is UpdateFlowState.Failed -> Shell(
            title = "The update didn't download",
            onDismiss = onClose,
            body = {
                Text(
                    "${state.reason} Nothing was installed and nothing changed. Try again once " +
                        "you're connected, or take the APK from the GitHub releases page.",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary
                )
            },
            confirm = { DialogButton("Back to your ledger", onClose) }
        )
    }
}

/**
 * The one dialog every state is poured into, so the title, the ink and the corner are decided
 * once rather than nine times.
 */
@Composable
private fun Shell(
    title: String,
    onDismiss: () -> Unit,
    body: @Composable () -> Unit,
    confirm: @Composable () -> Unit,
    dismiss: (@Composable () -> Unit)? = null
) {
    val theme = LocalAppTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Shapes.medium,
        containerColor = theme.card,
        title = { Text(title, style = MeraPaisaType.screenTitle, color = theme.textPrimary) },
        text = body,
        confirmButton = confirm,
        dismissButton = dismiss ?: {}
    )
}

/** A dialog button. The quiet one is the way out, so it does not compete with the decision. */
@Composable
private fun DialogButton(label: String, onClick: () -> Unit, quiet: Boolean = false) {
    val theme = LocalAppTheme.current
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(
            label,
            style = MeraPaisaType.action,
            color = if (quiet) theme.textSecondary else theme.primary
        )
    }
}
