package com.kg.merapaisa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.kg.merapaisa.AppTheme
import com.kg.merapaisa.toColorScheme

/**
 * Drives Material from the user's chosen palette. There is no dynamic colour and no template
 * purple scheme any more, so an unstyled AlertDialog or Switch already looks right. A FilterChip
 * takes choiceChipColors and choiceChipBorder, because Material gives a chosen chip no edge.
 */
@Composable
fun MeraPaisaTheme(
    theme: AppTheme,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = theme.toColorScheme(),
        typography = Typography,
        content = content
    )
}
