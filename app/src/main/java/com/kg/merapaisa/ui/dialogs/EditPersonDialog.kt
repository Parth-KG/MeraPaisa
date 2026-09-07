package com.kg.merapaisa.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.SUPPORTED_CURRENCIES
import com.kg.merapaisa.data.currencySymbol
import com.kg.merapaisa.deleteProfilePhoto
import com.kg.merapaisa.saveProfilePhoto
import kotlinx.coroutines.launch

@Composable
fun EditPersonDialog(
    person: Person,
    converting: Boolean,
    conversionError: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(person.name) }
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    var shouldConvert by remember { mutableStateOf(false) }
    var selectedCurrency by remember { mutableStateOf(person.currency) }
    var pfpType by remember { mutableStateOf(person.pfpType) }
    var emoji by remember { mutableStateOf(if (person.pfpType == "emoji") person.pfpValue else "😊") }
    var selectedColor by remember { mutableStateOf(person.pfpColor) }
    var photoPath by remember { mutableStateOf(person.pfpValue.takeIf { person.pfpType == "photo" }) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            // Replace an earlier pick from this same dialog, but leave the saved photo
            // alone until the edit is actually committed.
            val replaceable = photoPath.takeIf { it != person.pfpValue }
            saveProfilePhoto(context, uri, replaceable)?.let {
                photoPath = it
                pfpType = "photo"
            }
        }
    }

    val colors = listOf("#E84B3A","#3A8FE8","#2ECC71","#F39C12","#9B59B6","#E91E63","#00BCD4","#FF5722")
    var showConvertAlert by remember { mutableStateOf(false) }
    var pendingCurrency by remember { mutableStateOf("") }

    val dismiss = {
        deleteProfilePhoto(context, photoPath.takeIf { it != person.pfpValue })
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!converting) dismiss() },
        title = { Text("Edit Person", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = theme.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Profile Picture", color = theme.textSecondary, fontSize = 13.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("initials","emoji","photo").forEach { type ->
                        FilterChip(
                            selected = pfpType == type,
                            onClick = {
                                // Only switch to "photo" once one is actually saved — cancelling
                                // the picker used to leave an avatar with no image and no fallback.
                                if (type == "photo") {
                                    launcher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } else {
                                    pfpType = type
                                }
                            },
                            label = { Text(type.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                        )
                    }
                }

                if (pfpType == "emoji") {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Emoji", color = theme.textSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (pfpType == "initials") {
                    Text("Color", color = theme.textSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(c)))
                                    .border(if (selectedColor == c) 2.dp else 0.dp, theme.textPrimary, CircleShape)
                                    .clickable { selectedColor = c }
                            )
                        }
                    }
                }
                Text("Currency", color = theme.textSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUPPORTED_CURRENCIES.forEach { c ->
                        FilterChip(
                            selected = selectedCurrency == c,
                            enabled = !converting,
                            onClick = {
                                when {
                                    c == selectedCurrency -> Unit
                                    // Back to the currency the balance is already stored in:
                                    // there is nothing to convert, and savePersonEdit skips the
                                    // conversion anyway because the currency has not changed.
                                    c == person.currency -> {
                                        shouldConvert = false
                                        selectedCurrency = c
                                    }
                                    else -> {
                                        pendingCurrency = c
                                        showConvertAlert = true
                                    }
                                }
                            },
                            label = { Text(currencySymbol(c), fontSize = 14.sp) }
                        )
                    }
                }

                if (shouldConvert && selectedCurrency != person.currency) {
                    Text(
                        "Balance will be converted from ${currencySymbol(person.currency)} " +
                            "to ${currencySymbol(selectedCurrency)} at today's rate when you save.",
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                }

                if (conversionError != null) {
                    Text(conversionError, color = theme.negative, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val pfpValue = when (pfpType) {
                            "emoji" -> emoji
                            "photo" -> photoPath ?: person.pfpValue
                            else -> name.take(2)
                        }
                        onSave(name, pfpType, pfpValue, selectedColor, selectedCurrency,shouldConvert)
                    }
                },
                enabled = !converting && name.isNotBlank()
            ) {
                if (converting) {
                    // The save is held until the rate resolves, so it cannot relabel
                    // the currency while leaving the amount in the old one.
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = theme.background
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Converting", fontWeight = FontWeight.Bold)
                } else {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss, enabled = !converting) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
    if (showConvertAlert) {
        AlertDialog(
            onDismissRequest = { showConvertAlert = false },
                title = { Text("Change Currency", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            // person.currency, not selectedCurrency: savePersonEdit converts from the currency
            // the balance is stored in. Naming the selected one meant that changing currency
            // twice without saving described a conversion that was never going to happen.
            text = { Text("Convert balance from ${person.currency} to $pendingCurrency using live rates, or keep amount as-is?", color = theme.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        shouldConvert = true
                        selectedCurrency = pendingCurrency
                        showConvertAlert = false
                    }
                    ) { Text("Convert") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        shouldConvert = false
                        selectedCurrency = pendingCurrency
                        showConvertAlert = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.fillStrong)
                ) { Text("Keep as-is", color = theme.textPrimary) }
            }
        )
    }
}
