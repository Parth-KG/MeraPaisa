package com.kg.merapaisa

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.compositionLocalOf

val LocalAppTheme = compositionLocalOf { themes[0] }

data class AppTheme(
    val name: String,
    val background: Color,
    val surface: Color,
    val card: Color,
    val primary: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val positive: Color,
    val negative: Color
)

val themes = listOf(
    AppTheme("Midnight", Color(0xFF0A0A0F), Color(0xFF111118), Color(0xFF16161F), Color(0xFF2ECC71), Color(0xFFE84B3A), Color.White, Color(0xFF555555), Color(0xFF2ECC71), Color(0xFFE84B3A)),
    AppTheme("Amoled", Color(0xFF000000), Color(0xFF0A0A0A), Color(0xFF111111), Color(0xFF00FF88), Color(0xFFFF3B3B), Color.White, Color(0xFF444444), Color(0xFF00FF88), Color(0xFFFF3B3B)),
    AppTheme("Slate", Color(0xFF0F1923), Color(0xFF1A2634), Color(0xFF1E2D3E), Color(0xFF4FC3F7), Color(0xFFEF5350), Color.White, Color(0xFF546E7A), Color(0xFF4FC3F7), Color(0xFFEF5350)),
    AppTheme("Charcoal", Color(0xFF1A1410), Color(0xFF241E18), Color(0xFF2C2520), Color(0xFFFFB74D), Color(0xFFEF5350), Color.White, Color(0xFF795548), Color(0xFFFFB74D), Color(0xFFEF5350)),
    AppTheme("Paper", Color(0xFFF5F5F0), Color(0xFFFFFFFF), Color(0xFFF0EDE8), Color(0xFF2E7D32), Color(0xFFC62828), Color(0xFF1A1A1A), Color(0xFF888888), Color(0xFF2E7D32), Color(0xFFC62828)),
    AppTheme("Cream", Color(0xFFFFF8F0), Color(0xFFFFFDF8), Color(0xFFF5EFE6), Color(0xFF6D4C41), Color(0xFFBF360C), Color(0xFF3E2723), Color(0xFF8D6E63), Color(0xFF6D4C41), Color(0xFFBF360C)),
    AppTheme("Ocean", Color(0xFF0A0F1A), Color(0xFF0F1929), Color(0xFF142030), Color(0xFF29B6F6), Color(0xFFFF7043), Color.White, Color(0xFF37474F), Color(0xFF29B6F6), Color(0xFFFF7043)),
    AppTheme("Sunset", Color(0xFF1A0F0A), Color(0xFF291509), Color(0xFF301A0E), Color(0xFFFF8A65), Color(0xFF42A5F5), Color.White, Color(0xFF5D4037), Color(0xFFFF8A65), Color(0xFF42A5F5)),
    AppTheme("Rose", Color(0xFF1A0A0F), Color(0xFF29101A), Color(0xFF30141F), Color(0xFFF48FB1), Color(0xFF80CBC4), Color.White, Color(0xFF880E4F), Color(0xFFF48FB1), Color(0xFF80CBC4)),
    AppTheme("Purple", Color(0xFF0F0A1A), Color(0xFF160F29), Color(0xFF1C1430), Color(0xFFCE93D8), Color(0xFF80CBC4), Color.White, Color(0xFF4A148C), Color(0xFFCE93D8), Color(0xFF80CBC4)),
    AppTheme("Neon", Color(0xFF000000), Color(0xFF0A0010), Color(0xFF100020), Color(0xFF00FF41), Color(0xFFFF00FF), Color(0xFF00FF41), Color(0xFF003B00), Color(0xFF00FF41), Color(0xFFFF00FF)),
    AppTheme("Gold", Color(0xFF0A0800), Color(0xFF1A1400), Color(0xFF221B00), Color(0xFFFFD700), Color(0xFFE84B3A), Color.White, Color(0xFF5D4E00), Color(0xFFFFD700), Color(0xFFE84B3A)),
    AppTheme("Vibrant", Color(0xFF0F0A1A), Color(0xFF1A0F2E), Color(0xFF22143D), Color(0xFFFF6D00), Color(0xFF7C4DFF), Color.White, Color(0xFF4A1A6B), Color(0xFFFF6D00), Color(0xFF7C4DFF))
)

fun getThemeByName(name: String) = themes.find { it.name == name } ?: themes[0]