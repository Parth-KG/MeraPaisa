package com.kg.merapaisa.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
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
import com.kg.merapaisa.ui.DecisionDialog
import com.kg.merapaisa.ui.EmptyState
import com.kg.merapaisa.ui.ImportFlowState
import com.kg.merapaisa.ui.LockedScreen
import com.kg.merapaisa.ui.NetPosition
import com.kg.merapaisa.ui.NumPad
import com.kg.merapaisa.ui.PersonRow
import com.kg.merapaisa.ui.RowDivider
import com.kg.merapaisa.ui.TextRowInset
import com.kg.merapaisa.ui.SplitAdjustmentsContent
import com.kg.merapaisa.ui.SplitAmountScreen
import com.kg.merapaisa.ui.SplitPickerScreen
import com.kg.merapaisa.ui.Tab
import com.kg.merapaisa.ui.UpdateFlowState
import com.kg.merapaisa.ui.backup.BackupDialog
import com.kg.merapaisa.ui.dialogs.ThemeSheet
import com.kg.merapaisa.ui.dialogs.AddPersonDialog
import com.kg.merapaisa.ui.dialogs.ConvertCurrencyDialog
import com.kg.merapaisa.ui.dialogs.CreateGroupDialog
import com.kg.merapaisa.ui.dialogs.EditEntrySheet
import com.kg.merapaisa.ui.dialogs.EditPersonDialog
import com.kg.merapaisa.ui.dialogs.EntryHistoryContent
import com.kg.merapaisa.ui.dialogs.MoveDebtDialog
import com.kg.merapaisa.ui.dialogs.ReminderSheet
import com.kg.merapaisa.ui.dialogs.SettingsScreen
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.ui.groups.AddExpenseDialog
import com.kg.merapaisa.ui.groups.GroupDetailScreen
import com.kg.merapaisa.ui.groups.GroupRow
import com.kg.merapaisa.ui.groups.GroupsEmptyState
import com.kg.merapaisa.ui.groups.EditGroupSheet
import com.kg.merapaisa.ui.groups.SettleUpSheet
import com.kg.merapaisa.ui.share.ImportLedgerDialog
import com.kg.merapaisa.ui.share.ShareLedgerSheet
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import com.kg.merapaisa.ui.update.UpdateScreen
import com.kg.merapaisa.data.PersonWithBalance

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
fun PeopleRowsCase() = PeopleRows(Fixtures.mixedPeople)

/** Whole amounts only, in rupees and yen: a column with no paise keeps no empty decimal slot. */
@Composable
fun PeopleRowsWholeCase() = PeopleRows(Fixtures.wholePeople)

@Composable
private fun PeopleRows(people: List<PersonWithBalance>) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        people.forEachIndexed { index, person ->
            if (index > 0) RowDivider()
            PersonRow(
                person = person,
                isSelected = person.id == 1L,
                onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {},
                onEditClick = {}, onSettleUp = {}, onShareSummary = {},
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
                onEditClick = {}, onSettleUp = {}, onShareSummary = {},
                onShareLedger = {}, onMoveDebt = {}
            )
        }
    }
}

/** Two people on Even: one squared with Settle up, one who never owed anything. */
@Composable
fun EvenPeopleCase() {
    Column(Modifier.fillMaxWidth()) {
        PersonRow(
            person = Fixtures.settledUpPerson,
            isSelected = false,
            onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {},
            onEditClick = {}, onSettleUp = {}, onShareSummary = {},
            onShareLedger = {}, onMoveDebt = {}
        )
        PersonRow(
            person = Fixtures.evenPerson,
            isSelected = false,
            onHistoryClick = {}, onClick = {}, onSendReminder = {}, onDelete = {},
            onEditClick = {}, onSettleUp = {}, onShareSummary = {},
            onShareLedger = {}, onMoveDebt = {}
        )
    }
}

/** Three currencies, which must never be added together. */
@Composable
fun NetTotalMultiCase(bothSides: Boolean = false) = NetPosition(
    totals = Fixtures.totals,
    sides = Fixtures.sides,
    bothSides = bothSides,
    onToggle = {},
    modifier = Modifier.fillMaxWidth()
)

@Composable
fun NetTotalSingleCase(bothSides: Boolean = false) = NetPosition(
    totals = Fixtures.totalsSingle,
    sides = Fixtures.sidesSingle,
    bothSides = bothSides,
    onToggle = {},
    modifier = Modifier.fillMaxWidth()
)

@Composable
fun NetTotalEvenCase() = NetPosition(totals = Fixtures.totalsEven, modifier = Modifier.fillMaxWidth())

@Composable
fun NumPadCase() {
    NumPad(
        person = Fixtures.asha,
        input = "1234.50",
        onKey = {}, onSettleUp = {}, onAdd = {}, onSubtract = {},
        note = Fixtures.LONG_NOTE,
        onNoteChange = {}, showNote = true, onToggleNote = {}
    )
}

/** The keypad at zero: nothing to settle, so no button under the two directions. */
@Composable
fun NumPadEvenCase() {
    NumPad(
        person = Fixtures.settledUpPerson,
        input = "",
        onKey = {}, onSettleUp = {}, onAdd = {}, onSubtract = {},
        note = "",
        onNoteChange = {}, showNote = false, onToggleNote = {}
    )
}

/** Yen has no fractions, so its keypad has no point: the slot stays empty beside the 0. */
@Composable
fun NumPadYenCase() {
    NumPad(
        person = Fixtures.asha.copy(person = Fixtures.asha.person.copy(currency = "JPY"), balanceMinor = 12_500_00),
        input = "1250",
        onKey = {}, onSettleUp = {}, onAdd = {}, onSubtract = {},
        note = "", onNoteChange = {}, showNote = false, onToggleNote = {}
    )
}

@Composable
fun EmptyActiveCase() = EmptyState(tab = Tab.Active, modifier = Modifier.fillMaxSize(), onAddPerson = {})

/** People exist, all of them even, so Active is empty for a happier reason. */
@Composable
fun EmptyActiveEveryoneEvenCase() =
    EmptyState(tab = Tab.Active, modifier = Modifier.fillMaxSize(), everyoneEven = true, onAddPerson = {})

@Composable
fun EmptyEvenCase() = EmptyState(tab = Tab.Even, modifier = Modifier.fillMaxSize())

@Composable
fun EmptyGroupsCase() = GroupsEmptyState(modifier = Modifier.fillMaxSize())

@Composable
fun GroupRowsCase() {
    Column(Modifier.fillMaxWidth()) {
        Fixtures.groupSummaries.forEachIndexed { index, summary ->
            if (index > 0) RowDivider(TextRowInset)
            GroupRow(summary = summary, onClick = {}, onDelete = {})
        }
    }
}

/** The Groups tab with its Archived section open: two in use, one archived (v3.7.0). */
@Composable
fun GroupsArchivedCase() = com.kg.merapaisa.ui.groups.GroupsList(
    groups = Fixtures.groupSummaries.take(2),
    archived = Fixtures.groupSummaries.drop(2).map { it.copy(group = it.group.copy(archived = true)) },
    showArchived = true,
    onToggleArchived = {}, onOpen = {}, onDelete = {}, onArchive = { _, _ -> }, onCreateGroup = {},
    modifier = Modifier.fillMaxSize()
)

/** Every group archived: the line says so, the section stays below, closed. */
@Composable
fun GroupsAllArchivedCase() = com.kg.merapaisa.ui.groups.GroupsList(
    groups = emptyList(),
    archived = Fixtures.groupSummaries.map { it.copy(group = it.group.copy(archived = true)) },
    showArchived = false,
    onToggleArchived = {}, onOpen = {}, onDelete = {}, onArchive = { _, _ -> }, onCreateGroup = {},
    modifier = Modifier.fillMaxSize()
)

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

/**
 * A group after part of it was moved to your ledger (v3.7.0): a moved payment under "Payments
 * between members", and a new expense since, so you hold a position again, with the line saying
 * so and the button to move it too.
 */
@Composable
fun GroupDetailMovedCase() {
    val you = com.kg.merapaisa.data.Person(id = 7, name = "You", currency = "INR", isSelf = true)
    val members = listOf(you) + Fixtures.groupMembers.take(2)
    GroupDetailScreen(
        group = Fixtures.group,
        members = members,
        expenses = listOf(
            com.kg.merapaisa.data.Expense(id = 1, groupId = 1, description = "Hotel, two nights", amountMinor = 9_000_00, paidByPersonId = 7),
            com.kg.merapaisa.data.Expense(id = 2, groupId = 1, description = "Boat", amountMinor = 1_800_00, paidByPersonId = 7),
            com.kg.merapaisa.data.Expense(id = 3, groupId = 1, description = com.kg.merapaisa.data.MOVED_TO_LEDGER,
                amountMinor = 3_000_00, paidByPersonId = 1, isSettlement = true),
            com.kg.merapaisa.data.Expense(id = 4, groupId = 1, description = com.kg.merapaisa.data.MOVED_TO_LEDGER,
                amountMinor = 3_000_00, paidByPersonId = 2, isSettlement = true)
        ),
        shares = listOf(
            com.kg.merapaisa.data.ExpenseShare(3, 7, 3_000_00),
            com.kg.merapaisa.data.ExpenseShare(4, 7, 3_000_00)
        ),
        balances = listOf(
            com.kg.merapaisa.data.MemberBalance(7, 1_200_00),
            com.kg.merapaisa.data.MemberBalance(1, -600_00),
            com.kg.merapaisa.data.MemberBalance(2, -600_00)
        ),
        transfers = listOf(
            com.kg.merapaisa.data.Transfer(1, 7, 600_00),
            com.kg.merapaisa.data.Transfer(2, 7, 600_00)
        ),
        simplifyDebts = true,
        onBack = {}, onAddExpense = {}, onSettleUp = {},
        onDeleteExpense = {}, onSimplifyChange = {},
        onMoveToLedger = {}
    )
}

/**
 * You are even overall but not pair by pair, Fewest payments off (v3.7.1): Asha owes you and you
 * owe Ravi, so Settle up lists both and the move is offered under your zero.
 */
@Composable
fun GroupDetailEvenPairsCase() {
    val you = com.kg.merapaisa.data.Person(id = 7, name = "You", currency = "INR", isSelf = true)
    val members = listOf(you) + Fixtures.groupMembers.take(2)
    val (asha, other) = Fixtures.groupMembers.take(2).map { it.id }
    GroupDetailScreen(
        group = Fixtures.group.copy(simplifyDebts = false),
        members = members,
        expenses = listOf(
            com.kg.merapaisa.data.Expense(id = 1, groupId = 1, description = "Houseboat", amountMinor = 2_400_00, paidByPersonId = 7),
            com.kg.merapaisa.data.Expense(id = 2, groupId = 1, description = "Fuel", amountMinor = 2_400_00, paidByPersonId = other)
        ),
        balances = listOf(
            com.kg.merapaisa.data.MemberBalance(7, 0),
            com.kg.merapaisa.data.MemberBalance(asha, -1_200_00),
            com.kg.merapaisa.data.MemberBalance(other, 1_200_00)
        ),
        transfers = listOf(
            com.kg.merapaisa.data.Transfer(asha, 7, 1_200_00),
            com.kg.merapaisa.data.Transfer(7, other, 1_200_00)
        ),
        simplifyDebts = false,
        onBack = {}, onAddExpense = {}, onSettleUp = {},
        onDeleteExpense = {}, onSimplifyChange = {},
        onMoveToLedger = {}
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

// -- full screens ---------------------------------------------------------------------------

/** A day's worth of entries under each kind of heading, with a lakh figure in the column. */
@Composable
fun HistoryCase() = EntryHistoryContent(
    person = Fixtures.asha,
    entries = Fixtures.ashaHistory,
    onBack = {}, onEdit = {}, onDelete = {}, onReverse = {}, onClear = {}
)

@Composable
fun HistoryEmptyCase() = EntryHistoryContent(
    person = Fixtures.evenPerson,
    entries = emptyList(),
    onBack = {}, onEdit = {}, onDelete = {}, onReverse = {}, onClear = {}
)

/** Settings as it opens, the theme one row among the others. */
@Composable
fun SettingsCase() = SettingsScreen(
    people = Fixtures.mixedPeople,
    appLockEnabled = true,
    appLockAvailable = true,
    onAppLockChange = {},
    onImportLink = {},
    onBackupRestore = {},
    onExportCsv = {},
    canExport = true,
    onCheckUpdates = {},
    appVersion = "2.6.0",
    onDismiss = {},
    onApply = {}
)

/** Settings with your own UPI ID set. */
@Composable
fun SettingsUpiCase() = SettingsScreen(
    people = Fixtures.mixedPeople,
    appLockEnabled = true,
    appLockAvailable = true,
    onAppLockChange = {},
    onImportLink = {},
    onBackupRestore = {},
    onExportCsv = {},
    canExport = true,
    onCheckUpdates = {},
    appVersion = "3.6.0",
    onDismiss = {},
    onApply = {},
    myUpiId = "parth@example"
)

@Composable
private fun Backup(state: com.kg.merapaisa.ui.BackupFlowState) = BackupDialog(
    state = state,
    onSaveBackup = {}, onRestore = {}, onPickFolder = {}, onTurnOffAuto = {}, onBackUpNow = {},
    onModeChange = {}, onApply = {}, onDismiss = {}
)

@Composable
fun BackupMenuCase() = Backup(Fixtures.backupMenu)

/** Replace from a CSV export, so the lost-groups warning is on screen. */
@Composable
fun BackupReviewCase() = Backup(Fixtures.backupReview)

/** Merge from a full backup, where people already here with no photo get the file's. */
@Composable
fun BackupReviewMergeCase() = Backup(Fixtures.backupReviewMerge)

@Composable
fun BackupDoneCase() = Backup(Fixtures.backupDone)

@Composable
private fun Import(state: ImportFlowState) = ImportLedgerDialog(
    state = state,
    persons = Fixtures.mixedPeople,
    onTargetChange = {}, onNewPersonNameChange = {}, onToggleItem = {}, onApply = {},
    onPasteChange = {}, onPasteSubmit = {}, onDismiss = {}
)

@Composable
fun ImportPasteCase() = Import(ImportFlowState.Pasting())

/** One of every difference a comparison can find. */
@Composable
fun ImportConfirmCase() = Import(Fixtures.importConfirming)

@Composable
fun ImportHeldBackCase() = Import(Fixtures.importHeldBack)

@Composable
fun ImportDoneCase() = Import(Fixtures.importDone)

@Composable
private fun Update(state: UpdateFlowState) = UpdateScreen(
    state = state,
    onDownload = { _, _ -> }, onInstall = {}, onGrantPermission = {}, onDismiss = {}, onClose = {}
)

@Composable
fun UpdateAvailableCase() = Update(Fixtures.updateAvailable)

@Composable
fun UpdateDownloadingCase() = Update(UpdateFlowState.Downloading("2.6.0", 42))

@Composable
fun UpdateInstallerFailedCase() = Update(UpdateFlowState.Failed("", downloaded = true))

@Composable
fun LockedCase() = LockedScreen(onUnlock = {})

/**
 * The gallery's running order. The harness renders each of these in every theme, so this list is
 * the single place that decides what gets reviewed.
 */
/**
 * The launcher icon's one-colour layer (v3.7.1), as a themed icon draws it: the mark in the theme's
 * ink on a tinted circle, at a launcher's size and larger. Not on the launcher itself, where the
 * release app sits beside the debug one.
 */
@Composable
fun ThemedIconCase() {
    val theme = LocalAppTheme.current
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().background(theme.background)
            .padding(com.kg.merapaisa.ui.theme.Spacing.lg),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(com.kg.merapaisa.ui.theme.Spacing.lg),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        listOf(40, 64, 104).forEach { size ->
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .background(theme.highlight, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(com.kg.merapaisa.R.drawable.ic_launcher_monochrome),
                    contentDescription = null,
                    tint = theme.primary,
                    // The layer is 108dp with the icon's 72dp in its middle, as an adaptive icon is.
                    modifier = Modifier.requiredSize((size * 1.5f).dp)
                )
            }
        }
    }
}

val GalleryCases: List<Pair<String, @Composable () -> Unit>> = listOf(
    "icon-themed" to { ThemedIconCase() },
    "people-rows" to { PeopleRowsCase() },
    "people-rows-whole" to { PeopleRowsWholeCase() },
    "people-list-long" to { PeopleListLongCase() },
    "people-even" to { EvenPeopleCase() },
    "net-total-multi" to { NetTotalMultiCase() },
    "net-total-single" to { NetTotalSingleCase() },
    "net-total-multi-both-sides" to { NetTotalMultiCase(bothSides = true) },
    "net-total-single-both-sides" to { NetTotalSingleCase(bothSides = true) },
    "net-total-even" to { NetTotalEvenCase() },
    "numpad" to { NumPadCase() },
    "numpad-yen" to { NumPadYenCase() },
    "numpad-even" to { NumPadEvenCase() },
    "empty-active" to { EmptyActiveCase() },
    "empty-even" to { EmptyEvenCase() },
    "empty-active-everyone-even" to { EmptyActiveEveryoneEvenCase() },
    "empty-groups" to { EmptyGroupsCase() },
    "group-rows" to { GroupRowsCase() },
    "groups-archived" to { GroupsArchivedCase() },
    "group-detail-moved" to { GroupDetailMovedCase() },
    "groups-all-archived" to { GroupsAllArchivedCase() },
    "group-detail" to { GroupDetailCase() },
    "group-detail-even" to { GroupDetailEvenCase() },
    "group-detail-even-pairs" to { GroupDetailEvenPairsCase() },
    "split-amount" to { SplitAmountCase() },
    "split-picker" to { SplitPickerCase() },
    "split-picker-too-few" to { SplitPickerTooFewCase() },
    "split-adjustments" to { SplitAdjustmentsCase() },
    "split-adjustments-locked" to { SplitAdjustmentsLockedCase() },
    "history" to { HistoryCase() },
    "history-empty" to { HistoryEmptyCase() },
    "settings" to { SettingsCase() },
    "settings-upi" to { SettingsUpiCase() },
    "backup-menu" to { BackupMenuCase() },
    "backup-review" to { BackupReviewCase() },
    "backup-review-merge" to { BackupReviewMergeCase() },
    "backup-done" to { BackupDoneCase() },
    "import-paste" to { ImportPasteCase() },
    "import-confirm" to { ImportConfirmCase() },
    "import-held-back" to { ImportHeldBackCase() },
    "import-done" to { ImportDoneCase() },
    "update-available" to { UpdateAvailableCase() },
    "update-downloading" to { UpdateDownloadingCase() },
    "update-installer-failed" to { UpdateInstallerFailedCase() },
    "locked" to { LockedCase() },
    "walkthrough-1-welcome" to { WalkthroughCase(0) },
    "walkthrough-2-balances" to { WalkthroughCase(1) },
    "walkthrough-2-balances-end" to { WalkthroughCase(1, end = true) },
    "walkthrough-3-keypad" to { WalkthroughCase(2) },
    "walkthrough-3-keypad-end" to { WalkthroughCase(2, end = true) },
    "walkthrough-4-person" to { WalkthroughCase(3) },
    "walkthrough-4-person-end" to { WalkthroughCase(3, end = true) },
    "walkthrough-5-split" to { WalkthroughCase(4) },
    "walkthrough-5-split-end" to { WalkthroughCase(4, end = true) },
    "walkthrough-6-groups" to { WalkthroughCase(5) },
    "walkthrough-6-groups-end" to { WalkthroughCase(5, end = true) },
    "walkthrough-7-setup" to { WalkthroughCase(6) },
    "walkthrough-7-setup-replay" to { WalkthroughCase(6, replay = true) }
)

/**
 * One page of the walkthrough, as a first run shows it. [end] scrolls a tall page to its foot, so
 * the callouts under the picture are photographed too. Keyed, so moving from one page's case to
 * the next starts a fresh pager rather than keeping the last one's page.
 */
@Composable
fun WalkthroughCase(page: Int, end: Boolean = false, replay: Boolean = false) {
    androidx.compose.runtime.key(page, end, replay) {
        com.kg.merapaisa.ui.walkthrough.WalkthroughScreen(
            finishLabel = if (replay) "Back to Settings" else "Start",
            onFinish = {},
            setUp = {
                // A first run has nothing set; a replay from Settings, everything.
                com.kg.merapaisa.ui.walkthrough.WalkthroughSetUp(
                    appLockEnabled = replay,
                    appLockAvailable = true,
                    onAppLockChange = {},
                    myUpiId = if (replay) "parth@example" else null,
                    onMyUpiId = {},
                    backupFolder = if (replay) "Backups" else null,
                    onPickFolder = {}
                )
            },
            initialPage = page,
            scrolledToEnd = end
        )
    }
}

// -- sheets and dialogs -----------------------------------------------------------------------

/**
 * Everything that opens a window of its own: the bottom sheets and the dialogs.
 *
 * Kept apart from [GalleryCases] because `captureToImage` cannot see a second window. The window
 * harness captures the whole display instead, which is also what a person sees: the sheet or the
 * dialog, over the scrim, over the themed background.
 */
val GalleryWindowCases: List<Pair<String, @Composable () -> Unit>> = listOf(
    "sheet-add-person" to { AddPersonDialog(onDismiss = {}, onAdd = { _, _, _, _, _ -> }) },
    "sheet-edit-person" to {
        EditPersonDialog(
            person = Fixtures.chaitanya.person,
            converting = false,
            conversionError = null,
            onDismiss = {},
            onSave = { _, _, _, _, _, _ -> }
        )
    },
    "sheet-own-upi" to {
        com.kg.merapaisa.ui.dialogs.OwnUpiSheet(
            current = "parth@example", yourName = "Parth", onDismiss = {}, onSave = {}
        )
    },
    "sheet-own-upi-empty" to {
        com.kg.merapaisa.ui.dialogs.OwnUpiSheet(current = null, yourName = null, onDismiss = {}, onSave = {})
    },
    "sheet-reminder-qr" to {
        ReminderSheet(
            person = Fixtures.asha, entries = Fixtures.ashaHistory, onDismiss = {},
            myUpiId = "parth@example", myName = "Parth"
        )
    },
    "sheet-reminder" to {
        ReminderSheet(person = Fixtures.chaitanya, entries = Fixtures.ashaHistory, onDismiss = {})
    },
    // Asha owes you rupees and there is no UPI ID yet (v3.7.1): the row that adds one.
    "sheet-reminder-add-upi" to {
        ReminderSheet(
            person = Fixtures.asha, entries = Fixtures.ashaHistory, onDismiss = {},
            myUpiId = null, myName = "Parth", onAddUpiId = {}
        )
    },
    "sheet-share" to {
        ShareLedgerSheet(
            state = Fixtures.shareState,
            onSenderNameChange = {}, onFullHistoryChange = {}, onShare = {}, onDismiss = {}
        )
    },
    "sheet-move-debt" to {
        MoveDebtDialog(
            state = Fixtures.moveDebt,
            people = Fixtures.mixedPeople,
            onKey = {}, onTargetChange = {}, onNoteChange = {}, onConfirm = {}, onDismiss = {}
        )
    },
    "sheet-move-what-you-owe" to {
        MoveDebtDialog(
            state = Fixtures.moveWhatYouOwe,
            people = Fixtures.mixedPeople,
            onKey = {}, onTargetChange = {}, onNoteChange = {}, onConfirm = {}, onDismiss = {}
        )
    },
    "sheet-edit-entry" to {
        EditEntrySheet(
            entry = Fixtures.ashaHistory.first(),
            currency = "INR",
            onSave = {}, onDelete = {}, onDismiss = {}
        )
    },
    "sheet-create-group" to {
        CreateGroupDialog(
            people = Fixtures.mixedPeople,
            defaultCurrency = "INR",
            onDismiss = {},
            onCreate = { _, _, _, _ -> }
        )
    },
    // Nobody in the ledger yet: the "Add a new person" row is the whole list (v3.7.0).
    "sheet-create-group-empty" to {
        CreateGroupDialog(people = emptyList(), defaultCurrency = "INR", onDismiss = {}, onCreate = { _, _, _, _ -> })
    },
    // Everyone in a long ledger to choose from, so the list runs on under the foot and its edge shows.
    "sheet-create-group-long" to {
        CreateGroupDialog(
            people = Fixtures.manyPeople,
            defaultCurrency = "INR",
            onDismiss = {},
            onCreate = { _, _, _, _ -> }
        )
    },
    // Three members and four people who could join, two of them ticked: the sheet as it looks once
    // someone is being added, with the count in the heading.
    "sheet-edit-group" to {
        EditGroupSheet(
            group = Fixtures.group,
            members = Fixtures.mixedPeople.take(3).map { it.person },
            people = Fixtures.mixedPeople,
            onDismiss = {},
            onSave = { _, _, _ -> },
            initiallySelectedIds = setOf(Fixtures.dollarPerson.id, Fixtures.shortName.id),
            // The first member paid for something, the second shares in it, the third is free to go.
            expenses = listOf(
                com.kg.merapaisa.data.Expense(id = 1, groupId = Fixtures.group.id, description = "Hotel",
                    amountMinor = 1_000_00, paidByPersonId = Fixtures.mixedPeople[0].id)
            ),
            shares = listOf(
                com.kg.merapaisa.data.ExpenseShare(1, Fixtures.mixedPeople[0].id, 500_00),
                com.kg.merapaisa.data.ExpenseShare(1, Fixtures.mixedPeople[1].id, 500_00)
            )
        )
    },
    "sheet-add-expense" to {
        AddExpenseDialog(
            members = Fixtures.groupMembers,
            currency = "INR",
            selfId = 1,
            onDismiss = {},
            onSave = { _, _, _, _ -> }
        )
    },
    // An expense opened to change it, with shares that aren't equal (v3.7.0).
    "sheet-edit-expense" to {
        val ids = Fixtures.groupMembers.map { it.id }
        AddExpenseDialog(
            members = Fixtures.groupMembers,
            currency = "INR",
            selfId = 1,
            onDismiss = {},
            onSave = { _, _, _, _ -> },
            editing = com.kg.merapaisa.ui.groups.ExpenseDraft(
                description = "Dinner at Thalassa",
                amountMinor = 4_200_00,
                paidByPersonId = ids[1],
                shares = com.kg.merapaisa.data.evenShares(4_200_00, ids.take(3))
            )
        )
    },
    // An expense saved with unequal shares opens with "Adjust the shares" already on (v3.7.0).
    "sheet-edit-expense-unequal" to {
        val ids = Fixtures.groupMembers.map { it.id }
        AddExpenseDialog(
            members = Fixtures.groupMembers.take(3),
            currency = "INR",
            selfId = 1,
            onDismiss = {},
            onSave = { _, _, _, _ -> },
            editing = com.kg.merapaisa.ui.groups.ExpenseDraft(
                description = "Deposit for the flat",
                amountMinor = 30_000_00,
                paidByPersonId = ids[0],
                shares = mapOf(ids[0] to 15_000_00L, ids[1] to 10_000_00L, ids[2] to 5_000_00L)
            )
        )
    },
    // Moving your part of a group to your ledger (v3.7.0): an INR and a USD person, then the
    // same with the dollar rate missing.
    "sheet-move-to-ledger" to {
        com.kg.merapaisa.ui.groups.MoveToLedgerSheet(
            groupName = "Goa, October", groupCurrency = "INR",
            flow = com.kg.merapaisa.ui.MoveFlowState(
                groupId = 1,
                lines = listOf(
                    com.kg.merapaisa.ui.MoveLineState(1, "Asha", "INR", 3_000_00, 3_000_00),
                    com.kg.merapaisa.ui.MoveLineState(2, "Diego Hernández", "USD", -1_250_00, -14_92),
                    com.kg.merapaisa.ui.MoveLineState(3, "Chaitanya Venkataraman", "INR", 618_92, 618_92)
                )
            ),
            onRetry = {}, onDismiss = {}, onMove = {}
        )
    },
    "sheet-move-to-ledger-no-rate" to {
        com.kg.merapaisa.ui.groups.MoveToLedgerSheet(
            groupName = "Goa, October", groupCurrency = "INR",
            flow = com.kg.merapaisa.ui.MoveFlowState(
                groupId = 1,
                lines = listOf(
                    com.kg.merapaisa.ui.MoveLineState(1, "Asha", "INR", 3_000_00, 3_000_00),
                    com.kg.merapaisa.ui.MoveLineState(2, "Diego Hernández", "USD", -1_250_00, null)
                )
            ),
            onRetry = {}, onDismiss = {}, onMove = {}
        )
    },
    "sheet-settle-up" to {
        SettleUpSheet(
            transfers = Fixtures.groupTransfers,
            members = Fixtures.groupMembers,
            currency = "INR",
            onRecord = {},
            onDismiss = {}
        )
    },
    "dialog-delete-person" to {
        val person = Fixtures.chaitanya
        DecisionDialog(
            title = "Delete ${person.name}?",
            body = "Removes the ${amountString(person.balanceMinor, person.currency)} they owe you and their " +
                "23 entries. It also removes 2 group expenses they paid for, changing what the " +
                "other members of those groups owe. This can't be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Keep ${person.name}",
            destructive = true,
            onConfirm = {},
            onDismiss = {}
        )
    },
    // The sentences MainScreen, EditEntrySheet and GroupDetailScreen build for these cases.
    "sheet-pay-qr" to {
        com.kg.merapaisa.ui.pay.PayQrSheet(
            personName = "Asha", amountMinor = 1_200_00,
            link = com.kg.merapaisa.data.upiPayLink("parth@example", "Parth", 1_200_00),
            myUpiId = "parth@example", onNotYet = {}, onPaid = {}
        )
    },
    "dialog-settle-up" to {
        DecisionDialog(
            title = "Settle up with Asha?",
            body = "Records Asha paying you ${amountString(1_250_00, "INR")}, which squares you, and " +
                "moves them to Even.",
            confirmLabel = "Settle up",
            dismissLabel = "Not yet",
            onConfirm = {},
            onDismiss = {}
        )
    },
    "dialog-delete-entry" to {
        DecisionDialog(
            title = "Delete this entry?",
            body = "The ${amountString(340_00, "INR")} goes from the history, and the balance moves " +
                "back by that much. This can't be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Keep it",
            destructive = true,
            onConfirm = {},
            onDismiss = {}
        )
    },
    "dialog-delete-expense" to {
        DecisionDialog(
            title = "Delete Hotel?",
            body = "Removes this ${amountString(6_000_00, "INR")} expense from the group, and what its " +
                "members owe each other changes to match. This can't be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Keep it",
            destructive = true,
            onConfirm = {},
            onDismiss = {}
        )
    },
    "dialog-delete-group" to {
        DecisionDialog(
            title = "Delete Goa, October?",
            body = "Removes every expense and payment in it, and what its members owe each " +
                "other there. Balances on Active and Even don't change. This can't be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Keep the group",
            destructive = true,
            onConfirm = {},
            onDismiss = {}
        )
    },
    "dialog-clear-history" to {
        DecisionDialog(
            title = "Clear Asha's history?",
            body = "Deletes 14 entries. The ${amountString(1_200_00, "INR")} Asha owes you from them " +
                "is kept as an opening balance. This can't be undone.",
            confirmLabel = "Clear history",
            dismissLabel = "Keep the history",
            destructive = true,
            onConfirm = {},
            onDismiss = {}
        )
    },
    "dialog-import-deletions" to {
        DecisionDialog(
            title = "Delete 2 entries here?",
            body = "You ticked 2 entries the sender removed, so recording this takes them out of " +
                "your ledger too. This can't be undone.",
            confirmLabel = "Delete and record",
            dismissLabel = "Go back",
            destructive = true,
            onConfirm = {},
            onDismiss = {}
        )
    },
    // Not destructive: it adds a line and removes none, so its confirm keeps the accent.
    "dialog-reverse" to {
        DecisionDialog(
            title = "Reverse this entry and the newer ones?",
            body = "Adds one entry of ${amountString(340_00, "INR")} in their favour that cancels " +
                "this entry and the 2 newer ones, so Asha's balance goes back to what it was " +
                "before. Nothing is deleted: the old entries stay in the history.",
            confirmLabel = "Reverse entries",
            dismissLabel = "Don't reverse",
            onConfirm = {},
            onDismiss = {}
        )
    },
    "dialog-replace-ledger" to {
        DecisionDialog(
            title = "Replace your ledger?",
            // The sentence BackupDialog builds for these counts.
            body = "Deletes 9 people, 61 entries and 3 groups. This can't be undone.",
            warning = "A CSV export puts no groups back.",
            confirmLabel = "Replace my ledger",
            dismissLabel = "Keep my ledger",
            destructive = true,
            onConfirm = {},
            onDismiss = {}
        )
    },
    "dialog-convert-currency" to {
        ConvertCurrencyDialog(
            personName = "Asha",
            from = "INR",
            to = "USD",
            onConvert = {},
            onRelabel = {},
            onDismiss = {}
        )
    },
    // The theme picker as it opens, and trying the theme five along, across the line between dark
    // and light, so every theme is seen as the sheet and as the picture inside one.
    "sheet-theme" to {
        ThemeSheet(inUse = LocalAppTheme.current, people = Fixtures.mixedPeople, onDismiss = {}, onUse = {})
    },
    "sheet-theme-trying" to {
        val inUse = LocalAppTheme.current
        ThemeSheet(
            inUse = inUse,
            people = Fixtures.mixedPeople,
            onDismiss = {},
            onUse = {},
            startWith = themes[(themes.indexOf(inUse) + themes.size / 2) % themes.size].name
        )
    }
)

// -- Android Studio previews ---------------------------------------------------------------

@Preview(name = "People rows, Neel", heightDp = 620)
@Composable
private fun PreviewPeopleNeel() = GalleryFrame(themes.first { it.name == "Neel" }) { PeopleRowsCase() }

@Preview(name = "People rows, Diya", heightDp = 620)
@Composable
private fun PreviewPeopleDiya() = GalleryFrame(themes.first { it.name == "Diya" }) { PeopleRowsCase() }

@Preview(name = "Net position, Gulab")
@Composable
private fun PreviewNetTotalGulab() = GalleryFrame(themes.first { it.name == "Gulab" }) { NetTotalMultiCase() }

@Preview(name = "Group detail, Monsoon", heightDp = 800)
@Composable
private fun PreviewGroupMonsoon() = GalleryFrame(themes.first { it.name == "Monsoon" }) { GroupDetailCase() }

@Preview(name = "Numpad, Kamal", heightDp = 620)
@Composable
private fun PreviewNumPadKamal() = GalleryFrame(themes.first { it.name == "Kamal" }) { NumPadCase() }
