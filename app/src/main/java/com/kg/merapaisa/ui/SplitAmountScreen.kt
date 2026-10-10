package com.kg.merapaisa.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import com.kg.merapaisa.data.currencyDecimals
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.SUPPORTED_CURRENCIES
import com.kg.merapaisa.data.parseAmountToMinor
import com.kg.merapaisa.ui.format.AmountText
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.format.TypedAmountText

/**
 * Step one of a split: how much there is to divide, and the currency it is divided in.
 *
 * The figure goes through AmountText rather than being pasted together from a symbol and the raw
 * entry, so it groups while it is typed: it read ₹1234.50 where every row it was about to become
 * read ₹1,234.50.
 *
 * The bar, the heading and the button at the foot are shared by all three steps and live at the
 * bottom of this file. Written once, because a flow whose three screens each grow their own top
 * bar stops reading as one flow.
 */
@Composable
fun SplitAmountScreen(
    amount: String,
    currency: String,
    onCurrencyChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit
) {
    val theme = LocalAppTheme.current
    val typedMinor = parseAmountToMinor(amount)
    // On its side the keypad does not fit under the heading, so the screen scrolls instead of
    // spacing things out. It was cut off with half its keys unreachable.
    val short = LocalConfiguration.current.screenHeightDp < 480

    Column(
        modifier = Modifier.fillMaxSize().background(theme.background).coversLedger()
            .then(if (short) Modifier.verticalScroll(rememberScrollState()) else Modifier)
    ) {

        SplitStepBar(onCancel = onCancel)

        SplitStepHeading(
            title = "Split an expense",
            supporting = "Every share is worked out from this amount, in this currency."
        )

        Spacer(if (short) Modifier.height(Spacing.lg) else Modifier.weight(1f))

        TypedSplitAmount(entry = amount, currency = currency)

        Spacer(Modifier.height(Spacing.lg))

        // Which currency the split is in. Everyone's share converts from this, so leaving it
        // implicit is how the amounts used to come out wrong. Codes rather than symbols: ₹ and
        // $ side by side are two glyphs to compare, INR and USD are two words to read.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)
        ) {
            SUPPORTED_CURRENCIES.forEach { code ->
                FilterChip(
                    selected = currency == code,
                    onClick = {
                        onCurrencyChange(code)
                        // A fraction typed for rupees has no meaning in yen.
                        if (currencyDecimals(code) == 0 && '.' in amount) onAmountChange(amount.substringBefore('.'))
                    },
                    shape = Shapes.small,
                    label = { Text(code, style = MeraPaisaType.action) },
                    colors = choiceChipColors(),
                    border = choiceChipBorder(currency == code)
                )
            }
        }

        Spacer(if (short) Modifier.height(Spacing.lg) else Modifier.weight(1f))

        SplitKeypad(entry = amount, currency = currency, onEntryChange = onAmountChange)

        SplitPrimaryButton(
            label = "Choose people",
            enabled = (typedMinor ?: 0L) > 0L,
            onClick = onNext
        )
    }
}

/**
 * What has been typed so far, grouped.
 *
 * Read back through the formatter while a trailing "." or a lone "0" is still being typed, which
 * neither parses nor groups, so those fall back to the raw entry rather than blanking the display
 * mid-keystroke. Right-aligned like every other amount in the app.
 */
@Composable
private fun TypedSplitAmount(entry: String, currency: String) {
    TypedAmountText(
        entry = entry,
        currencyCode = currency,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
    )
}

/** The key that clears one digit, told apart from the digits by name rather than by a glyph. */
private const val SPLIT_BACKSPACE = "backspace"

/**
 * The keys stay tiles. This is one of the two places where a grid of identical rounded shapes is
 * right, since they really are identical controls doing the same job at the same weight.
 */
@Composable
private fun SplitKeypad(entry: String, currency: String, onEntryChange: (String) -> Unit) {
    // As on the main keypad: no point for a currency with no fractions.
    val point = if (currencyDecimals(currency) == 0) "" else "."
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", point, "0", SPLIT_BACKSPACE)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        SplitKey(
                            key = key,
                            modifier = Modifier.weight(1f),
                            onClick = { onEntryChange(applyKey(entry, key)) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * What one key does to the entry.
 *
 * A second decimal point is swallowed rather than refused out loud: there is nothing to explain,
 * the amount already has one.
 */
private fun applyKey(entry: String, key: String): String = when {
    key == SPLIT_BACKSPACE -> entry.dropLast(1)
    key == "." && entry.contains(".") -> entry
    key == "." && entry.isEmpty() -> "0."
    else -> entry + key
}

@Composable
private fun SplitKey(key: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val isBackspace = key == SPLIT_BACKSPACE
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = Shapes.small,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = theme.highlight,
            contentColor = if (isBackspace) theme.textSecondary else theme.textPrimary
        )
    ) {
        if (isBackspace) {
            Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete a digit")
        } else {
            Text(
                key,
                style = MeraPaisaType.body,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = if (key == ".") "decimal point" else key
                }
            )
        }
    }
}

/**
 * The bar every step of the split carries: a way back, and a way out.
 *
 * Icons only. The step's title sits below in [SplitStepHeading], at the gutter and at full size,
 * where it has room to wrap at a large font scale instead of being squeezed between two buttons.
 */
@Composable
internal fun SplitStepBar(onCancel: () -> Unit, onBack: (() -> Unit)? = null) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back a step",
                    tint = theme.textPrimary
                )
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Drop this split",
                tint = theme.textPrimary
            )
        }
    }
}

/** The step's title, aligned left with everything below it, the way the rest of the app opens. */
@Composable
internal fun SplitStepHeading(title: String, supporting: String? = null) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.md)
    ) {
        Text(title, style = MeraPaisaType.screenTitle, color = theme.textPrimary)
        if (supporting != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(supporting, style = MeraPaisaType.label, color = theme.textSecondary)
        }
    }
}

/**
 * The one button that carries each step forward.
 *
 * The accent, not the positive ink: green and red belong to amounts, and a button wearing the
 * colour of money owed to you is a button pretending to be a balance.
 */
@Composable
internal fun SplitPrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .heightIn(min = 56.dp),
        // Disabled, it looks like every other button that can't be pressed yet. It was the
        // highlight under grey text, which since grey text reached 7:1 read as a button you could.
        colors = ButtonDefaults.buttonColors(
            containerColor = theme.primary,
            contentColor = theme.onAccent
        )
    ) {
        ButtonLabel(label)
    }
}
