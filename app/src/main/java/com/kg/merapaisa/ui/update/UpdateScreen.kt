package com.kg.merapaisa.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.FootActions
import com.kg.merapaisa.ui.Paragraph
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.ScreenFrame
import com.kg.merapaisa.ui.SecondaryAction
import com.kg.merapaisa.ui.SectionHeading
import com.kg.merapaisa.ui.UpdateFlowState
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Spacing

/**
 * Offering, downloading and installing a new version.
 *
 * Deliberately never automatic. The app downloads nothing until asked and installs nothing without
 * the system's own confirmation on top: an app that updates itself silently is indistinguishable,
 * from the outside, from one that has been taken over.
 *
 * A full screen, like backup and update links. It used to be a dialog, but most of its states are
 * not questions at all (checking, downloading, up to date, failed), and the one with real content,
 * a release's notes, had to scroll inside a fixed box so the buttons stayed on a small phone. Here
 * the notes get the height of the screen and the decision stays at the foot.
 */
@Composable
fun UpdateScreen(
    state: UpdateFlowState,
    onDownload: (version: String, url: String) -> Unit,
    onInstall: (path: String) -> Unit,
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit
) {
    val theme = LocalAppTheme.current

    when (state) {
        // No way back while GitHub is being asked: leaving would not stop the request, and its
        // answer would bring this screen straight back a moment later.
        UpdateFlowState.Checking -> ScreenFrame(title = "Checking for updates", onBack = null) {
            item { Working("Asking GitHub for the newest release.") }
        }

        UpdateFlowState.UpToDate -> Message(
            title = "You're up to date",
            text = "You're running the newest release on GitHub.",
            onClose = onClose
        )

        is UpdateFlowState.Unreachable -> Message(
            title = "Couldn't check for updates",
            text = "${state.reason} Nothing has changed. Try again once you're connected, or " +
                "look at the releases page on GitHub yourself.",
            onClose = onClose
        )

        // Back counts as "not now", as tapping outside the dialog used to, so the daily check
        // does not offer this version again.
        is UpdateFlowState.Available -> ScreenFrame(
            title = "Version ${state.version} is available",
            onBack = onDismiss,
            footer = {
                FootActions {
                    SecondaryAction("Not now", enabled = true, onClick = onDismiss)
                    PrimaryAction(
                        "Download ${state.version}",
                        enabled = true,
                        onClick = { onDownload(state.version, state.downloadUrl) }
                    )
                }
            }
        ) {
            item {
                Paragraph(
                    "It installs over the top. Your ledger, groups and photos are untouched.",
                    colour = theme.textPrimary
                )
            }
            if (state.sizeBytes > 0) {
                item {
                    Text(
                        "About ${megabytes(state.sizeBytes)} MB to download",
                        style = MeraPaisaType.label,
                        color = theme.textSecondary,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                    )
                }
            }
            if (state.notes.isNotBlank()) {
                item { SectionHeading("What changed") }
                item { Paragraph(state.notes) }
            }
        }

        is UpdateFlowState.Downloading -> ScreenFrame(
            title = "Downloading ${state.version}",
            onBack = null
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
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
                }
            }
            item {
                Paragraph(
                    "The download is checked against this app's signing key before anything is " +
                        "installed.",
                    colour = theme.textSecondary
                )
            }
        }

        is UpdateFlowState.ReadyToInstall -> ScreenFrame(
            title = "Ready to install",
            onBack = onClose,
            footer = {
                FootActions {
                    SecondaryAction("Later", enabled = true, onClick = onClose)
                    PrimaryAction(
                        "Install ${state.version}",
                        enabled = true,
                        onClick = { onInstall(state.path) }
                    )
                }
            }
        ) {
            item {
                Paragraph(
                    "Version ${state.version} is downloaded, and it is signed with the same key as " +
                        "the app you're running. Android will ask you to confirm."
                )
            }
        }

        UpdateFlowState.SignatureMismatch -> Message(
            title = "That download's signing key doesn't match",
            text = "The file that arrived is signed with a different key from the app you're " +
                "running, so it was deleted rather than installed. That should never happen. " +
                "Don't install Mera Paisa from anywhere except the GitHub releases page, and " +
                "check the APK's SHA-256 against the one in the release notes.",
            colour = theme.negative,
            onClose = onClose
        )

        is UpdateFlowState.NeedsPermission -> ScreenFrame(
            title = "Allow installs from Mera Paisa",
            onBack = onClose,
            footer = {
                FootActions {
                    SecondaryAction("Not now", enabled = true, onClick = onClose)
                    PrimaryAction("Open Android settings", enabled = true, onClick = onGrantPermission)
                }
            }
        ) {
            item {
                Paragraph(
                    "To install an update itself, Mera Paisa needs \"install unknown apps\" for " +
                        "this app. You'll still confirm every install. If you would rather not " +
                        "grant it, download version ${state.version} from the GitHub releases " +
                        "page and install it yourself."
                )
            }
        }

        // Two different failures. A download that broke off leaves nothing behind; an installer
        // that would not open leaves a checked file, so it is not described as a failed download.
        is UpdateFlowState.Failed -> if (state.downloaded) {
            Message(
                title = "Android's installer didn't open",
                text = "The update downloaded and passed the signing check, but nothing was " +
                    "installed. Check for updates in Settings to try again, or take the APK " +
                    "from the GitHub releases page." +
                    state.reason.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                onClose = onClose
            )
        } else {
            Message(
                title = "The update didn't download",
                text = "${state.reason} Nothing was installed and nothing changed. Try again " +
                    "once you're connected, or take the APK from the GitHub releases page.",
                onClose = onClose
            )
        }
    }
}

/** An outcome with nothing left to decide: what happened, and the way back. */
@Composable
private fun Message(
    title: String,
    text: String,
    onClose: () -> Unit,
    colour: Color = LocalAppTheme.current.textSecondary
) {
    ScreenFrame(
        title = title,
        onBack = onClose,
        footer = {
            FootActions { PrimaryAction("Back to your ledger", enabled = true, onClick = onClose) }
        }
    ) {
        item { Paragraph(text, colour = colour) }
    }
}

/** A spinner beside the sentence saying what it is waiting for. */
@Composable
private fun Working(text: String) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = theme.primary,
            trackColor = theme.outline
        )
        Text(text, style = MeraPaisaType.body, color = theme.textSecondary)
    }
}

/** Whole megabytes, rounded, and never "0 MB" for a file that exists. */
private fun megabytes(bytes: Long): Long = maxOf(1L, (bytes + 512 * 1024) / (1024 * 1024))
