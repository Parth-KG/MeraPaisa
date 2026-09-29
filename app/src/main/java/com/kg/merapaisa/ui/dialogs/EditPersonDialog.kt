package com.kg.merapaisa.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.deleteProfilePhoto
import com.kg.merapaisa.saveProfilePhoto
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight

/**
 * The same form as adding somebody, filled in, plus the one thing only an existing person can do:
 * change the currency their balance is kept in.
 *
 * That change is the reason this sheet keeps a dialog inside it. Everything else here is editing a
 * field, which a sheet is for; converting rewrites every entry this person has and cannot be taken
 * back, which is a decision, and a decision is what an AlertDialog is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPersonDialog(
    person: Person,
    converting: Boolean,
    conversionError: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Boolean) -> Unit
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(person.name) }
    var pfpType by remember { mutableStateOf(person.pfpType) }
    var emoji by remember { mutableStateOf(if (person.pfpType == "emoji") person.pfpValue else "") }
    var selectedColour by remember { mutableStateOf(person.pfpColor) }
    var photoPath by remember { mutableStateOf(person.pfpValue.takeIf { person.pfpType == "photo" }) }

    var selectedCurrency by remember { mutableStateOf(person.currency) }
    var shouldConvert by remember { mutableStateOf(false) }
    var pendingCurrency by remember { mutableStateOf("") }
    var showConvertAlert by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            // Replace an earlier pick from this same sheet, but leave the saved photo alone until
            // the edit is actually committed.
            val replaceable = photoPath.takeIf { it != person.pfpValue }
            saveProfilePhoto(context, uri, replaceable)?.let {
                photoPath = it
                pfpType = "photo"
            }
        }
    }

    // A rate is being fetched, and the save is waiting on it. Swiping the sheet away mid-fetch
    // would leave the conversion running with nothing to report back to, so the sheet refuses to
    // move until it lands.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !converting }
    )

    val dismiss = {
        deleteProfilePhoto(context, photoPath.takeIf { it != person.pfpValue })
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = { if (!converting) dismiss() },
        sheetState = sheetState,
        shape = Shapes.sheet,
        containerColor = theme.surface,
        contentColor = theme.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Edit person",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.lg))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                textStyle = MeraPaisaType.body,
                singleLine = true,
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )

            AvatarPicker(
                pfpType = pfpType,
                onTypeChange = { pfpType = it },
                emoji = emoji,
                onEmojiChange = { emoji = it },
                hasPhoto = photoPath != null,
                selectedColour = selectedColour,
                onColourChange = { selectedColour = it },
                onPickPhoto = {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )

            SheetHeading("Currency")
            CurrencyChips(
                selected = selectedCurrency,
                enabled = !converting,
                onSelect = { code ->
                    when {
                        code == selectedCurrency -> Unit
                        // Back to the currency the balance is already stored in: there is nothing
                        // to convert, and savePersonEdit skips the conversion anyway because the
                        // currency has not changed.
                        code == person.currency -> {
                            shouldConvert = false
                            selectedCurrency = code
                        }
                        else -> {
                            pendingCurrency = code
                            showConvertAlert = true
                        }
                    }
                }
            )

            if (selectedCurrency != person.currency) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    if (shouldConvert)
                        "Every entry will be converted from ${person.currency} to " +
                            "$selectedCurrency at today's rate when you save."
                    else
                        "The amounts stay as they are and only the currency is relabelled to " +
                            "$selectedCurrency when you save.",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }

            if (conversionError != null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    conversionError,
                    style = MeraPaisaType.label,
                    color = theme.negative,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }
        }

        Row(
            modifier = Modifier.height(IntrinsicSize.Min)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { dismiss() } },
                enabled = !converting,
                shape = Shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
                border = BorderStroke(1.dp, theme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textPrimary)
            ) {
                Text("Keep as it was", style = MeraPaisaType.action)
            }
            Button(
                onClick = {
                    val personName = name.trim()
                    val type = avatarTypeFor(pfpType, emoji, photoPath)
                    val value = avatarValueFor(type, personName, emoji, photoPath)
                    // Saved without closing the sheet first: a conversion can come back without a
                    // rate, and the sentence saying so has to arrive somewhere the user can still
                    // see it.
                    onSave(personName, type, value, selectedColour, selectedCurrency, shouldConvert)
                },
                enabled = !converting && name.isNotBlank(),
                shape = Shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.background
                )
            ) {
                if (converting) {
                    // The save is held until the rate resolves, so it cannot relabel the currency
                    // while leaving the amount in the old one.
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = theme.background
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Converting", style = MeraPaisaType.action)
                } else {
                    Text("Save person", style = MeraPaisaType.action)
                }
            }
        }
    }

    if (showConvertAlert) {
        ConvertCurrencyDialog(
            personName = person.name,
            from = person.currency,
            to = pendingCurrency,
            onConvert = {
                shouldConvert = true
                selectedCurrency = pendingCurrency
                showConvertAlert = false
            },
            onRelabel = {
                shouldConvert = false
                selectedCurrency = pendingCurrency
                showConvertAlert = false
            },
            onDismiss = { showConvertAlert = false }
        )
    }
}

/**
 * The one decision in this sheet, so the one thing here that stays a dialog.
 *
 * Until v2.3 converting wrote a single correcting entry, which left a log of old-currency amounts
 * with an adjustment tacked on the end. It now rewrites every entry, which is far clearer to read
 * and impossible to undo, so both halves of that belong in the sentence asking for confirmation.
 *
 * [from] is the currency the balance is stored in rather than whatever is selected in the sheet,
 * because that is what the save converts from. Naming the selected one meant that changing
 * currency twice without saving described a conversion that was never going to happen.
 */
@Composable
internal fun ConvertCurrencyDialog(
    personName: String,
    from: String,
    to: String,
    onConvert: () -> Unit,
    onRelabel: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Shapes.medium,
        containerColor = theme.card,
        title = {
            Text(
                "Convert $personName's entries to $to?",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Every entry is rewritten from $from to $to at today's rate, so " +
                        "$personName's whole history reads in $to.",
                    style = MeraPaisaType.body,
                    color = theme.textSecondary
                )
                Text(
                    "This can't be undone. The original $from amounts are replaced.",
                    style = MeraPaisaType.label,
                    color = theme.negative
                )
                Text(
                    "Group expenses aren't converted, so if $personName is in a group, their " +
                        "balance will mix $to entries with unconverted group amounts.",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConvert,
                shape = Shapes.small,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.background
                )
            ) {
                Text("Convert the entries", style = MeraPaisaType.action)
            }
        },
        dismissButton = {
            // The quieter way through, and the one that changes nothing you cannot change back.
            Button(
                onClick = onRelabel,
                shape = Shapes.small,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.fillStrong,
                    contentColor = theme.textPrimary
                )
            ) {
                Text("Keep the amounts", style = MeraPaisaType.action)
            }
        }
    )
}
