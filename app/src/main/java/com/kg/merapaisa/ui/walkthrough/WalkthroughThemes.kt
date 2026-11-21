package com.kg.merapaisa.ui.walkthrough

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.AppTheme
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.partnerOf
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.Paragraph
import com.kg.merapaisa.ui.PersonRow
import com.kg.merapaisa.ui.coversLedger
import com.kg.merapaisa.ui.dialogs.miniatureOf
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing

/**
 * The ten themes as five rows: each dark theme beside the light one the home-screen widget pairs it
 * with by day (`partnerOf`), dark first, in the picker's order.
 */
internal fun themePairs(): List<Pair<AppTheme, AppTheme>> =
    themes.filter { it.isDark }.map { it to partnerOf(it) }

/**
 * The first page of a first run's walkthrough (Parth, 20 Nov 2026): every theme as a small preview
 * drawn in that theme, Asha's row with her amount, by the real `PersonRow`. A tap uses that theme at
 * once, saved as Settings saves it, so this page and every page after it change with it. The theme
 * in use is already chosen, so Next alone is fine, and choosing is never required.
 *
 * Ten previews at full width don't fit a page, so they come in the widget's pairs, two to a row.
 * Each preview is the row laid out at the window's width and drawn smaller, as the theme sheet's
 * picture is, so it wraps as the real screen does. TalkBack hears each as its theme's name, a
 * radio button, selected or not; the row inside is silent.
 */
@Composable
fun WalkthroughThemes(
    inUse: AppTheme,
    onChoose: (String) -> Unit,
    /** For the gallery, to photograph the foot of the page at large type. */
    scrolledToEnd: Boolean = false
) {
    val theme = LocalAppTheme.current
    val scroll = rememberScrollState()
    if (scrolledToEnd) LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(bottom = Spacing.lg)
    ) {
        Text(
            "Choose a theme",
            style = MeraPaisaType.screenTitle,
            color = theme.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .semantics { heading() }
        )
        Spacer(Modifier.height(Spacing.sm))
        Paragraph("Tap one to use it now, and the pages after this follow. Settings has Theme for later.")
        Spacer(Modifier.height(Spacing.sm))
        Paragraph("Each dark theme sits beside the light one the home-screen widget pairs it with.")
        Spacer(Modifier.height(Spacing.lg))
        Column(
            modifier = Modifier.selectableGroup().padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            themePairs().forEach { (dark, light) ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    ThemePreview(dark, chosen = dark.name == inUse.name, modifier = Modifier.weight(1f)) { onChoose(dark.name) }
                    ThemePreview(light, chosen = light.name == inUse.name, modifier = Modifier.weight(1f)) { onChoose(light.name) }
                }
            }
        }
    }
}

/**
 * One theme: its name in its own accent on its own page, as on the theme sheet's chips, over Asha's
 * row drawn in it. The chosen one has a tick and a firmer edge in the accent of the theme in use,
 * never only a colour, since every preview is a different colour already.
 */
@Composable
private fun ThemePreview(t: AppTheme, chosen: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val windowWidth = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    Column(
        modifier = modifier
            .clip(Shapes.medium)
            .background(t.background)
            .border(if (chosen) 2.dp else 1.dp, if (chosen) theme.primary else theme.border, Shapes.medium)
            .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = t.name }
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = Spacing.md)
                .clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (chosen) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = t.primary, modifier = Modifier.size(18.dp))
            }
            Text(t.name, style = MeraPaisaType.action, color = t.primary, maxLines = 1, softWrap = false)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { }
                .focusProperties { onEnter = { cancelFocusChange() } }
                .focusGroup()
        ) {
            MeraPaisaTheme(t) {
                CompositionLocalProvider(LocalAppTheme provides t) {
                    Box(Modifier.miniatureOf(windowWidth).background(t.background)) {
                        PersonRow(
                            person = WalkthroughSamples.asha,
                            isSelected = false,
                            onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {},
                            onEditClick = {}, onSettleUp = {}, onShareSummary = {}, onShareLedger = {},
                            onMoveDebt = {}
                        )
                    }
                }
            }
            // Over the row and taking its touches without consuming them, so a tap anywhere on
            // the preview chooses the theme rather than reaching the row.
            Box(Modifier.matchParentSize().coversLedger())
        }
    }
}
