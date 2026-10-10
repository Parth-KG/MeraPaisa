package com.kg.merapaisa.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.ShareFlowState
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.SecondaryAction
import kotlinx.coroutines.launch

/**
 * The outgoing half of the two-sided ledger: what this link will tell the other phone, shown before
 * it goes anywhere.
 *
 * It was called a sheet and built as an AlertDialog. It is a sheet now.
 *
 * The name field is not decoration. The self row ships called "You", and a link whose sender is
 * "You" gives the recipient nothing to match against, so this asks once and remembers. Left empty,
 * the link used to go out from "A friend", which tells the person opening it even less, so the
 * send button waits for a name and the line under the field says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLedgerSheet(
    state: ShareFlowState,
    onSenderNameChange: (String) -> Unit,
    onFullHistoryChange: (Boolean) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val scroll = rememberScrollState()

    SheetFrame(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scroll)
        ) {
            Text(
                "Send an update link (beta)",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "${state.personName} opens the link and their app records the other side of " +
                    "these entries, so both ledgers agree.",
                style = MeraPaisaType.body,
                color = theme.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.lg))

            OutlinedTextField(
                value = state.senderName,
                onValueChange = onSenderNameChange,
                label = { Text("Your name") },
                supportingText = {
                    Text(
                        if (state.senderName.isBlank()) {
                            "Add your name to send the link. It's how they know who it's from."
                        } else {
                            "How you appear on their phone, so they know who the link is from."
                        },
                        style = MeraPaisaType.label
                    )
                },
                textStyle = MeraPaisaType.body,
                singleLine = true,
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )

            Spacer(Modifier.height(Spacing.lg))

            if (state.hasNothingToSend) {
                // The common confusing case, named plainly rather than left as a dead button.
                Text(
                    if (state.fullHistory) {
                        "There are no entries for ${state.personName} yet, so there is nothing " +
                            "to send. Record one and the link will have something to carry."
                    } else if (state.onlyTheirsAreNew) {
                        "Everything new here came from ${state.personName}'s own links, so there " +
                            "is nothing to send back. Turn on \"Send everything\" below to send " +
                            "their full history."
                    } else {
                        "Nothing new since you last sent ${state.personName} an update link. " +
                            "Turn on \"Send everything\" below to send their full history again."
                    },
                    style = MeraPaisaType.body,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            } else {
                Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    Text(
                        if (state.entryCount == 1) "1 entry" else "${state.entryCount} entries",
                        style = MeraPaisaType.sectionTitle,
                        color = theme.textPrimary
                    )
                    Text(
                        netSentence(state.netMinor, state.currency, state.personName),
                        style = MeraPaisaType.body,
                        // A sentence, not an amount, so it keeps the text ink.
                        color = theme.textPrimary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.fullHistory,
                        onValueChange = onFullHistoryChange,
                        role = Role.Switch
                    )
                    .heightIn(min = 48.dp)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Send everything", style = MeraPaisaType.bodyStrong, color = theme.textPrimary)
                    // Reworded for v2.5. The old line, "their app ignores anything it has already
                    // applied", described the dedupe-by-link behaviour and is no longer what
                    // happens: a full send is now compared against their ledger, and it is the
                    // only kind of link that can carry a correction or a deletion. Someone reading
                    // the old sentence had no way to know that the switch they were leaving alone
                    // was the one that makes edits travel.
                    Text(
                        "Their app compares it with what they already have, so corrections and " +
                            "deleted entries reach them. Without this, only new entries are sent.",
                        style = MeraPaisaType.label,
                        color = theme.textSecondary
                    )
                }
                Switch(checked = state.fullHistory, onCheckedChange = null)
            }
        }

        SheetFoot(above = scroll) {
            SecondaryAction("Don't send", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
            // Played out before the share sheet arrives over it: building the message closes
            // this one from the other end, and the two animating across each other read as a
            // flicker rather than as a handover.
            PrimaryAction(
                "Send the link",
                enabled = !state.busy && !state.hasNothingToSend && state.senderName.isNotBlank()
            ) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onShare() }
            }
        }
    }
}

/**
 * Says what the link means for the recipient, since they are the one who has to act on it. Phrasing
 * it from the sender's side would put the debt the wrong way round in the one place it is read
 * aloud.
 */
private fun netSentence(netMinor: Long, currency: String, personName: String): String = when {
    netMinor > 0 -> "$personName will record owing you ${amountString(netMinor, currency)}"
    netMinor < 0 -> "$personName will record you owing them ${amountString(-netMinor, currency)}"
    else -> "These cancel out, so they don't change what either of you owes"
}
