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
import com.kg.merapaisa.ui.groups.SettleUpSheet
import com.kg.merapaisa.ui.share.ImportLedgerDialog
import com.kg.merapaisa.ui.share.ShareLedgerSheet
import com.kg.merapaisa.ui.theme.MeraPaisaTheme
import com.kg.merapaisa.ui.update.UpdateScreen

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

/** Yen has no fractions, so its keypad has no point: the slot stays empty beside the 0. */
@Composable
fun NumPadYenCase() {
    NumPad(
        person = Fixtures.asha.copy(person = Fixtures.asha.person.copy(currency = "JPY"), balanceMinor = 12_500_00),
        input = "1250",
        onKey = {}, onSettleToggle = {}, onAdd = {}, onSubtract = {},
        note = "", onNoteChange = {}, showNote = false, onToggleNote = {}
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
            if (index > 0) RowDivider(TextRowInset)
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

/** The theme picker has to show every theme's own colours, whichever theme it is drawn in. */
@Composable
fun SettingsCase() = SettingsScreen(
    currentThemeName = LocalAppTheme.current.name,
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
val GalleryCases: List<Pair<String, @Composable () -> Unit>> = listOf(
    "people-rows" to { PeopleRowsCase() },
    "people-list-long" to { PeopleListLongCase() },
    "people-settled-and-even" to { SettledPersonCase() },
    "net-total-multi" to { NetTotalMultiCase() },
    "net-total-single" to { NetTotalSingleCase() },
    "net-total-even" to { NetTotalEvenCase() },
    "numpad" to { NumPadCase() },
    "numpad-yen" to { NumPadYenCase() },
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
    "split-adjustments-locked" to { SplitAdjustmentsLockedCase() },
    "history" to { HistoryCase() },
    "history-empty" to { HistoryEmptyCase() },
    "settings" to { SettingsCase() },
    "backup-menu" to { BackupMenuCase() },
    "backup-review" to { BackupReviewCase() },
    "backup-done" to { BackupDoneCase() },
    "import-paste" to { ImportPasteCase() },
    "import-confirm" to { ImportConfirmCase() },
    "import-held-back" to { ImportHeldBackCase() },
    "import-done" to { ImportDoneCase() },
    "update-available" to { UpdateAvailableCase() },
    "update-downloading" to { UpdateDownloadingCase() },
    "update-installer-failed" to { UpdateInstallerFailedCase() },
    "locked" to { LockedCase() }
)

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
    "sheet-reminder" to {
        ReminderSheet(person = Fixtures.chaitanya, entries = Fixtures.ashaHistory, onDismiss = {})
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
    "sheet-add-expense" to {
        AddExpenseDialog(
            members = Fixtures.groupMembers,
            currency = "INR",
            selfId = 1,
            onDismiss = {},
            onAdd = { _, _, _, _ -> }
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
            onConfirm = {},
            onDismiss = {}
        )
    },
    // The sentences MainScreen, EditEntrySheet and GroupDetailScreen build for these cases.
    "dialog-settle-up" to {
        DecisionDialog(
            title = "Settle up with Asha?",
            body = "Records Asha paying you ${amountString(1_250_00, "INR")}, which squares you, and " +
                "moves them to Settled. Reopening them later doesn't undo the payment.",
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
    }
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
