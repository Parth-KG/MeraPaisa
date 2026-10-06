package com.kg.merapaisa.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.AppTheme
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.netTotalsByCurrency
import com.kg.merapaisa.data.sidesByCurrency
import com.kg.merapaisa.getThemeByName
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.EmptyState
import com.kg.merapaisa.ui.NetPosition
import com.kg.merapaisa.ui.PersonRow
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.SecondaryAction
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.format.shrinkToFit
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.Tab as LedgerTab
import kotlinx.coroutines.launch

/**
 * Try a theme on your own ledger before you use it.
 *
 * The list it replaces showed each theme as a two-line swatch beside its name, and a name and two
 * stripes say little about what Kansa does to your balances. Here the top of the sheet is your
 * Balances screen, drawn by the screen's own parts in whichever theme you are trying, with your
 * own people in it, or the empty state if there is no one yet. A tap on a name changes only that
 * picture. The sheet stays in the theme you have, so the buttons under your thumb do not change
 * colour as you go, and nothing is used until you say so.
 *
 * Each name is drawn on its theme's own page in its own accent, dark themes first, then light, the
 * order the picker has always had. The one you are trying has a tick and a firmer edge, never only
 * a colour, because every chip here is a different colour already.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeSheet(
    inUse: AppTheme,
    people: List<PersonWithBalance>,
    onDismiss: () -> Unit,
    onUse: (String) -> Unit,
    startWith: String = inUse.name
) {
    val theme = LocalAppTheme.current
    // Survives turning the phone, like any choice half made.
    var triedName by rememberSaveable(inUse.name, startWith) { mutableStateOf(startWith) }
    val tried = getThemeByName(triedName)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    SheetFrame(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Try a theme",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.md))

            LedgerPreview(
                tried = tried,
                people = people,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.md))

            Column(
                modifier = Modifier.selectableGroup().padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                ThemeChips(themes.filter { it.isDark }, tried, inUse) { triedName = it }
                ThemeChips(themes.filterNot { it.isDark }, tried, inUse) { triedName = it }
            }
        }

        // The way out always names what you keep. The way forward appears once there is something
        // to use, and names it: a button that would change nothing is not offered.
        Row(
            modifier = Modifier.height(IntrinsicSize.Min)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryAction("Keep ${inUse.name}", enabled = true) { close(onDismiss) }
            if (tried.name != inUse.name) {
                PrimaryAction("Use ${tried.name}", enabled = true) { close { onUse(tried.name) } }
            }
        }
    }
}

/** One row of names, wrapping as whole chips. */
@Composable
private fun ThemeChips(
    group: List<AppTheme>,
    tried: AppTheme,
    inUse: AppTheme,
    onTry: (String) -> Unit
) {
    // Three to a row at most, so five names wrap three and two rather than four and one left
    // alone, whichever of them carries the tick.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        maxItemsInEachRow = 3
    ) {
        group.forEach { t ->
            ThemeChip(t, tried = t.name == tried.name, inUse = t.name == inUse.name) { onTry(t.name) }
        }
    }
}

/**
 * A theme's name on its own page in its own accent, so the chip is a sample of the theme as well
 * as a label for it. ThemeInkContrastTest holds every accent to 4.5:1 on its page for this.
 */
@Composable
private fun ThemeChip(t: AppTheme, tried: Boolean, inUse: Boolean, onTry: () -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(Shapes.small)
            .background(t.background)
            .border(if (tried) 2.dp else 1.dp, if (tried) theme.primary else theme.border, Shapes.small)
            .selectable(selected = tried, role = Role.RadioButton, onClick = onTry)
            .semantics(mergeDescendants = true) {
                contentDescription = if (inUse) "${t.name}, in use" else t.name
            }
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (tried) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = t.primary, modifier = Modifier.size(18.dp))
        }
        Text(
            t.name,
            style = MeraPaisaType.action,
            color = t.primary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

/**
 * The top of the Balances screen in [tried]: the tabs, the total and the first people, drawn by
 * the parts that draw the real screen, so what you see here is what you will get.
 *
 * It is a picture, not a second ledger. Taps stop at it, keyboard focus passes over it, and
 * TalkBack hears it as one line naming the theme, rather than a set of rows that do nothing.
 */
@Composable
private fun LedgerPreview(tried: AppTheme, people: List<PersonWithBalance>, modifier: Modifier = Modifier) {
    val edge = LocalAppTheme.current.border
    // One person at large type, where two filled the screen and pushed every theme below it.
    val peopleShown = if (LocalDensity.current.fontScale >= 1.5f) 1 else 2
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.medium)
            .border(1.dp, edge, Shapes.medium)
            .clearAndSetSemantics { contentDescription = "Your ledger in ${tried.name}" }
            .focusProperties { onEnter = { cancelFocusChange() } }
            .focusGroup()
    ) {
        MeraPaisaTheme(tried) {
            CompositionLocalProvider(LocalAppTheme provides tried) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tried.background)
                        .padding(bottom = Spacing.sm)
                ) {
                    PreviewTabs(tried)
                    Spacer(Modifier.height(Spacing.lg))
                    NetPosition(totals = netTotalsByCurrency(people), sides = sidesByCurrency(people))
                    Spacer(Modifier.height(Spacing.md))
                    if (people.isEmpty()) {
                        EmptyState(tab = LedgerTab.Active)
                    } else {
                        people.take(peopleShown).forEachIndexed { index, person ->
                            if (index > 0) RowDivider()
                            PersonRow(
                                person = person,
                                isSelected = false,
                                onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {},
                                onEditClick = {}, onSettleToggle = {}, onShareSummary = {},
                                onShareLedger = {}, onMoveDebt = {}
                            )
                        }
                    }
                }
            }
        }
        // Laid over the picture and taking every touch, so a row's long press cannot open its menu.
        Box(
            Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        )
    }
}

/** The Balances tabs as they look with Active open. Only a picture: the preview takes no taps. */
@Composable
private fun PreviewTabs(tried: AppTheme) {
    PrimaryTabRow(
        selectedTabIndex = 0,
        containerColor = tried.background,
        contentColor = tried.primary,
        divider = {},
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(0),
                width = Dp.Unspecified,
                color = tried.primary
            )
        }
    ) {
        LedgerTab.entries.forEach { tab ->
            Tab(
                selected = tab == LedgerTab.Active,
                onClick = {},
                selectedContentColor = tried.textPrimary,
                unselectedContentColor = tried.textSecondary,
                // Shrinking at large type, as the real tabs do, rather than cut to "Settle".
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
