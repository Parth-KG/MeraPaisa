package com.kg.merapaisa.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
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
import com.kg.merapaisa.CurrencyStore

@Composable
fun AddPersonDialog(onDismiss: () -> Unit, onAdd: (String, String, String, String, String) -> Unit) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    var photoPath by remember { mutableStateOf<String?>(null) }
    var pfpType by remember { mutableStateOf("initials") }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            // Downscaled and written off the main thread; the old file is replaced.
            saveProfilePhoto(context, uri, photoPath)?.let {
                photoPath = it
                pfpType = "photo"
            }
        }
    }
    val lastCurrency by CurrencyStore.getLastCurrency(context).collectAsState(initial = "INR")
    var selectedCurrency by remember { mutableStateOf("INR") }
    LaunchedEffect(lastCurrency) { selectedCurrency = lastCurrency }
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("😊") }
    var selectedColor by remember { mutableStateOf("#4CAF50") }



    val colors = listOf("#E84B3A","#3A8FE8","#2ECC71","#F39C12","#9B59B6","#E91E63","#00BCD4","#FF5722")

    val dismiss = {
        // Nothing was saved, so the picked file has no owner.
        deleteProfilePhoto(context, photoPath)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Add Person", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
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

                // PFP type selector
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
                            onClick = { selectedCurrency = c },
                            label = { Text(currencySymbol(c), fontSize = 14.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val pfpValue = when (pfpType) {
                            "emoji" -> emoji
                            "photo" -> photoPath ?: ""
                            else -> name.take(2)
                        }
                        onAdd(name, pfpType, pfpValue, selectedColor,selectedCurrency)
                    }
                }
            ) { Text("Add", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text("Cancel", color = theme.textSecondary) }
        }
    )
}
