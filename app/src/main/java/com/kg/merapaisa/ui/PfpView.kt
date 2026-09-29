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
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.avatarEmojiStyle
import com.kg.merapaisa.ui.theme.avatarInitialsStyle
import java.io.File

@Composable
fun PfpView(person: Person, size: Int) {
    val theme = LocalAppTheme.current
    // The stored colour is a choice of hue, not of luminance: no single value can be legible on
    // both Paper and Amoled. avatarInk keeps the hue somebody picked and gives it the lightness
    // this theme needs, which also rescues every avatar already in the ledger.
    val color = remember(person.pfpColor, theme.isDark) { avatarInk(person.pfpColor, theme.isDark) }
    // A circle, which is the one shape the design reserves for avatars. It was a square rounded
    // at 32 percent of its size: the same soft-tile family as everything else the redesign moved
    // away from, and so a face in the list was shaped like a button beside it.
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(Shapes.circle)
            .background(color.copy(alpha = AVATAR_WASH))
            .border(1.5.dp, color.copy(alpha = 0.35f), Shapes.circle),
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
            person.pfpType == "emoji" -> Text(person.pfpValue, style = avatarEmojiStyle(size), textAlign = TextAlign.Center)
            else -> Text(
                com.kg.merapaisa.data.initialsOf(person.name),
                style = avatarInitialsStyle(size),
                color = color,
                maxLines = 1
            )
        }
    }
}
