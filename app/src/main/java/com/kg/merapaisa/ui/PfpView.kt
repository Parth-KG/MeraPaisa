package com.kg.merapaisa.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.Person
import java.io.File

@Composable
fun PfpView(person: Person, size: Int) {
    val theme = LocalAppTheme.current
    val color = remember(person.pfpColor, theme.positive) {
        try { Color(android.graphics.Color.parseColor(person.pfpColor)) } catch (e: Exception) { theme.positive }
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.32f).dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape((size * 0.32f).dp)),
        contentAlignment = Alignment.Center
    ) {
        val photo = remember(person.pfpValue, person.pfpType) {
            File(person.pfpValue).takeIf { person.pfpType == "photo" && it.isFile }
        }
        when {
            photo != null -> AsyncImage(
                model = photo,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            person.pfpType == "emoji" -> Text(person.pfpValue, fontSize = (size * 0.45f).sp, textAlign = TextAlign.Center)
            else -> Text(
                person.name.take(2).uppercase(),
                fontSize = (size * 0.35f).sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
