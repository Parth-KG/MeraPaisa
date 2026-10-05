package com.kg.merapaisa.ui

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
 * What an empty tab says, and what it offers.
 *
 * It used to describe where a control was: "Tap the + button in the bottom-left corner to add
 * someone you split money with." That is instructions for finding a button instead of a button,
 * and it went stale the moment the button moved. The first action is now here, under one line
 * saying what is missing. A second sentence selling the app went too: the button already says
 * what to do.
 *
 * No illustration and no oversized icon. There is nothing to illustrate: the screen is empty
 * because nothing has been recorded yet, and a drawing of a wallet does not change that.
 *
 * Aligned left with the content rather than centred, so the line starts where every row beneath
 * it will start once there is something here.
 */
@Composable
fun EmptyState(tab: Tab, modifier: Modifier = Modifier, onAddPerson: () -> Unit = {}) {
    val theme = LocalAppTheme.current

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        when (tab) {
            Tab.Active -> {
                Text("No one here yet.", style = MeraPaisaType.screenTitle, color = theme.textPrimary)
                Button(
                    onClick = onAddPerson,
                    shape = Shapes.small,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.onAccent
                    )
                ) {
                    Text("Add a person", style = MeraPaisaType.action)
                }
            }

            // Nothing to offer here: you reach this tab by settling someone on the Active tab,
            // so a button would have to send you back where you came from. The second line
            // stands in for it by naming that action.
            Tab.Settled -> {
                Text("Nothing settled yet.", style = MeraPaisaType.screenTitle, color = theme.textPrimary)
                Text(
                    "Settle someone up and they move here, with their history kept.",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary
                )
            }

            Tab.Groups -> Unit
        }
    }
}
