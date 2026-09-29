package com.kg.merapaisa.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kg.merapaisa.AppTheme
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.themes
import com.kg.merapaisa.ui.EmptyState
import com.kg.merapaisa.ui.NetPosition
import com.kg.merapaisa.ui.NumPad
import com.kg.merapaisa.ui.PersonRow
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.SplitAdjustmentsContent
import com.kg.merapaisa.ui.SplitAmountScreen
import com.kg.merapaisa.ui.SplitPickerScreen
import com.kg.merapaisa.ui.Tab
import com.kg.merapaisa.ui.groups.GroupDetailScreen
import com.kg.merapaisa.ui.groups.GroupRow
import com.kg.merapaisa.ui.groups.GroupsEmptyState
import com.kg.merapaisa.ui.theme.MeraPaisaTheme

/**
 * Every fake-data rendering, written once.
 *
 * Android Studio shows these as previews and [com.kg.merapaisa.ui.gallery.DesignGalleryTest]
 * screenshots the same functions on a device, so what gets reviewed is what gets shipped. Keeping
 * two copies of the fake data would let the two drift, and the screenshot is the one that counts.
 *
 * [GalleryCases] is the list the harness walks. Add a case there and it appears in every theme.
 */

/** Puts a case in a themed, full-bleed frame so a screenshot shows the real background. */
@Composable
fun GalleryFrame(theme: AppTheme, content: @Composable () -> Unit) {
    MeraPaisaTheme(theme) {
        CompositionLocalProvider(LocalAppTheme provides theme) {
            Box(Modifier.fillMaxSize().background(theme.background)) { content() }
        }
    }
}

// -- cases ---------------------------------------------------------------------------------

/** One row of each shape a balance can take, stacked so the amount column has to line up. */
@Composable
fun PeopleRowsCase() {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Fixtures.mixedPeople.forEachIndexed { index, person ->
            if (index > 0) RowDivider()
            PersonRow(
                person = person,
                isSelected = person.id == 1L,
                onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {},
                onEditClick = {}, onSettleToggle = {}, onShareSummary = {},
                onShareLedger = {}, onMoveDebt = {}
            )
        }
    }
}

/** Long enough to scroll: catches a row that only looks right in isolation. */
@Composable
fun PeopleListLongCase() {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Fixtures.manyPeople.forEachIndexed { index, person ->
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

@Composable
fun SettledPersonCase() {
    Column(Modifier.fillMaxWidth()) {
        PersonRow(
            person = Fixtures.settledPerson,
            isSelected = false,
            onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {},
            onEditClick = {}, onSettleToggle = {}, onShareSummary = {},
            onShareLedger = {}, onMoveDebt = {}
        )
        PersonRow(
            person = Fixtures.evenPerson,
            isSelected = false,
            onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {},
            onEditClick = {}, onSettleToggle = {}, onShareSummary = {},
            onShareLedger = {}, onMoveDebt = {}
        )
    }
}

/** Three currencies, which must never be added together. */
@Composable
fun NetTotalMultiCase() = NetPosition(totals = Fixtures.totals, modifier = Modifier.fillMaxWidth())

@Composable
fun NetTotalSingleCase() = NetPosition(totals = Fixtures.totalsSingle, modifier = Modifier.fillMaxWidth())

@Composable
fun NetTotalEvenCase() = NetPosition(totals = Fixtures.totalsEven, modifier = Modifier.fillMaxWidth())

@Composable
fun NumPadCase() {
    NumPad(
        person = Fixtures.asha,
        input = "1234.50",
        onKey = {}, onSettleToggle = {}, onAdd = {}, onSubtract = {},
        note = Fixtures.LONG_NOTE,
        onNoteChange = {}, showNote = true, onToggleNote = {}
    )
}

@Composable
fun EmptyActiveCase() = EmptyState(tab = Tab.Active, modifier = Modifier.fillMaxSize(), onAddPerson = {})

@Composable
fun EmptySettledCase() = EmptyState(tab = Tab.Settled, modifier = Modifier.fillMaxSize())

@Composable
fun EmptyGroupsCase() = GroupsEmptyState(modifier = Modifier.fillMaxSize())

@Composable
fun GroupRowsCase() {
    Column(Modifier.fillMaxWidth()) {
        Fixtures.groupSummaries.forEachIndexed { index, summary ->
            if (index > 0) RowDivider()
            GroupRow(summary = summary, onClick = {}, onDelete = {})
        }
    }
}

/** Six members, unequal expenses, so the settle-up plan has something real to reduce. */
@Composable
fun GroupDetailCase() {
    GroupDetailScreen(
        group = Fixtures.group,
        members = Fixtures.groupMembers,
        expenses = Fixtures.groupExpenses,
        balances = Fixtures.groupBalances,
        transfers = Fixtures.groupTransfers,
        simplifyDebts = true,
        onBack = {}, onAddExpense = {}, onSettleUp = {},
        onDeleteExpense = {}, onSimplifyChange = {}
    )
}

/** The same group with nothing outstanding, which is a different sentence on screen. */
@Composable
fun GroupDetailEvenCase() {
    GroupDetailScreen(
        group = Fixtures.group,
        members = Fixtures.groupMembers.take(3),
        expenses = emptyList(),
        balances = Fixtures.groupMembers.take(3).map { com.kg.merapaisa.data.MemberBalance(it.id, 0) },
        transfers = emptyList(),
        simplifyDebts = true,
        onBack = {}, onAddExpense = {}, onSettleUp = {},
        onDeleteExpense = {}, onSimplifyChange = {}
    )
}

// -- the split flow -------------------------------------------------------------------------

@Composable
fun SplitAmountCase() {
    SplitAmountScreen(
        amount = "12345.50",
        currency = "INR",
        onCurrencyChange = {}, onAmountChange = {}, onCancel = {}, onNext = {}
    )
}

/** Mixed currencies and a long name, so the picker has to show real signed balances. */
@Composable
fun SplitPickerCase() {
    SplitPickerScreen(
        allPersons = Fixtures.mixedPeople,
        selectedIds = setOf(1L, 3L),
        includeMe = true,
        onToggleMe = {}, onTogglePerson = {}, onAddPerson = {},
        onBack = {}, onCancel = {}, onNext = {}
    )
}

/** One person selected, which is the state the primary action must refuse. */
@Composable
fun SplitPickerTooFewCase() {
    SplitPickerScreen(
        allPersons = Fixtures.mixedPeople,
        selectedIds = setOf(1L),
        includeMe = false,
        onToggleMe = {}, onTogglePerson = {}, onAddPerson = {},
        onBack = {}, onCancel = {}, onNext = {}
    )
}

/**
 * The screen the rework is really about: locked shares, and the rest redistributing.
 *
 * `convert` returns the amount unchanged, since a gallery run has no network and conversion is
 * not what this case is for.
 */
@Composable
fun SplitAdjustmentsCase() {
    SplitAdjustmentsContent(
        convert = { amount, _, _ -> amount },
        amountMinor = 12_345_50,
        sourceCurrency = "INR",
        selectedPersons = Fixtures.mixedPeople.take(4),
        includeMe = true,
        note = Fixtures.LONG_NOTE,
        onNoteChange = {}, onBack = {}, onCancel = {}, onConfirm = {}
    )
}

/**
 * Two shares locked and the rest absorbing the difference.
 *
 * The state the split rework is really about, and the one no screenshot showed until now: a locked
 * share has to read as locked without relying on a tint.
 */
@Composable
fun SplitAdjustmentsLockedCase() {
    SplitAdjustmentsContent(
        convert = { amount, _, _ -> amount },
        amountMinor = 12_345_50,
        sourceCurrency = "INR",
        selectedPersons = Fixtures.mixedPeople.take(4),
        includeMe = true,
        note = "Dinner",
        onNoteChange = {}, onBack = {}, onCancel = {}, onConfirm = {},
        initiallyLockedIds = setOf(1L, 2L)
    )
}

/**
 * The gallery's running order. The harness renders each of these in every theme, so this list is
 * the single place that decides what gets reviewed.
 */
val GalleryCases: List<Pair<String, @Composable () -> Unit>> = listOf(
    "people-rows" to { PeopleRowsCase() },
    "people-list-long" to { PeopleListLongCase() },
    "people-settled-and-even" to { SettledPersonCase() },
    "net-total-multi" to { NetTotalMultiCase() },
    "net-total-single" to { NetTotalSingleCase() },
    "net-total-even" to { NetTotalEvenCase() },
    "numpad" to { NumPadCase() },
    "empty-active" to { EmptyActiveCase() },
    "empty-settled" to { EmptySettledCase() },
    "empty-groups" to { EmptyGroupsCase() },
    "group-rows" to { GroupRowsCase() },
    "group-detail" to { GroupDetailCase() },
    "group-detail-even" to { GroupDetailEvenCase() },
    "split-amount" to { SplitAmountCase() },
    "split-picker" to { SplitPickerCase() },
    "split-picker-too-few" to { SplitPickerTooFewCase() },
    "split-adjustments" to { SplitAdjustmentsCase() },
    "split-adjustments-locked" to { SplitAdjustmentsLockedCase() }
)

// -- Android Studio previews ---------------------------------------------------------------

@Preview(name = "People rows, Paper", heightDp = 620)
@Composable
private fun PreviewPeoplePaper() = GalleryFrame(themes.first { it.name == "Paper" }) { PeopleRowsCase() }

@Preview(name = "People rows, Midnight", heightDp = 620)
@Composable
private fun PreviewPeopleMidnight() = GalleryFrame(themes.first { it.name == "Midnight" }) { PeopleRowsCase() }

@Preview(name = "Net position, Paper")
@Composable
private fun PreviewNetTotalPaper() = GalleryFrame(themes.first { it.name == "Paper" }) { NetTotalMultiCase() }

@Preview(name = "Group detail, Amoled", heightDp = 800)
@Composable
private fun PreviewGroupAmoled() = GalleryFrame(themes.first { it.name == "Amoled" }) { GroupDetailCase() }

@Preview(name = "Numpad, Ocean", heightDp = 620)
@Composable
private fun PreviewNumPadOcean() = GalleryFrame(themes.first { it.name == "Ocean" }) { NumPadCase() }
