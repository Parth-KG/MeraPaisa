package com.kg.merapaisa.ui.dialogs

import com.kg.merapaisa.ui.LabelAndAmount
import com.kg.merapaisa.ui.format.entrySpoken
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.ui.DecisionDialog
import com.kg.merapaisa.ui.MainViewModel
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.TextRowInset
import com.kg.merapaisa.ui.format.AmountText
import com.kg.merapaisa.ui.format.SignStyle
import com.kg.merapaisa.ui.format.amountSpoken
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.kg.merapaisa.ui.coversLedger

/**
 * Everything ever recorded with one person, newest first.
 *
 * This was an AlertDialog with a LazyColumn inside it and three more AlertDialogs stacked on top,
 * which is a whole ledger read through a letterbox: a dialog is sized for a question, and a
 * person's history is the longest thing in the app. It is a screen now, built like
 * `GroupDetailScreen`, and the two decisions it still asks for are the only dialogs left.
 *
 * The entries are grouped by day. A flat run of timestamps makes you read the date on every line
 * to work out where one day ends, whereas a heading says it once and the lines below it only have
 * to carry a time.
 */
@Composable
fun EntryHistoryScreen(person: PersonWithBalance, viewModel: MainViewModel, onBack: () -> Unit) {
    // Keyed on the person, because `getTransactions` builds a fresh Flow on every call and
    // collecting a new one each recomposition would restart the query for nothing.
    val stream = remember(person.id) { viewModel.getTransactions(person.id) }
    val entries by stream.collectAsState(initial = emptyList())

    EntryHistoryContent(
        person = person,
        entries = entries,
        onBack = onBack,
        onEdit = viewModel::editTransaction,
        onDelete = viewModel::deleteTransaction,
        onReverse = { target -> viewModel.rollbackToTransaction(person, target) },
        onClear = { viewModel.clearTransactionsForPerson(person.id) }
    )
}

/** The screen itself, given the entries, so the gallery can draw it without a database. */
@Composable
fun EntryHistoryContent(
    person: PersonWithBalance,
    entries: List<Transaction>,
    onBack: () -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onReverse: (Transaction) -> Unit,
    onClear: () -> Unit
) {
    val theme = LocalAppTheme.current

    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingReversal by remember { mutableStateOf<Transaction?>(null) }
    var editingEntry by remember { mutableStateOf<Transaction?>(null) }

    // The screen is switched on by state and drawn over the people list rather than pushed onto a
    // back stack, so back has to be caught here or it would fall through to the list underneath.
    BackHandler(enabled = true) { onBack() }

    val count = if (entries.size == 1) "1 entry" else "${entries.size} entries"

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
                onClick = onBack,
                modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.textPrimary
                )
            }
            Spacer(Modifier.weight(1f))
            // Clearing stays in the top bar rather than moving to a named button at the foot. It
            // is rare and destructive, and the foot of a screen is where the thing you came to do
            // belongs. There is nothing to clear on an empty history, and an item that always
            // refuses is worse than no item.
            if (entries.isNotEmpty()) {
                TextButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Clear history", style = MeraPaisaType.action, color = theme.textPrimary)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = Spacing.lg)
        ) {
            Text(
                "${person.name}'s history",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (entries.isNotEmpty()) {
                Text(count, style = MeraPaisaType.label, color = theme.textSecondary)
            }
        }

        // The balance counts group expenses too, and they are not entries here. Without this line
        // the entries added up to less than the figure on the main screen and nothing said why.
        val fromGroups = person.balanceMinor - entries.sumOf { it.amountMinor }
        if (fromGroups != 0L) {
            LabelAndAmount(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                    .clearAndSetSemantics {
                        contentDescription = "From groups you share, " + entrySpoken(fromGroups, person.currency)
                    },
                label = {
                    Column {
                        Text("From groups you share", style = MeraPaisaType.bodyStrong, color = theme.textPrimary)
                        Text(
                            "Not entries here. Open the group to see them.",
                            style = MeraPaisaType.label,
                            color = theme.textSecondary
                        )
                    }
                },
                amount = {
                    AmountText(
                        amountMinor = fromGroups,
                        currencyCode = person.currency,
                        style = MeraPaisaType.amount
                    )
                }
            )
        }

        if (entries.isEmpty()) {
            Text(
                "Nothing recorded with ${person.name} yet.",
                style = MeraPaisaType.body,
                color = theme.textSecondary,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl)
            )
        }

        val days = rememberDays(entries)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                ),
            contentPadding = PaddingValues(bottom = Spacing.lg)
        ) {
            days.forEach { day ->
                item(key = "day-${day.key}") { DayHeading(day.heading) }
                // The index restarts inside each day, so the first line of a day carries no
                // hairline: the heading above it is already the separation.
                itemsIndexed(day.entries, key = { _, t -> "entry-${t.id}" }) { index, t ->
                    if (index > 0) RowDivider(TextRowInset)
                    EntryRow(
                        entry = t,
                        person = person,
                        time = day.timeOf(t),
                        onEdit = { editingEntry = t },
                        onReverse = { pendingReversal = t }
                    )
                }
            }
        }
    }

    editingEntry?.let { target ->
        EditEntrySheet(
            entry = target,
            currency = person.currency,
            onSave = {
                onEdit(it)
                editingEntry = null
            },
            onDelete = {
                onDelete(it)
                editingEntry = null
            },
            onDismiss = { editingEntry = null }
        )
    }

    pendingReversal?.let { target ->
        // The same entries PersonDao.rollbackTo sums: this one and everything at or after it.
        // Naming the figure is what makes "reverse" something you can check before tapping.
        val undone = entries.filter { it.timestamp >= target.timestamp }
        val reversal = -undone.sumOf { it.amountMinor }
        val newer = undone.size - 1
        val which = when (newer) {
            0 -> "this entry"
            1 -> "this entry and the 1 newer one"
            else -> "this entry and the $newer newer ones"
        }
        val side = if (reversal >= 0) "your" else "their"
        DecisionDialog(
            title = if (newer == 0) "Reverse this entry?" else "Reverse this entry and the newer ones?",
            body = "Adds one entry of ${amountString(reversal, person.currency, SignStyle.None)} in " +
                "$side favour that cancels $which, so ${person.name}'s balance goes back to what " +
                "it was before. Nothing is deleted: the old entries stay in the history.",
            confirmLabel = "Reverse entries",
            dismissLabel = "Don't reverse",
            // Not destructive: it writes a line rather than removing any, so it takes the accent
            // that every other action on the screen takes.
            confirmColour = theme.primary,
            onConfirm = {
                onReverse(target)
                pendingReversal = null
            },
            onDismiss = { pendingReversal = null }
        )
    }

    if (showClearConfirm) {
        // What is owed survives a clear: the balance is derived from these rows, so clearing them
        // carries the outstanding amount across as one opening entry. Saying so is the difference
        // between a warning somebody reads and one they guess at. The figure is written into the
        // sentence with no sign, because the words either side of it already say which way it runs.
        // Only these entries are carried over. Whatever group expenses add stays in the groups, so
        // quoting the whole balance here named a figure the opening entry would not hold.
        val direct = entries.sumOf { it.amountMinor }
        val figure = amountString(direct, person.currency, SignStyle.None)
        val groupsNote = if (direct != person.balanceMinor) " What your groups add is not touched." else ""
        val body = when {
            direct == 0L ->
                "Deletes $count. They come to zero, so the balance stays where it is.$groupsNote " +
                    "This can't be undone."

            direct > 0 ->
                "Deletes $count. The $figure ${person.name} owes you from them is kept as an " +
                    "opening balance.$groupsNote This can't be undone."

            else ->
                "Deletes $count. The $figure you owe ${person.name} from them is kept as an " +
                    "opening balance.$groupsNote This can't be undone."
        }
        DecisionDialog(
            title = "Clear ${person.name}'s history?",
            body = body,
            confirmLabel = "Clear history",
            dismissLabel = "Keep the history",
            onConfirm = {
                onClear()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false }
        )
    }
}

/** A day's worth of entries, with the heading and the clock already worked out for them. */
private class HistoryDay(
    val key: String,
    val heading: String,
    val entries: List<Transaction>,
    private val clock: SimpleDateFormat
) {
    fun timeOf(entry: Transaction): String = clock.format(Date(entry.timestamp))
}

/**
 * Cuts the history into days, newest first.
 *
 * The rows arrive ordered by time, so grouping by a day key keeps that order without a second
 * sort. The key is a formatted date rather than a division of the timestamp: days are not all the
 * same length once a clock changes, and the phone's own calendar is the only thing that knows
 * where this person's midnight falls.
 *
 * Today and yesterday are named in words because that is how anybody would say them out loud, and
 * the year is left off within this one, where it is the same on every line and carries nothing.
 */
@Composable
private fun rememberDays(entries: List<Transaction>): List<HistoryDay> {
    val locale = Locale.getDefault()
    return remember(entries, locale) {
        val key = SimpleDateFormat("yyyy-MM-dd", locale)
        val thisYear = SimpleDateFormat("EEEE d MMMM", locale)
        val otherYear = SimpleDateFormat("d MMMM yyyy", locale)
        val clock = SimpleDateFormat("h:mm a", locale)

        val calendar = Calendar.getInstance()
        val todayKey = key.format(calendar.time)
        val currentYear = calendar.get(Calendar.YEAR)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayKey = key.format(calendar.time)

        entries.groupBy { key.format(Date(it.timestamp)) }.map { (dayKey, sameDay) ->
            val date = Date(sameDay.first().timestamp)
            val stamp = Calendar.getInstance().apply { time = date }
            HistoryDay(
                key = dayKey,
                heading = when {
                    dayKey == todayKey -> "Today"
                    dayKey == yesterdayKey -> "Yesterday"
                    stamp.get(Calendar.YEAR) == currentYear -> thisYear.format(date)
                    else -> otherYear.format(date)
                },
                entries = sameDay,
                clock = clock
            )
        }
    }
}

/** The date over a day's entries. Sentence case, quiet, with air above it and none below. */
@Composable
private fun DayHeading(text: String) {
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
 * One line of the ledger: a row on the background, not a tile.
 *
 * The figure keeps its sign and its ink, because an entry is money running one way or the other
 * and this is the one screen where you can see which. An entry with no note says the direction in
 * words instead, so the row never opens with an empty line.
 *
 * The two actions sit apart. Tapping the line opens it for correction, which is what you reach for
 * after a typo; reversing is its own button because it reaches past this entry to every newer one,
 * and that is not something to hit by aiming at a row.
 */
@Composable
private fun EntryRow(
    entry: Transaction,
    person: PersonWithBalance,
    time: String,
    onEdit: () -> Unit,
    onReverse: () -> Unit
) {
    val theme = LocalAppTheme.current
    val title = entry.note.ifBlank {
        if (entry.amountMinor > 0) "They owe you" else "You owe them"
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = "Edit this entry", onClick = onEdit)
                .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md)
                // Merged rather than cleared, so the line is one stop for TalkBack while the
                // button beside it stays a stop of its own.
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "$title, $time, ${entrySpoken(entry.amountMinor, person.currency)}"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MeraPaisaType.bodyStrong,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(time, style = MeraPaisaType.label, color = theme.textSecondary)
            }
            Box(Modifier.clearAndSetSemantics { }) {
                AmountText(
                    amountMinor = entry.amountMinor,
                    currencyCode = person.currency,
                    style = MeraPaisaType.amount,
                    columnAligned = true
                )
            }
        }

        IconButton(
            onClick = onReverse,
            modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Reverse this entry and the newer ones",
                tint = theme.textSecondary
            )
        }
    }
}
