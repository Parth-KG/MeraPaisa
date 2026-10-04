package com.kg.merapaisa.ui.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.AppTheme
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.TextRowInset
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.coversLedger
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import com.kg.merapaisa.ui.theme.Motion

/**
 * Everything about the app itself rather than about who owes whom.
 *
 * It was an AlertDialog, and it had outgrown one badly: a switch, three places to go, an export,
 * every theme and a credit line, stacked inside a box sized for a question and scrolling against
 * its own buttons. A dialog is for a decision. This is a screen now, built like the group screen:
 * a back arrow, the title in the gutter, sections of rows on the background, and one named button
 * at the foot.
 *
 * The theme list shows each theme rather than naming it. A name tells you nothing about what
 * Macchiato does to your ledger, so every row carries a small piece of that theme drawn in its own
 * background, its own text ink and its own accent, which is what changing it will actually look
 * like. It stays folded into one row until asked for, because most visits are not about the theme.
 */
@Composable
fun SettingsScreen(
    currentThemeName: String,
    appLockEnabled: Boolean,
    appLockAvailable: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    onImportLink: () -> Unit,
    onBackupRestore: () -> Unit,
    onExportCsv: () -> Unit,
    canExport: Boolean,
    onCheckUpdates: () -> Unit,
    appVersion: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    startWithThemesOpen: Boolean = false
) {
    val theme = LocalAppTheme.current
    // Folded on every visit and stored nowhere, but turning the phone keeps it open.
    var themesOpen by rememberSaveable { mutableStateOf(startWithThemesOpen) }
    // A pick survives turning the phone too. Closing the list drops it, so nothing waits unseen.
    var pendingTheme by rememberSaveable { mutableStateOf(currentThemeName) }
    val listState = rememberLazyListState()

    // The themes sit near the foot, so opening them scrolls just far enough to show the last one.
    LaunchedEffect(themesOpen) {
        if (!themesOpen) return@LaunchedEffect
        val lastKey = themeKey(themes.last())
        repeat(20) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.firstOrNull { it.key == lastKey }
            val visibleEnd = info.viewportEndOffset - info.afterContentPadding
            val needed = if (last == null) info.viewportSize.height / 2f
            else (last.offset + last.size - visibleEnd).toFloat()
            if (last != null && needed <= 0f) return@LaunchedEffect
            listState.animateScrollBy(needed, tween(Motion.slow, easing = Motion.emphasized))
        }
    }

    // This is part of the main tree rather than a Dialog window, so back has to be caught here.
    // Left alone it falls through to the people list underneath and closes the app.
    BackHandler(enabled = true) { onDismiss() }

    Column(modifier = Modifier.fillMaxSize().background(theme.background).coversLedger()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.textPrimary
                )
            }
        }

        Text(
            "Settings",
            style = MeraPaisaType.screenTitle,
            color = theme.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = Spacing.lg)
        )

        // With the button gone, the list itself has to clear the navigation bar.
        val clearOfBar = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(
                bottom = Spacing.lg + if (themesOpen) 0.dp else clearOfBar.calculateBottomPadding()
            )
        ) {
            item { SectionHeading("App lock") }
            item {
                AppLockRow(
                    enabled = appLockEnabled,
                    available = appLockAvailable,
                    onChange = onAppLockChange
                )
            }

            item { SectionHeading("Your ledger") }
            item {
                // An action rather than a setting, so it acts at once and leaves the screen
                // instead of waiting for the button at the foot. Until assetlinks.json is live at
                // the domain root, a tapped link does not reach the app, which makes this the
                // only way one can get in.
                ActionRow(title = "Record an update link", onClick = onImportLink)
            }
            item { RowDivider(TextRowInset) }
            item {
                // Moved here from the main screen's top bar, where it was an unlabelled share
                // icon beside Settings. Writing a CSV is a filing job that belongs with backup,
                // not something to reach for while reading a balance. Disabled rather than hidden
                // when there is nothing to write, so it does not appear and vanish.
                ActionRow(
                    title = "Export as CSV",
                    subtitle = if (canExport) null else "Nothing to export yet. Add a person first.",
                    enabled = canExport,
                    onClick = onExportCsv
                )
            }
            item { RowDivider(TextRowInset) }
            item {
                ActionRow(title = "Back up and restore", onClick = onBackupRestore)
            }
            item {
                // The manifest allows Android's own backup, so the database leaves the phone
                // whether or not anybody saves a file. Better said here, plainly, than discovered
                // on a new phone: it is the system's feature, and this app has no account of its
                // own to confuse it with.
                Text(
                    "Android's own backup also copies your ledger to your Google account. " +
                        "Mera Paisa has no account or server of its own.",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                )
            }

            item { SectionHeading("This app") }
            item {
                ActionRow(
                    title = "Check for updates",
                    subtitle = "You're on $appVersion.",
                    onClick = onCheckUpdates
                )
            }

            item(key = "theme-summary") {
                ThemeSummaryRow(
                    inUse = theme,
                    open = themesOpen,
                    onToggle = {
                        if (themesOpen) pendingTheme = currentThemeName
                        themesOpen = !themesOpen
                    }
                )
            }
            if (themesOpen) {
                itemsIndexed(themes, key = { _, t -> themeKey(t) }) { index, t ->
                    Column(Modifier.animateItem(fadeInSpec = Appear, placementSpec = Move, fadeOutSpec = Leave)) {
                        if (index > 0) RowDivider(TextRowInset)
                        ThemeRow(
                            preview = t,
                            selected = pendingTheme == t.name,
                            inUse = currentThemeName == t.name,
                            onSelect = { pendingTheme = t.name }
                        )
                    }
                }
            }

            item(key = "credit") {
                Text(
                    "Made for fun by Parth, @Parth-KG on GitHub.",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .animateItem(fadeInSpec = null, placementSpec = Move, fadeOutSpec = null)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .padding(top = Spacing.xxl)
                )
            }
        }

        // One button, and it names the one thing this screen holds back until you ask. Everything
        // else acts on the tap. It is dead until you pick a different theme, the way Settle up is
        // dead when everyone is already even, because a live button that would change nothing is
        // a lie about what tapping it does. And it is there only while the themes are: with them
        // folded away, a dead button at the foot of every visit would be one for something unseen.
        AnimatedVisibility(
            visible = themesOpen,
            enter = slideInVertically(tween(Motion.slow, easing = Motion.emphasizedDecelerate)) { it } +
                fadeIn(tween(Motion.slow)),
            exit = slideOutVertically(tween(Motion.medium, easing = Motion.emphasizedAccelerate)) { it } +
                fadeOut(tween(Motion.medium))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    )
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            ) {
                Button(
                    onClick = { onApply(pendingTheme) },
                    enabled = pendingTheme != currentThemeName,
                    shape = Shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.background
                    )
                ) {
                    Text("Use this theme", style = MeraPaisaType.action)
                }
            }
        }
    }
}

/** A heading inside the list. Sentence case, quiet, with air above it and none below. */
@Composable
private fun SectionHeading(text: String) {
    val theme = LocalAppTheme.current
    Text(
        text,
        style = MeraPaisaType.sectionTitle,
        color = theme.textSecondary,
        modifier = Modifier.padding(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.xl,
            bottom = Spacing.sm
        )
    )
}

/**
 * The lock, and what to do when the phone cannot offer one.
 *
 * The whole row is the switch, not just the switch itself, because a 32dp control at the far edge
 * of a phone is the hardest thing on the screen to hit. When there is no screen lock set the row
 * stops being toggleable rather than silently doing nothing, and the sentence says what to set.
 */
@Composable
private fun AppLockRow(enabled: Boolean, available: Boolean, onChange: (Boolean) -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (available) {
                    Modifier.toggleable(value = enabled, onValueChange = onChange, role = Role.Switch)
                } else {
                    Modifier
                }
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Lock the app",
                style = MeraPaisaType.bodyStrong,
                color = if (available) theme.textPrimary else theme.textSecondary
            )
            // Only when it cannot be turned on: then the row needs to say why, and what to set.
            if (!available) {
                Text(
                    "Your phone has no screen lock, so there is nothing to ask for. Set one in " +
                        "Android settings and this can be turned on.",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary
                )
            }
        }
        // The row owns the toggle, so the switch itself takes no callback: with both live, a tap
        // on the switch would be handled twice.
        Switch(checked = enabled, enabled = available, onCheckedChange = null)
    }
}

/**
 * A place to go or a thing to do, written as a row on the background like every other list in the
 * app. Disabled states keep the row where it was and grey the words, so nothing shifts under the
 * thumb when the ledger fills up.
 */
@Composable
private fun ActionRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MeraPaisaType.bodyStrong,
                color = if (enabled) theme.textPrimary else theme.textSecondary
            )
            if (subtitle != null) Text(subtitle, style = MeraPaisaType.label, color = theme.textSecondary)
        }
    }
}

/**
 * The theme in use, folded: its name, a swatch of it, and a chevron that turns as the list opens.
 * The whole row toggles, and TalkBack hears one button, "Theme, Midnight", that says whether the
 * list is open.
 */
@Composable
private fun ThemeSummaryRow(inUse: AppTheme, open: Boolean, onToggle: () -> Unit) {
    val theme = LocalAppTheme.current
    val turn by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(Motion.medium, easing = Motion.emphasized),
        label = "chevron"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The air a section heading has above it, outside what the tap covers.
            .padding(top = Spacing.md)
            .clickable(
                onClickLabel = if (open) "Hide themes" else "Show themes",
                role = Role.Button,
                onClick = onToggle
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .semantics(mergeDescendants = true) {
                contentDescription = "Theme, ${inUse.name}"
                stateDescription = if (open) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            "Theme",
            style = MeraPaisaType.bodyStrong,
            color = theme.textPrimary,
            modifier = Modifier.weight(1f).clearAndSetSemantics { }
        )
        ThemeSwatch(inUse)
        Text(
            inUse.name,
            style = MeraPaisaType.body,
            color = theme.textSecondary,
            modifier = Modifier.clearAndSetSemantics { }
        )
        Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = theme.textSecondary,
            modifier = Modifier.rotate(turn)
        )
    }
}

private fun themeKey(theme: AppTheme) = "theme-${theme.name}"

private val Appear = tween<Float>(Motion.medium, easing = Motion.emphasizedDecelerate)
private val Leave = tween<Float>(Motion.quick, easing = Motion.emphasizedAccelerate)
private val Move = tween<IntOffset>(Motion.medium, easing = Motion.emphasized)

/**
 * One theme, shown rather than named.
 *
 * Selection is a wash of fill and a tick, not the accent alone: the row is the one place in the
 * app where every theme's accent sits under the next, and picking the selected one out by colour
 * would be exactly the reading a colour-blind user cannot do.
 */
@Composable
private fun ThemeRow(
    preview: AppTheme,
    selected: Boolean,
    inUse: Boolean,
    onSelect: () -> Unit
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) theme.fillStrong else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        ThemeSwatch(preview)
        Column(modifier = Modifier.weight(1f)) {
            Text(preview.name, style = MeraPaisaType.bodyStrong, color = theme.textPrimary)
            if (inUse) {
                Text("in use", style = MeraPaisaType.label, color = theme.textSecondary)
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = theme.primary)
        }
    }
}

/**
 * A theme in miniature: its background, a line of its text ink, a shorter line of its accent.
 *
 * Three dots of positive, negative and background is what was here before, and it showed the two
 * amount inks, which are the colours a theme changes least visibly. What you actually notice when
 * you switch is the paper and the ink on it, so those are what the swatch draws, in the same
 * order they appear on a real row.
 */
@Composable
private fun ThemeSwatch(preview: AppTheme) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier
            .size(width = 44.dp, height = 36.dp)
            .clip(Shapes.small)
            .background(preview.background)
            .border(1.dp, theme.outline, Shapes.small)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(preview.textPrimary))
        Box(Modifier.fillMaxWidth(0.55f).height(4.dp).background(preview.primary))
    }
}
