package com.kg.merapaisa.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.theme.Spacing

/**
 * A hairline between rows, inset to where the text begins.
 *
 * Full-bleed rules cut the avatar column in half and make the list look like a table. Starting the
 * line at the text is what tells the eye the rows belong to one column of names.
 */
@Composable
fun RowDivider() {
    val theme = LocalAppTheme.current
    HorizontalDivider(
        color = theme.outline,
        modifier = Modifier.padding(start = Spacing.xxl + Spacing.xl, end = Spacing.lg)
    )
}

