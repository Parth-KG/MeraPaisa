package com.kg.merapaisa.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.currencySymbol
import com.kg.merapaisa.data.formatMinorPlain
import com.kg.merapaisa.ui.format.PlainAmountText
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.data.parseAmountToMinor
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Motion
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing

data class SplitParticipant(
    val id: Long,
    val name: String,
    val currency: String
)

/**
 * A conversion result together with the source amounts it was computed from.
 *
 * Kept as one value rather than two states so the two can never be read half-updated, and so
 * "is this result still about what is on screen?" is a single comparison. Confirming a result
 * that predates the last edit would record the amounts the user just changed away from.
 */
private data class ConvertedSplit(
    val forAmountsInSource: Map<Long, Long>,
    val amounts: Map<Long, Long>
)

/** How long the amount fields must be quiet before a rate is fetched for them. */
private const val CONVERSION_SETTLE_MS = 400L

private fun equalSplit(amountMinor: Long, participants: List<SplitParticipant>): Map<Long, Long> {
    if (participants.isEmpty()) return emptyMap()
    val base = amountMinor / participants.size
    val remainder = amountMinor - (base * participants.size)
    return participants.mapIndexed { i, p ->
        p.id to if (i == 0) base + remainder else base
    }.toMap()
}

private fun redistribute(
    current: Map<Long, Long>,
    locked: Set<Long>,
    changedId: Long,
    newValue: Long,
    total: Long
): Map<Long, Long> {
    val updated = current.toMutableMap()
    updated[changedId] = newValue

    val lockedSum = updated.filterKeys { it in locked }.values.sum()
    val unlockedIds = updated.keys.filter { it !in locked }
    if (unlockedIds.isEmpty()) return updated

    val remaining = total - lockedSum
    val perUnlocked = remaining / unlockedIds.size
    // Integer division leaves a few minor units over; give them to the first row so the
    // parts still add up to the whole rather than tripping the totals-mismatch warning.
    val leftover = remaining - perUnlocked * unlockedIds.size

    unlockedIds.forEachIndexed { i, id ->
        val share = if (i == 0) perUnlocked + leftover else perUnlocked
        updated[id] = kotlin.math.max(0L, share)
    }
    return updated
}

/**
 * Step three of a split: who pays what.
 *
 * Changing one share moves the others, and the whole point of this screen is being able to see
 * that happen. So the shares nobody touched count to their new value, and the one under the thumb
 * does not: the movement is what tells you which numbers the app decided and which you did.
 *
 * A locked share is drawn as locked, not merely tinted. It says the word, it loses the fill that
 * makes a field look typeable, and it carries a closed padlock. Any one of those on its own is a
 * signal somebody cannot read.
 */
@Composable
fun SplitAdjustmentsScreen(
    viewModel: MainViewModel,
    amountMinor: Long,
    sourceCurrency: String,
    selectedPersons: List<PersonWithBalance>,
    includeMe: Boolean,
    note: String,
    onNoteChange: (String) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (Map<Long, Long>) -> Unit
) = SplitAdjustmentsContent(
    // The only thing the view model was ever asked for here. Passing the one call it makes, rather
    // than the whole view model, is what lets the screenshot gallery and a preview render this
    // screen at all. The view model taking signature stays exactly as it was, because
    // SplitConfirmTimingTest and MainScreen both call it.
    convert = viewModel::convertCurrency,
    amountMinor = amountMinor,
    sourceCurrency = sourceCurrency,
    selectedPersons = selectedPersons,
    includeMe = includeMe,
    note = note,
    onNoteChange = onNoteChange,
    onBack = onBack,
    onCancel = onCancel,
    onConfirm = onConfirm
)

/**
 * The same screen with no view model: everything it needs, handed in.
 *
 * Kept separate so previews and the gallery can render it with fake data. A screen that can only
 * be seen by running the app is a screen nobody looks at until it ships.
 */
@Composable
fun SplitAdjustmentsContent(
    convert: suspend (Long, String, String) -> Long?,
    amountMinor: Long,
    sourceCurrency: String,
    selectedPersons: List<PersonWithBalance>,
    includeMe: Boolean,
    note: String,
    onNoteChange: (String) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (Map<Long, Long>) -> Unit,
    /**
     * Rows that start locked. Empty in the app, since a split opens with nothing locked and a row
     * locks when it is edited. It exists so a preview and the gallery can show the locked state,
     * which is otherwise only reachable by typing and therefore never appears in a screenshot.
     */
    initiallyLockedIds: Set<Long> = emptySet()
) {
    val theme = LocalAppTheme.current

    // Build participant list. "You" is represented by id = -1L (won't conflict with any DB id).
    val youId = -1L
    val participants = remember(selectedPersons, includeMe) {
        val list = mutableListOf<SplitParticipant>()
        if (includeMe) {
            list.add(SplitParticipant(id = youId, name = "You", currency = sourceCurrency))
        }
        selectedPersons.forEach {
            list.add(SplitParticipant(id = it.id, name = it.name, currency = it.currency))
        }
        list
    }

    // Per-person amounts in source currency (we convert at confirm time only for display)
    var amountsInSource by remember(participants, amountMinor) {
        mutableStateOf(equalSplit(amountMinor, participants))
    }
    var lockedIds by remember(participants) { mutableStateOf(initiallyLockedIds) }

    // The row the user last typed in. It is the one row that must not animate: its digits are
    // already under a thumb, and a number that moves while you type it cannot be read.
    var lastEditedId by remember(participants) { mutableStateOf<Long?>(null) }

    // Converted amounts (in each person's own currency), recalculated when amountsInSource changes
    var converted by remember { mutableStateOf<ConvertedSplit?>(null) }
    var conversionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(amountsInSource, participants) {
        conversionError = null
        // Every keystroke rewrites amountsInSource, so without this each digit fired one rate
        // request per foreign-currency participant for a number still being typed. Waiting for
        // the typing to settle cancels those. Rows already in the source currency never wait.
        if (participants.any { it.currency != sourceCurrency }) delay(CONVERSION_SETTLE_MS)
        val result = mutableMapOf<Long, Long>()
        for (p in participants) {
            val srcAmt = amountsInSource[p.id] ?: 0L
            if (p.currency == sourceCurrency) {
                result[p.id] = srcAmt
            } else {
                val convertedAmount = convert(srcAmt, sourceCurrency, p.currency)
                if (convertedAmount == null) {
                    conversionError = "Couldn't get today's rate for ${p.currency}, so ${p.name}'s " +
                        "share can't be worked out. Try again once you're connected, or take " +
                        "${p.name} out of the split."
                    result[p.id] = srcAmt   // fallback, but warning is shown
                } else {
                    result[p.id] = convertedAmount
                }
            }
        }
        converted = ConvertedSplit(forAmountsInSource = amountsInSource, amounts = result)
    }

    // Anything computed from an earlier set of amounts is stale and must not be shown or
    // confirmed. Null means "not converted yet", which is also the state on first composition.
    val convertedAmounts = converted?.takeIf { it.forAmountsInSource == amountsInSource }?.amounts

    val total = amountsInSource.values.sum()
    val totalsMatch = total == amountMinor

    Column(modifier = Modifier.fillMaxSize().background(theme.background).coversLedger()) {

        SplitStepBar(onCancel = onCancel, onBack = onBack)

        SplitStepHeading(
            title = "Adjust the shares",
            supporting = "Splitting ${amountString(amountMinor, sourceCurrency)}"
        )

        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            placeholder = {
                Text("Dinner, cab fare", style = MeraPaisaType.body, color = theme.textSecondary)
            },
            textStyle = MeraPaisaType.body,
            singleLine = true,
            shape = Shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.md)
        )

        // No dividers between these rows. Each one already carries a boxed field, and a hairline
        // inset to a name that starts at the gutter would cut across it rather than separate it.
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items(participants, key = { it.id }) { p ->
                SplitShareRow(
                    participant = p,
                    sourceCurrency = sourceCurrency,
                    amountInSourceMinor = amountsInSource[p.id] ?: 0L,
                    convertedAmountMinor = convertedAmounts?.get(p.id),
                    locked = p.id in lockedIds,
                    // Only the shares the user left alone move on their own, so only those count.
                    animateShare = p.id != lastEditedId,
                    onAmountChange = { newAmt ->
                        lastEditedId = p.id
                        amountsInSource = redistribute(
                            current = amountsInSource,
                            locked = lockedIds + p.id,   // editing locks this row
                            changedId = p.id,
                            newValue = newAmt,
                            total = amountMinor
                        )
                        lockedIds = lockedIds + p.id
                    },
                    onToggleLock = {
                        if (p.id in lockedIds) {
                            lockedIds = lockedIds - p.id
                            // Unlocked, this row is back in the pool that redistribution moves,
                            // so it stops being the row that must hold still.
                            if (lastEditedId == p.id) lastEditedId = null
                        } else {
                            lockedIds = lockedIds + p.id
                        }
                    }
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            LabelAndAmount(
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Shares add up to", style = MeraPaisaType.body, color = theme.textSecondary)
                },
                // No sign and no direction ink: this is a sum being checked against a target, not
                // a debt running one way or the other. Red is reserved for it being wrong.
                amount = {
                    PlainAmountText(
                        amountMinor = total,
                        currencyCode = sourceCurrency,
                        style = MeraPaisaType.amount,
                        colour = if (totalsMatch) theme.textPrimary else theme.negative
                    )
                }
            )
            if (!totalsMatch) {
                Text(
                    mismatchMessage(
                        total = total,
                        target = amountMinor,
                        currency = sourceCurrency,
                        lockedSum = amountsInSource.filterKeys { it in lockedIds }.values.sum(),
                        everyShareLocked = participants.all { it.id in lockedIds }
                    ),
                    style = MeraPaisaType.label,
                    color = theme.negative,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
            // Only when somebody is in another currency. With everyone in the source currency the
            // amounts are ready a frame after the screen opens, and the line flashed up for that
            // one frame on every ordinary split.
            if (convertedAmounts == null && conversionError == null &&
                participants.any { it.currency != sourceCurrency }
            ) {
                Text(
                    "Getting today's rates…",
                    style = MeraPaisaType.label,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
            if (conversionError != null) {
                Text(
                    conversionError!!,
                    style = MeraPaisaType.label,
                    color = theme.negative,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
        }

        SplitPrimaryButton(
            label = "Save split",
            enabled = conversionError == null && convertedAmounts != null,
            onClick = {
                // Build final map, excluding "You". Guarded rather than trusted: `enabled`
                // already blocks this, and a null here would mean confirming amounts that
                // were never converted.
                val ready = convertedAmounts
                if (ready != null) onConfirm(ready.filterKeys { it != youId })
            }
        )
    }
}

/**
 * Why the shares do not add up, by how much, and what to do about it.
 *
 * It used to say "Warning: totals don't match." A person looking at that knows less than they did
 * before reading it: they can see the two figures above disagree. What they cannot see is that a
 * lock is the reason nothing moved to close the gap.
 */
private fun mismatchMessage(
    total: Long,
    target: Long,
    currency: String,
    lockedSum: Long,
    everyShareLocked: Boolean
): String {
    val over = total > target
    // The sentence carries the direction in words, so the figure itself is the gap's size.
    val gap = amountString(if (over) total - target else target - total, currency)
    val what =
        if (over) "These shares come to $gap more than you're splitting."
        else "These shares are $gap short of what you're splitting."
    val why = when {
        everyShareLocked ->
            "Every share is locked, so there was nothing left to take up the difference. " +
                "Unlock a row, then change a share."
        lockedSum > target ->
            "The locked shares already come to more than the whole split. Lower one of them, " +
                "or unlock it."
        else -> "Change a share, or unlock one so the rest can take up the difference."
    }
    return "$what $why"
}

/**
 * One person's share of the split.
 *
 * The share counts to its new value when the redistribution moved it, and snaps when the person
 * typing moved it. Nothing animates on first composition: the animation starts at the value it is
 * given, so the opening shares simply appear.
 */
@Composable
private fun SplitShareRow(
    participant: SplitParticipant,
    sourceCurrency: String,
    amountInSourceMinor: Long,
    convertedAmountMinor: Long?,
    locked: Boolean,
    animateShare: Boolean,
    onAmountChange: (Long) -> Unit,
    onToggleLock: () -> Unit
) {
    val theme = LocalAppTheme.current

    val animated by animateFloatAsState(
        targetValue = amountInSourceMinor.toFloat(),
        animationSpec = tween(durationMillis = Motion.medium, easing = Motion.emphasized),
        label = "share"
    )
    // A float holds about seven digits, so the figure that settles is always read back from the
    // Long. The float only fills the frames in between, where a paisa either way cannot be seen.
    val shownMinor = when {
        !animateShare -> amountInSourceMinor
        animated == amountInSourceMinor.toFloat() -> amountInSourceMinor
        else -> animated.roundToLong()
    }

    // Plain digits rather than a grouped amount: the grouping commas would not parse back, and
    // this is the one amount on screen the user types into rather than reads.
    //
    // Trailing zeros are kept. Trimming them turned a share of 2,469.10 into "2469.1", which reads
    // as a different and slightly wrong number, and parseAmountToMinor accepts either.
    //
    // While the field has focus it shows exactly what was typed, and only takes the formatted
    // figure back once focus leaves. Rebuilding the text from the amount on every keystroke
    // snapped "600" back to "60.00" on the first backspace, so a share could not be cleared, and
    // the next digit landed after the ".00".
    var editing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val formatted = formatMinorPlain(shownMinor, sourceCurrency, trimZeros = false)
    val text = if (editing) typed else formatted

    val supporting = when {
        convertedAmountMinor != null && participant.currency != sourceCurrency && locked ->
            "Locked, saved as ${amountString(convertedAmountMinor, participant.currency)}"
        convertedAmountMinor != null && participant.currency != sourceCurrency ->
            "Saved as ${amountString(convertedAmountMinor, participant.currency)}"
        locked -> "Locked"
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                participant.name,
                style = MeraPaisaType.bodyStrong,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MeraPaisaType.label,
                    color = theme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // A locked share loses the fill that makes a box look typeable and keeps only a rule
        // around it, so it reads as a figure held rather than a field waiting. It is still
        // editable: locking a share should not cost you a tap to change it.
        Row(
            modifier = Modifier
                .clip(Shapes.small)
                .then(
                    if (locked) Modifier.border(1.dp, theme.outline, Shapes.small)
                    else Modifier.background(theme.fill)
                )
                .heightIn(min = 48.dp)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // The symbol leads so the amount grows into the row's slack instead of into the
            // symbol, and it is set a step down and quieter, the way every other amount sets it.
            Text(
                currencySymbol(sourceCurrency),
                style = MeraPaisaType.amountSmall,
                color = theme.textSecondary
            )
            BasicTextField(
                value = text,
                onValueChange = { newText ->
                    typed = newText
                    parseAmountToMinor(newText)?.let { onAmountChange(it) }
                },
                textStyle = MeraPaisaType.amount.copy(
                    color = theme.textPrimary,
                    textAlign = TextAlign.End
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                cursorBrush = SolidColor(theme.primary),
                // Sizes to its content: the floor keeps an empty field tappable, the ceiling stops
                // a pathological amount swallowing the name.
                modifier = Modifier
                    .widthIn(min = 56.dp, max = 140.dp)
                    .onFocusChanged { state ->
                        if (state.isFocused && !editing) typed = formatted
                        editing = state.isFocused
                    }
            )
        }

        IconButton(onClick = onToggleLock, modifier = Modifier.size(48.dp)) {
            Icon(
                if (locked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                contentDescription =
                    // "You" is a name here, so the row for you would read "Lock You's share".
                    when {
                        participant.name == "You" && locked -> "Unlock your share"
                        participant.name == "You" -> "Lock your share"
                        locked -> "Unlock ${participant.name}'s share"
                        else -> "Lock ${participant.name}'s share"
                    },
                tint = if (locked) theme.primary else theme.textSecondary
            )
        }
    }
}
