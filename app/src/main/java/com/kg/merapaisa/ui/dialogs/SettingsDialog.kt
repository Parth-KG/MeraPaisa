package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes

/**
 * The app's only settings surface. It began as a theme picker; the app lock needed somewhere
 * to live and a second one-off dialog would have been worse than one that says "Settings".
 */
@Composable
fun SettingsDialog(
    currentThemeName: String,
    appLockEnabled: Boolean,
    appLockAvailable: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val theme = LocalAppTheme.current
    var pendingTheme by remember { mutableStateOf(currentThemeName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "App lock",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            if (appLockAvailable) {
                                "Ask for fingerprint, face or device PIN"
                            } else {
                                "Set a screen lock on your phone to use this"
                            },
                            color = theme.textPrimary,
                            fontSize = 14.sp
                        )
                    }
                    Switch(
                        checked = appLockEnabled,
                        enabled = appLockAvailable,
                        onCheckedChange = onAppLockChange
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text("Theme", color = theme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                items(themes) { t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (pendingTheme == t.name) lerp(theme.card, t.primary, 0.22f) else theme.fill)
                            .border(1.dp, if (pendingTheme == t.name) t.primary.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { pendingTheme = t.name }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(t.name, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(t.background).border(1.dp, theme.outline, CircleShape))
                            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(t.positive))
                            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(t.negative))
                        }
                    }
                }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "made for fun ❤️ by parth • github: @Parth-KG",
                    fontSize = 11.sp,
                    color = theme.textSecondary.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApply(pendingTheme) }) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}
