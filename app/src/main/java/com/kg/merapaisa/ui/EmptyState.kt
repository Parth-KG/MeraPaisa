package com.kg.merapaisa.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import kotlinx.coroutines.launch

/** First launch used to be two tab buttons above nothing at all. */
@Composable
fun EmptyState(tab: Tab, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (tab == Tab.Active) "No one here yet" else "Nothing settled yet",
            color = theme.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (tab == Tab.Active) {
                "Tap the + button in the bottom-left corner to add someone you split money with."
            } else {
                "Debts you settle up will be kept here."
            },
            color = theme.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}
