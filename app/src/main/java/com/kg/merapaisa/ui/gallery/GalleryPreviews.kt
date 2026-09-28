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
import com.kg.merapaisa.ui.NetTotalCard
import com.kg.merapaisa.ui.NumPad
import com.kg.merapaisa.ui.PersonRow
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
        Fixtures.mixedPeople.forEach { person ->
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
        Fixtures.manyPeople.forEach { person ->
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
fun NetTotalMultiCase() = NetTotalCard(totals = Fixtures.totals, modifier = Modifier.fillMaxWidth())

@Composable
fun NetTotalSingleCase() = NetTotalCard(totals = Fixtures.totalsSingle, modifier = Modifier.fillMaxWidth())

@Composable
fun NetTotalEvenCase() = NetTotalCard(totals = Fixtures.totalsEven, modifier = Modifier.fillMaxWidth())

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
fun EmptyActiveCase() = EmptyState(tab = Tab.Active, modifier = Modifier.fillMaxSize())

@Composable
fun EmptySettledCase() = EmptyState(tab = Tab.Settled, modifier = Modifier.fillMaxSize())

@Composable
fun EmptyGroupsCase() = GroupsEmptyState(modifier = Modifier.fillMaxSize())

@Composable
fun GroupRowsCase() {
    Column(Modifier.fillMaxWidth()) {
        Fixtures.groupSummaries.forEach { GroupRow(summary = it, onClick = {}, onDelete = {}) }
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
    "group-detail-even" to { GroupDetailEvenCase() }
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
