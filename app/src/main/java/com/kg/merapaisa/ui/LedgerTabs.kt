package com.kg.merapaisa.ui

import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.format.shrinkToFit
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.walkthrough.TourId
import com.kg.merapaisa.ui.walkthrough.tourTarget

/**
 * Active, Even and Groups across the top of Balances.
 *
 * A real tab row. These were pill buttons, which look like three things you can press rather than
 * one place you are currently in, and gave no sense of which of the three you were looking at
 * beyond a fill colour.
 *
 * Shared by Balances itself, the theme picker's picture of it and the walkthrough, so all three
 * stay one drawing.
 */
@Composable
internal fun LedgerTabs(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    PrimaryTabRow(
        selectedTabIndex = Tab.entries.indexOf(selected),
        containerColor = theme.background,
        contentColor = theme.primary,
        modifier = modifier,
        divider = {},
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(Tab.entries.indexOf(selected)),
                width = Dp.Unspecified,
                color = theme.primary
            )
        }
    ) {
        Tab.entries.forEach { tab ->
            androidx.compose.material3.Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                modifier = if (tab == Tab.Even) Modifier.tourTarget(TourId.EvenTab) else Modifier,
                selectedContentColor = theme.textPrimary,
                unselectedContentColor = theme.textSecondary,
                // One line, shrinking at large type: at 1.5x the labels broke mid-word,
                // "Settle / d".
                text = {
                    Text(
                        tab.name,
                        style = MeraPaisaType.action,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = shrinkToFit(MeraPaisaType.action)
                    )
                }
            )
        }
    }
}
