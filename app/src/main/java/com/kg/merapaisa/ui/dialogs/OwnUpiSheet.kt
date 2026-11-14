package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.isUpiId
import com.kg.merapaisa.data.normaliseUpiId
import com.kg.merapaisa.data.upiReceiveLink
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.QrImage
import com.kg.merapaisa.ui.SecondaryAction
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Your own UPI ID: what friends pay when they scan your QR, from Show QR or a reminder.
 *
 * With an ID set, the sheet shows it as a QR with no amount, as a check of what friends will scan
 * (their UPI app asks them for the amount). Saving a new ID, or removing it, goes back to the caller,
 * which asks for the phone's fingerprint, face or screen lock before anything is written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnUpiSheet(
    current: String?,
    /** Your name as friends' UPI apps should show it, if you have given one. */
    yourName: String?,
    onDismiss: () -> Unit,
    /** The ID to save, or null to remove it. Only called when it differs from [current]. */
    onSave: (String?) -> Unit
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scroll = rememberScrollState()
    var typed by remember { mutableStateOf(current.orEmpty()) }
    val problem = upiIdProblem(typed)
    val wanted = typed.trim().takeIf { it.isNotEmpty() }?.let(::normaliseUpiId)
    val changed = wanted != current

    SheetFrame(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scroll)
        ) {
            Text(
                "Your UPI ID",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Friends pay this when they scan your QR, on your phone or in a reminder. " +
                    "Changing it asks for your fingerprint or screen lock.",
                style = MeraPaisaType.body,
                color = theme.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )

            if (current != null && isUpiId(current)) {
                Spacer(Modifier.height(Spacing.lg))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    QrImage(
                        text = upiReceiveLink(current, yourName),
                        description = "QR code for paying $current"
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "This is what friends scan. Their UPI app asks them for the amount.",
                        style = MeraPaisaType.label,
                        color = theme.textSecondary
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text("Your UPI ID") },
                placeholder = { Text("name@bank") },
                isError = problem != null,
                supportingText = problem?.let { { Text(it) } },
                textStyle = MeraPaisaType.body,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )
        }

        SheetFoot(above = scroll) {
            SecondaryAction("Keep as it is", enabled = true) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
            PrimaryAction(
                if (wanted == null && current != null) "Remove UPI ID" else "Save UPI ID",
                enabled = changed && problem == null
            ) { onSave(wanted) }
        }
    }
}

/**
 * What is wrong with your UPI ID as typed, in a sentence, or null when it is fine (or left empty,
 * which removes it). It catches typos, not a wrong but valid ID.
 */
internal fun upiIdProblem(value: String): String? = when {
    value.isBlank() -> null
    isUpiId(value) -> null
    else -> "That doesn't look like a UPI ID. It needs a name, an @ and the bank's handle, like yourname@okaxis."
}

/** The title and line the phone's prompt shows before your own UPI ID is written. */
internal fun ownUpiPrompt(current: String?, wanted: String?): Pair<String, String> = when {
    wanted == null -> "Remove your UPI ID" to "Reminders will go without your QR"
    current == null -> "Set your UPI ID" to "Friends who scan your QR will pay $wanted"
    else -> "Change your UPI ID" to "Friends who scan your QR will pay $wanted"
}
