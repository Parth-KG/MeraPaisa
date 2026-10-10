package com.kg.merapaisa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
 * What is on screen while the app is locked.
 *
 * Deliberately shows nothing of the ledger, not a name and not a balance, because this view is
 * also what appears behind the system prompt and in the recents thumbnail.
 *
 * The padlock is gone. A large icon centred above a sentence is the empty-screen mannerism the
 * design avoids everywhere else, and here it was drawing attention to a screen whose whole job is
 * to be uninteresting. Left aligned with the app's gutter, so unlocking lands on a layout that
 * already matches the ledger underneath.
 */
@Composable
fun LockedScreen(onUnlock: () -> Unit) {
    val theme = LocalAppTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Mera Paisa is locked", style = MeraPaisaType.screenTitle, color = theme.textPrimary)
        Text(
            "Your ledger is hidden until you unlock it.",
            style = MeraPaisaType.body,
            color = theme.textSecondary,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xl)
        )
        Button(
            onClick = onUnlock,
            shape = Shapes.small,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.primary,
                contentColor = theme.onAccent
            )
        ) {
            // The system prompt's own title, so the tap and the prompt it opens say the same thing.
            // Not a list of methods: the lock also accepts a pattern or a password, and which
            // biometrics exist depends on the phone.
            ButtonLabel("Unlock Mera Paisa")
        }
    }
}
