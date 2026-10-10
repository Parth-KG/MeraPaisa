package com.kg.merapaisa.ui

import androidx.compose.foundation.layout.Column
import com.kg.merapaisa.ui.theme.Spacing
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.theme.Shapes

/**
 * Every bottom sheet in the app: the sheet shape, the theme's surface and the drag handle, decided
 * once, and two guards that Material's [ModalBottomSheet] does not have.
 *
 * When a fling reaches the end of a list, the sheet hands the speed that is left to its own settle,
 * which springs it back to the place it is already in, starting at that speed. The spring is
 * allowed to overshoot, so the whole sheet jumped as much as 300 px past its place and back, at
 * either end of the list. [KeepSheetStill] takes that leftover speed first.
 *
 * And the sheet pads its content for the status bar by however far it has moved down, so near the
 * top its height changes with its own position. A sheet about as tall as the screen (New group with
 * six people) kept restarting that spring as its resting place moved under it, and bounced until
 * the screen was touched. Laid out below the status bar, the sheet's height never depends on where
 * it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetFrame(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalAppTheme.current
    val keepStill = remember(sheetState) { KeepSheetStill(sheetState) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        shape = Shapes.sheet,
        containerColor = theme.surface,
        contentColor = theme.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.border) },
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().nestedScroll(keepStill), content = content)
    }
}

/**
 * A sheet's actions, side by side at its foot: the way out, then the way forward, each from
 * [SecondaryAction] and [PrimaryAction].
 *
 * Eleven sheets built this row by hand, each with Material's 24dp of padding inside the buttons,
 * which left a long label so little room that it wrapped where the shared buttons' 12dp does not.
 */
@Composable
internal fun SheetFoot(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min)
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Lets a list inside a sheet run out of room without moving the sheet.
 *
 * The speed left at the end of a fling is kept from the sheet, unless the sheet itself moved during
 * the gesture: dragging it down from the top of a list and letting go still closes it, as it always
 * has.
 */
@OptIn(ExperimentalMaterial3Api::class)
private class KeepSheetStill(private val sheetState: SheetState) : NestedScrollConnection {
    private var offsetWhenTouched: Float? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && offsetWhenTouched == null) {
            offsetWhenTouched = sheetOffset()
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val before = offsetWhenTouched
        offsetWhenTouched = null
        return if (before != null && sheetOffset() != before) Velocity.Zero else available
    }

    private fun sheetOffset(): Float? = runCatching { sheetState.requireOffset() }.getOrNull()
}
