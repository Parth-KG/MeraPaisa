package com.kg.merapaisa.ui.walkthrough

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.Paragraph
import com.kg.merapaisa.ui.SectionHeading
import com.kg.merapaisa.ui.dialogs.ActionRow
import com.kg.merapaisa.ui.dialogs.AppLockRow
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Spacing

/**
 * The walkthrough's last page: the app lock, your UPI ID and weekly backups, each optional and
 * each the same row Settings has, doing what it does there. The lock asks before it changes, and
 * the UPI ID is saved behind the phone's lock, as in Settings.
 *
 * Weekly backups rather than "Back up now": on a first run there is nothing to back up yet, and
 * once a folder is chosen the backups follow as the ledger fills (Parth, 20 Nov 2026).
 *
 * On a replay from Settings each row says what is already set.
 */
@Composable
fun WalkthroughSetUp(
    appLockEnabled: Boolean,
    appLockAvailable: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    myUpiId: String?,
    onMyUpiId: () -> Unit,
    /** The folder weekly backups go to, or null while they are off. */
    backupFolder: String?,
    onPickFolder: () -> Unit
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.lg)
    ) {
        Text(
            "Set up, if you like",
            style = MeraPaisaType.screenTitle,
            color = theme.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .semantics { heading() }
        )
        Spacer(Modifier.height(Spacing.sm))
        Paragraph("Each of these is optional, and stays in Settings for later.")

        SectionHeading("App lock")
        AppLockRow(enabled = appLockEnabled, available = appLockAvailable, onChange = onAppLockChange)

        SectionHeading("UPI")
        ActionRow(
            title = "Your UPI ID",
            subtitle = myUpiId ?: "Not set. With it, friends can scan a QR to pay you.",
            onClick = onMyUpiId
        )

        SectionHeading("Backups")
        ActionRow(
            title = if (backupFolder == null) "Back up every week" else "Weekly backups are on",
            subtitle = if (backupFolder == null) {
                "Pick a folder, and a backup is saved there every week."
            } else {
                "Saving to $backupFolder once a week. Tap to pick another folder."
            },
            onClick = onPickFolder
        )
        Text(
            "Mera Paisa has no server, so a backup file is the copy you keep if this phone is lost. " +
                "Android's own backup may also copy your ledger to your Google account.",
            style = MeraPaisaType.label,
            color = theme.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        )
    }
}
