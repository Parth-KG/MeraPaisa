package com.kg.merapaisa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.TextStyle

/**
 * A full screen switched in by UI state: a back arrow, a title in the gutter, a list, and the
 * actions named at the foot.
 *
 * Backup and update links each carried an identical private copy of this, and the update screen
 * would have been a third. One copy means the insets, the arrow and the title are decided once.
 *
 * [onBack] is null while work is running that leaving would not stop: a file being written, a
 * link being recorded, an update being fetched. The arrow is not drawn and the system back is
 * swallowed, rather than hiding the screen while its result reappears on its own a moment later.
 */
@Composable
internal fun ScreenFrame(
    title: String,
    onBack: (() -> Unit)?,
    footer: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val theme = LocalAppTheme.current
    val listState = rememberLazyListState()

    BackHandler(enabled = true) { onBack?.invoke() }

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
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = theme.textPrimary
                    )
                }
            } else {
                // Keeps the title where it sits on every other step, so the screen does not jump
                // up by a row the moment the work starts.
                Spacer(Modifier.height(48.dp))
            }
        }

        Text(
            title,
            style = MeraPaisaType.screenTitle,
            color = theme.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = Spacing.lg)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.lg),
            content = content
        )

        if (footer != null) {
            FootEdge(visible = listState.canScrollForward)
            footer()
        }
    }
}

/**
 * The line along the top of a foot while the content above runs on beneath it.
 *
 * The buttons sat on the same colour as the list, so a list cut off at the foot looked finished: a
 * row half hidden under the buttons read as the last one. The line is drawn only while there is
 * more to scroll to, and is transparent rather than gone otherwise, so nothing moves by a pixel when
 * it appears.
 */
@Composable
internal fun FootEdge(visible: Boolean) {
    val theme = LocalAppTheme.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(if (visible) theme.divider else Color.Transparent)
    )
}

/** The actions, side by side at the foot, clear of the navigation bar. */
@Composable
internal fun FootActions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min)
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * What the screen is for, in the accent.
 *
 * [busyLabel] holds the button while its work runs, with a spinner and the words for what is
 * happening. Held is not unavailable: the spinner and the words are the only sign of the work, so
 * they keep the button's colours rather than fading to a disabled grey nobody can read.
 */
@Composable
internal fun RowScope.PrimaryAction(
    label: String,
    enabled: Boolean,
    busyLabel: String? = null,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val busy = busyLabel != null
    val standard = ButtonDefaults.buttonColors()
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = Shapes.small,
        modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
        contentPadding = FootButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = theme.primary,
            contentColor = theme.onAccent,
            disabledContainerColor = if (busy) theme.primary else standard.disabledContainerColor,
            disabledContentColor = if (busy) theme.onAccent else standard.disabledContentColor
        )
    ) {
        if (busyLabel != null) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = theme.onAccent
            )
            Spacer(Modifier.width(Spacing.sm))
            ButtonLabel(busyLabel)
        } else {
            ButtonLabel(label)
        }
    }
}

/**
 * The way out, outlined so it does not compete with the decision beside it.
 *
 * [fill] is for a button that floats over a list, as Balances' Split does: filled with the page,
 * a row scrolled beneath it does not show through the outline.
 */
@Composable
internal fun RowScope.SecondaryAction(
    label: String,
    enabled: Boolean,
    fill: Color = Color.Transparent,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = Shapes.small,
        modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
        contentPadding = FootButtonPadding,
        border = BorderStroke(1.dp, theme.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = fill, contentColor = theme.textPrimary)
    ) {
        ButtonLabel(label)
    }
}

/**
 * The words on any button, centred.
 *
 * A label that wraps at large type took Text's start alignment, so its second line sat flush left
 * under a first line that looked centred: "Keep Chaitanya / Venkataraman" read as two labels.
 */
@Composable
internal fun ButtonLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(text, style = MeraPaisaType.action, color = color, textAlign = TextAlign.Center, modifier = modifier)
}

/**
 * One choice among a few, as a chip: a label on one line, centred, in a chip at least 48dp each
 * way.
 *
 * A chip sized itself to its label, so a currency symbol made a chip 34dp wide, a target to aim
 * at, and at large type the last chip in a row was squeezed until its word broke inside it
 * ("Phot/o"). Here the label never wraps and the chip never narrows below 48dp. Rows of these are
 * FlowRows, so a chip that does not fit moves to the next line whole.
 */
@Composable
internal fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = MeraPaisaType.action
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        shape = Shapes.small,
        modifier = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
        // The label itself takes the 48dp, less the chip's own 32dp of padding, so a single symbol
        // is centred in it. Held to a minimum on the chip alone, Material laid the label at the
        // start and the spare width after it: "₹" sat a few dp left of centre.
        label = {
            Text(
                label,
                style = style,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = ChipLabelMinWidth)
            )
        },
        colors = choiceChipColors(),
        border = choiceChipBorder(selected, enabled)
    )
}

/**
 * A row of chips choosing one of a few. The others are outlined like any button you can press, and
 * the chosen one sits on the highlight, edged in the accent. Material's own chips gave the chosen
 * one no edge and the rest only a divider's, so in every theme a choice was a faint fill beside
 * faint outlines, and the chosen chip read as the disabled one.
 */
@Composable
internal fun choiceChipColors(): SelectableChipColors {
    val theme = LocalAppTheme.current
    return FilterChipDefaults.filterChipColors(
        labelColor = theme.textSecondary,
        selectedContainerColor = theme.highlight,
        selectedLabelColor = theme.textPrimary
    )
}

/** The edge that goes with [choiceChipColors]. */
@Composable
internal fun choiceChipBorder(selected: Boolean, enabled: Boolean = true): BorderStroke {
    val theme = LocalAppTheme.current
    return FilterChipDefaults.filterChipBorder(
        enabled = enabled,
        selected = selected,
        borderColor = theme.border,
        selectedBorderColor = theme.primary,
        selectedBorderWidth = 1.5.dp
    )
}

/** A heading inside the list. Sentence case, quiet, with air above it and little below. */
@Composable
internal fun SectionHeading(text: String) {
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

/** A sentence in the gutter, aligned with everything else on the screen. */
@Composable
internal fun Paragraph(text: String, colour: Color = LocalAppTheme.current.textSecondary) {
    Text(
        text,
        style = MeraPaisaType.body,
        color = colour,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
    )
}

/**
 * Less side padding than Material's 24dp. Two buttons share the width at the foot, and at the
 * default "Restore from a file" and "Replace my ledger" broke onto a second line.
 */
private val FootButtonPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)

/** 48dp less a FilterChip's 8dp of padding and 8dp around its label, on each side. */
private val ChipLabelMinWidth = 16.dp

/**
 * For a full screen drawn over the ledger rather than in place of it.
 *
 * Screens here are switched on by state and laid over the people list, so a tap on an empty patch
 * of one, with nothing of its own under the finger, fell through to whatever sat behind: tapping
 * beside a person's history title opened Settings. This takes every touch that reaches the screen,
 * after its own buttons and lists have had their turn, so nothing behind it ever gets one.
 */
fun Modifier.coversLedger(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent() }
}
