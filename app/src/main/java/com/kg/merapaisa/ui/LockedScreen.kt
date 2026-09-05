package com.kg.merapaisa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme

/**
 * What is on screen while the app is locked. Deliberately shows nothing of the ledger — not
 * a name, not a balance — because this view is also what appears behind the system prompt.
 */
@Composable
fun LockedScreen(onUnlock: () -> Unit) {
    val theme = LocalAppTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = theme.textSecondary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text("Mera Paisa is locked", color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Unlock with your fingerprint, face or device PIN.",
            color = theme.textSecondary,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(onClick = onUnlock, shape = RoundedCornerShape(14.dp)) {
            Text("Unlock", fontWeight = FontWeight.SemiBold)
        }
    }
}
