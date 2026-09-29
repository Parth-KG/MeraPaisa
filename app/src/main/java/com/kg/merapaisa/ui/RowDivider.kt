package com.kg.merapaisa.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.theme.Spacing

/**
 * A hairline between rows, inset to where the text begins.
 *
 * Full-bleed rules cut the avatar column in half and make the list look like a table. Starting the
 * line at the text is what tells the eye the rows belong to one column of names.
 *
 * The default clears an avatar, a radio button or a checkbox. A list whose rows start with text
 * passes [TextRowInset], or the line starts well to the right of every word it separates.
 */
@Composable
fun RowDivider(start: Dp = Spacing.xxl + Spacing.xl) {
    val theme = LocalAppTheme.current
    HorizontalDivider(
        color = theme.outline,
        modifier = Modifier.padding(start = start, end = Spacing.lg)
    )
}

/** Where the text starts in a row with nothing in front of it: the screen gutter. */
val TextRowInset: Dp = Spacing.lg

