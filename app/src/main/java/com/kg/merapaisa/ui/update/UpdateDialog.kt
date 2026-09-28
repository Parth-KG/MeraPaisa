package com.kg.merapaisa.ui.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.UpdateFlowState

/**
 * Offering, downloading and installing a new version.
 *
 * Deliberately never automatic. The app downloads nothing until asked and installs nothing without
 * the system's own confirmation on top — an app that updates itself silently is indistinguishable,
 * from the outside, from one that has been taken over.
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Asking GitHub…", fontSize = 13.sp, color = theme.textSecondary)
                }
            },
            confirm = {}
        )

        UpdateFlowState.UpToDate -> Shell(
            title = "You're up to date",
            onDismiss = onClose,
            body = { Text("This is the newest release.", fontSize = 13.sp, color = theme.textSecondary) },
            confirm = { TextButton(onClick = onClose) { Text("Done", color = theme.primary) } }
        )

        is UpdateFlowState.Unreachable -> Shell(
            title = "Couldn't check",
            onDismiss = onClose,
            body = {
                Text(
                    "${state.reason} Nothing has changed — you can try again later, or download from " +
                        "the releases page yourself.",
                    fontSize = 13.sp,
                    color = theme.textSecondary
                )
            },
            confirm = { TextButton(onClick = onClose) { Text("Close", color = theme.primary) } }
        )

        is UpdateFlowState.Available -> Shell(
            title = "Version ${state.version} is available",
            onDismiss = onDismiss,
            body = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Installs over the top — your ledger, groups and photos are untouched.",
                        fontSize = 13.sp,
                        color = theme.textPrimary
                    )
                    if (state.notes.isNotBlank()) {
                        // No line cap here — plainNotes already reflows and trims to a length a
                        // dialog can hold. Cutting by line count chopped mid-sentence once the
                        // paragraphs were reflowed into long ones.
                        Text(state.notes, fontSize = 12.sp, color = theme.textSecondary)
                    }
                    if (state.sizeBytes > 0) {
                        Text("${state.sizeBytes / 1024 / 1024} MB", fontSize = 11.sp, color = theme.textSecondary)
                    }
                }
            },
            confirm = {
                TextButton(onClick = { onDownload(state.version, state.downloadUrl) }) {
                    Text("Download", color = theme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismiss = { TextButton(onClick = onDismiss) { Text("Not now", color = theme.textSecondary) } }
        )

        is UpdateFlowState.Downloading -> Shell(
            title = "Downloading ${state.version}",
            onDismiss = {},
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.percent >= 0) {
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${state.percent}%", fontSize = 12.sp, color = theme.textSecondary)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "The download is checked against this app's signing key before anything is " +
                            "installed.",
                        fontSize = 11.sp,
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
                    "Version ${state.version} downloaded, and it is signed with the same key as the " +
                        "app you're running. Android will ask you to confirm.",
                    fontSize = 13.sp,
                    color = theme.textSecondary
                )
            },
            confirm = {
                TextButton(onClick = { onInstall(state.path) }) {
                    Text("Install", color = theme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismiss = { TextButton(onClick = onClose) { Text("Later", color = theme.textSecondary) } }
        )

        UpdateFlowState.SignatureMismatch -> Shell(
            title = "That download isn't right",
            onDismiss = onClose,
            body = {
                Text(
                    "The file that arrived is signed with a different key from the app you're " +
                        "running, so it was deleted rather than installed. That should never " +
                        "happen. Don't install Mera Paisa from anywhere except the GitHub " +
                        "releases page, and check the APK's SHA-256 against the one in the " +
                        "release notes.",
                    fontSize = 13.sp,
                    color = theme.negative
                )
            },
            confirm = { TextButton(onClick = onClose) { Text("Close", color = theme.primary) } }
        )

        is UpdateFlowState.NeedsPermission -> Shell(
            title = "Android needs your permission",
            onDismiss = onClose,
            body = {
                Text(
                    "To install an update itself, Mera Paisa needs \"install unknown apps\" for this " +
                        "app. You'll still confirm every install. Or download version " +
                        "${state.version} from GitHub yourself — either works.",
                    fontSize = 13.sp,
                    color = theme.textSecondary
                )
            },
            confirm = {
                TextButton(onClick = onGrantPermission) {
                    Text("Open settings", color = theme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismiss = { TextButton(onClick = onClose) { Text("Not now", color = theme.textSecondary) } }
        )

        is UpdateFlowState.Failed -> Shell(
            title = "The update didn't download",
            onDismiss = onClose,
            body = {
                Text(
                    "${state.reason} Nothing was installed and nothing changed.",
                    fontSize = 13.sp,
                    color = theme.textSecondary
                )
            },
            confirm = { TextButton(onClick = onClose) { Text("Close", color = theme.primary) } }
        )
    }
}

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
        containerColor = theme.card,
        title = { Text(title, color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = body,
        confirmButton = confirm,
        dismissButton = dismiss ?: {}
    )
}
