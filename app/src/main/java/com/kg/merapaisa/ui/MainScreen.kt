package com.kg.merapaisa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.data.parseAmountToMinor
import com.kg.merapaisa.ui.dialogs.AddPersonDialog
import com.kg.merapaisa.ui.dialogs.EditPersonDialog
import com.kg.merapaisa.ui.dialogs.MoveDebtDialog
import com.kg.merapaisa.ui.dialogs.ReminderDialog
import com.kg.merapaisa.CurrencyStore
import com.kg.merapaisa.ui.dialogs.CreateGroupDialog
import com.kg.merapaisa.ui.dialogs.SettingsDialog
import com.kg.merapaisa.ui.groups.AddExpenseDialog
import com.kg.merapaisa.ui.groups.GroupDetailScreen
import com.kg.merapaisa.ui.groups.GroupRow
import com.kg.merapaisa.ui.groups.SettleUpSheet
import com.kg.merapaisa.ui.groups.GroupsEmptyState
import com.kg.merapaisa.ui.dialogs.TransactionHistoryDialog
import com.kg.merapaisa.ui.share.ImportLedgerDialog
import com.kg.merapaisa.ui.share.ShareLedgerSheet
import com.kg.merapaisa.ui.backup.BackupDialog
import com.kg.merapaisa.ui.update.UpdateDialog
import com.kg.merapaisa.BuildConfig
import com.kg.merapaisa.update.UpdateInstaller
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import com.kg.merapaisa.data.backupFileName
import com.kg.merapaisa.backup.BackupWriter
import kotlinx.coroutines.launch
import com.kg.merapaisa.SecurityStore
import com.kg.merapaisa.ThemeStore
import com.kg.merapaisa.data.netTotalsByCurrency
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.tween
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.unit.Dp
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Motion
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.RowDivider

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val persons by viewModel.persons.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val ui by viewModel.uiState.collectAsState()

    // The split flow is part of this tree rather than a Dialog, so back has to be handled
    // here — otherwise it would fall through and close the app mid-split.
    BackHandler(enabled = ui.split != null) {
        val split = ui.split
        if (split != null && split.step > 0) {
            viewModel.updateSplit { it.copy(step = it.step - 1) }
        } else {
            viewModel.cancelSplit()
        }
    }
    BackHandler(enabled = ui.split == null && ui.selectedId != null) { viewModel.clearSelection() }

    // A settled debt is one you have marked settled, not merely one that nets to zero —
    // otherwise everyone you add lands in Settled the moment they are created.
    val activePersons = persons.filter { !it.isSettled }
    val settledPersons = persons.filter { it.isSettled }
    val list = if (ui.tab == Tab.Active) activePersons else settledPersons
    val selectedPerson = persons.find { it.id == ui.selectedId }
    val editingPerson = persons.find { it.id == ui.editingPersonId }
    val historyPerson = persons.find { it.id == ui.historyPersonId }
    val pendingDelete = persons.find { it.id == ui.pendingDeleteId }
    val pendingReminder = persons.find { it.id == ui.pendingReminderId }

    Box(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // A real tab row. These were pill buttons, which look like three things you can
            // press rather than one place you are currently in, and gave no sense of which of
            // the three you were looking at beyond a fill colour.
            PrimaryTabRow(
                selectedTabIndex = Tab.entries.indexOf(ui.tab),
                containerColor = theme.background,
                contentColor = theme.primary,
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                ),
                divider = {},
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(Tab.entries.indexOf(ui.tab)),
                        width = Dp.Unspecified,
                        color = theme.primary
                    )
                }
            ) {
                Tab.entries.forEach { tab ->
                    androidx.compose.material3.Tab(
                        selected = ui.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        selectedContentColor = theme.textPrimary,
                        unselectedContentColor = theme.textSecondary,
                        text = { Text(tab.name, style = MeraPaisaType.action) }
                    )
                }
            }

            // Settings is the only thing in the corner now. Exporting a CSV was an unlabelled
            // share icon sitting beside it, which is a filing job, not something you reach for
            // while looking at a balance: it lives in Settings with backup and restore.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = Spacing.sm),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { viewModel.showSettingsDialog(true) }) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = theme.textSecondary
                    )
                }
            }

            if (ui.tab == Tab.Active) {
                NetPosition(
                    totals = netTotalsByCurrency(list),
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Groups get their own list; Active and Settled share the people list.
            if (ui.tab == Tab.Groups) {
                if (groups.isEmpty()) {
                    GroupsEmptyState(
                        modifier = Modifier
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                        onCreateGroup = { viewModel.showCreateGroupDialog(true) }
                    )
                } else {
                    LazyColumn(
                        // No horizontal padding here: GroupRow pads to the gutter itself, as
                        // PersonRow does, and RowDivider measures its inset from the list edge.
                        modifier = Modifier
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                        contentPadding = PaddingValues(bottom = Spacing.xxl)
                    ) {
                        itemsIndexed(groups, key = { _, it -> it.group.id }) { index, summary ->
                            if (index > 0) RowDivider()
                            GroupRow(
                                summary = summary,
                                onClick = { viewModel.openGroup(summary.group.id) },
                                onDelete = { viewModel.deleteGroup(summary.group.id) }
                            )
                        }
                    }
                }
            } else if (list.isEmpty()) {
                EmptyState(
                    tab = ui.tab,
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    onAddPerson = { viewModel.showAddDialog(true) }
                )
            } else LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(bottom = Spacing.xxl)
            ) {
                itemsIndexed(list, key = { _, it -> it.id }) { index, person ->
                    if (index > 0) RowDivider()
                    PersonRow(
                        person = person,
                        isSelected = ui.selectedId == person.id,
                        onClick = { viewModel.togglePerson(person.id) },
                        onDelete = { viewModel.confirmDelete(person.id) },
                        onSendReminder = { viewModel.composeReminder(person.id) },
                        onEditClick = { viewModel.editPerson(person.id) },
                        onHistoryClick = { viewModel.showHistory(person.id) },
                        onSettleToggle = {
                            if (person.isSettled) viewModel.reopenPerson(person) else viewModel.settlePerson(person)
                        },
                        onShareSummary = {
                            viewModel.personSummary(person) { text ->
                                shareText(context, text, "Share summary via")
                            }
                        },
                        onShareLedger = { viewModel.openShareSheet(person.id) },
                        onMoveDebt = { viewModel.openMoveDebt(person.id) }
                    )
                }
            }

            // Numpad
            if (selectedPerson != null && ui.split == null && ui.tab != Tab.Groups) {
                NumPad(
                    person = selectedPerson,
                    input = ui.input,
                    onSettleToggle = {
                        if (selectedPerson.isSettled) {
                            viewModel.reopenPerson(selectedPerson)
                        } else {
                            viewModel.settlePerson(selectedPerson)
                        }
                    },
                    note = ui.note,
                    onNoteChange = viewModel::setNote,
                    showNote = ui.showNote,
                    onToggleNote = viewModel::toggleNoteField,
                    onKey = viewModel::onKeyPress,
                    onAdd = {
                        val amount = parseAmountToMinor(ui.input) ?: return@NumPad
                        viewModel.recordAmount(selectedPerson.id, amount, ui.note)
                        viewModel.clearSelection()
                    },
                    onSubtract = {
                        val amount = parseAmountToMinor(ui.input) ?: return@NumPad
                        viewModel.recordAmount(selectedPerson.id, -amount, ui.note)
                        viewModel.clearSelection()
                    }
                )
            }
        }
        // One labelled action, plus Split where it applies.
        //
        // These were two identical 65dp icon-only squares in opposite bottom corners: a plus on
        // the left and a fork on the right, the same size and the same shape, neither saying what
        // it did. Which one added a person and which one started a split was something you had to
        // learn. Both are named now, and they sit together, because a thumb reaching the bottom
        // of the screen should find the actions in one place rather than two.
        AnimatedVisibility(
            visible = ui.selectedId == null,
            enter = fadeIn(tween(Motion.quick)),
            exit = fadeOut(tween(Motion.quick)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (ui.tab == Tab.Groups) viewModel.showCreateGroupDialog(true)
                        else viewModel.showAddDialog(true)
                    },
                    shape = Shapes.small,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.primary,
                        contentColor = theme.background
                    )
                ) {
                    Text(
                        if (ui.tab == Tab.Groups) "New group" else "Add a person",
                        style = MeraPaisaType.action
                    )
                }

                if (ui.tab != Tab.Groups) {
                    OutlinedButton(
                        onClick = { viewModel.startSplit() },
                        shape = Shapes.small,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        border = BorderStroke(1.dp, theme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textPrimary)
                    ) {
                        Text("Split a bill", style = MeraPaisaType.action)
                    }
                }
            }
        }

        if (ui.showCreateGroupDialog) {
            val lastCurrency by CurrencyStore.getLastCurrency(context).collectAsState(initial = "INR")
            CreateGroupDialog(
                people = persons,
                defaultCurrency = lastCurrency,
                onDismiss = { viewModel.showCreateGroupDialog(false) },
                onCreate = viewModel::createGroup
            )
        }
        if (ui.showAddDialog) {
            AddPersonDialog(
                onDismiss = { viewModel.showAddDialog(false) },
                onAdd = { name, pfpType, pfpValue, pfpColor, currency ->
                    viewModel.addPerson(name, pfpType, pfpValue, pfpColor, currency) { newId ->
                        viewModel.showAddDialog(false)
                        viewModel.selectTab(Tab.Active)
                        viewModel.togglePerson(newId)
                    }
                }
            )
        }
        if (editingPerson != null) {
            val converting by viewModel.converting.collectAsState()
            val conversionError by viewModel.conversionError.collectAsState()
            EditPersonDialog(
                person = editingPerson.person,
                converting = converting,
                conversionError = conversionError,
                onDismiss = {
                    viewModel.dismissConversionError()
                    viewModel.editPerson(null)
                },
                onSave = { name, pfpType, pfpValue, pfpColor, currency, shouldConvert ->
                    viewModel.savePersonEdit(
                        snapshot = editingPerson,
                        name = name,
                        pfpType = pfpType,
                        pfpValue = pfpValue,
                        pfpColor = pfpColor,
                        currency = currency,
                        convertBalance = shouldConvert
                    ) { viewModel.editPerson(null) }
                }
            )
        }
        if (historyPerson != null) {
            TransactionHistoryDialog(
                person = historyPerson,
                viewModel = viewModel,
                onDismiss = { viewModel.showHistory(null) }
            )
        }
        if (ui.showSettingsDialog) {
            val appLockEnabled by SecurityStore.isAppLockEnabled(context).collectAsState(initial = false)
            SettingsDialog(
                currentThemeName = theme.name,
                appLockEnabled = appLockEnabled,
                appLockAvailable = remember { canAuthenticate(context) },
                onImportLink = viewModel::openPasteImport,
                canExport = persons.isNotEmpty(),
                onExportCsv = {
                    viewModel.exportLedgerCsv { csv ->
                        scope.launch { shareCsv(context, writeExportToCache(context, csv)) }
                    }
                },
                onBackupRestore = viewModel::openBackupScreen,
                onCheckUpdates = viewModel::checkForUpdatesNow,
                appVersion = BuildConfig.VERSION_NAME,
                onAppLockChange = { enabled ->
                    scope.launch { SecurityStore.setAppLockEnabled(context, enabled) }
                },
                onDismiss = { viewModel.showSettingsDialog(false) },
                onApply = { selectedTheme ->
                    scope.launch { ThemeStore.setTheme(context, selectedTheme) }
                    viewModel.showSettingsDialog(false)
                }
            )
        }
    }
    val openGroupId = ui.openGroupId
    if (openGroupId != null) {
        val detail by viewModel.openGroup.collectAsState()
        val selfId by viewModel.selfId.collectAsState()
        val summary = groups.firstOrNull { it.group.id == openGroupId }

        BackHandler(enabled = true) { viewModel.openGroup(null) }

        if (summary != null && detail != null) {
            val loaded = detail!!
            GroupDetailScreen(
                group = summary.group,
                members = loaded.members,
                expenses = loaded.expenses,
                balances = loaded.balances,
                transfers = loaded.transfers,
                simplifyDebts = loaded.simplifyDebts,
                onBack = { viewModel.openGroup(null) },
                onAddExpense = { viewModel.showAddExpenseDialog(true) },
                onSettleUp = { viewModel.showSettleUp(true) },
                onDeleteExpense = viewModel::deleteExpense,
                onSimplifyChange = viewModel::setSimplifyDebts
            )

            if (ui.showAddExpenseDialog) {
                AddExpenseDialog(
                    members = loaded.members,
                    currency = summary.group.currency,
                    selfId = selfId,
                    onDismiss = { viewModel.showAddExpenseDialog(false) },
                    onAdd = viewModel::addExpense
                )
            }
            if (ui.showSettleUp) {
                SettleUpSheet(
                    balances = loaded.balances,
                    members = loaded.members,
                    currency = summary.group.currency,
                    onRecord = { t -> viewModel.recordTransfer(t.fromPersonId, t.toPersonId, t.amountMinor) },
                    onDismiss = { viewModel.showSettleUp(false) }
                )
            }
        }
    }

    ui.split?.let { split ->
        when (split.step) {
            0 -> SplitAmountScreen(
                amount = split.amount,
                currency = split.currency,
                onCurrencyChange = { code -> viewModel.updateSplit { it.copy(currency = code) } },
                onAmountChange = { entry -> viewModel.updateSplit { it.copy(amount = entry) } },
                onCancel = viewModel::cancelSplit,
                onNext = { viewModel.updateSplit { it.copy(step = 1) } }
            )
            1 -> SplitPickerScreen(
                allPersons = persons,
                selectedIds = split.selectedIds,
                includeMe = split.includeMe,
                onToggleMe = { viewModel.updateSplit { it.copy(includeMe = !it.includeMe) } },
                onTogglePerson = { id ->
                    viewModel.updateSplit {
                        val next = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
                        it.copy(selectedIds = next)
                    }
                },
                onAddPerson = { viewModel.showAddDialog(true) },
                onBack = { viewModel.updateSplit { it.copy(step = 0) } },
                onCancel = viewModel::cancelSplit,
                onNext = { viewModel.updateSplit { it.copy(step = 2) } }
            )
            else -> SplitAdjustmentsScreen(
                viewModel = viewModel,
                amountMinor = parseAmountToMinor(split.amount) ?: 0L,
                sourceCurrency = split.currency,
                selectedPersons = persons.filter { it.id in split.selectedIds },
                includeMe = split.includeMe,
                note = split.note,
                onNoteChange = { entry -> viewModel.updateSplit { it.copy(note = entry) } },
                onBack = { viewModel.updateSplit { it.copy(step = 1) } },
                onCancel = viewModel::cancelSplit,
                onConfirm = { perPersonAmounts ->
                    // person id -> amount in that person's own currency, as minor units
                    viewModel.recordSplit(perPersonAmounts, split.note.ifBlank { "Split" })
                    viewModel.cancelSplit()
                }
            )
        }
    }
    }

    pendingReminder?.let { target ->
        ReminderDialog(
            person = target,
            viewModel = viewModel,
            onDismiss = { viewModel.composeReminder(null) }
        )
    }
    pendingDelete?.let { target ->
        val transactionCount by viewModel.getTransactionCount(target.id).collectAsState(initial = 0)
        val groupExpenseCount by viewModel.getGroupExpenseCount(target.id).collectAsState(initial = 0)
        val groupSharedCount by viewModel.getGroupSharedCount(target.id).collectAsState(initial = 0)
        AlertDialog(
            onDismissRequest = { viewModel.confirmDelete(null) },
                title = {
                Text("Delete ${target.name}?", color = theme.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                val entries = if (transactionCount == 1) "1 transaction" else "$transactionCount transactions"
                // Deleting a person cascades away the group expenses they fronted, which moves
                // what every other member of those groups owes. That is too large a consequence
                // to leave out of the sentence asking for confirmation.
                val groupNote = when (groupExpenseCount) {
                    0 -> ""
                    1 -> " It also removes 1 group expense they paid for, changing what the " +
                        "other members of that group owe."
                    else -> " It also removes $groupExpenseCount group expenses they paid for, " +
                        "changing what the other members of those groups owe."
                }
                // The other half of the same consequence: expenses they merely shared stay, and
                // their part of them passes to whoever paid, who then absorbs what can no longer
                // be collected. Silently moving what a third person is owed is exactly as large a
                // consequence as removing an expense outright.
                val sharedNote = when (groupSharedCount) {
                    0 -> ""
                    1 -> " Their share of 1 group expense someone else paid for passes to whoever " +
                        "paid it."
                    else -> " Their share of $groupSharedCount group expenses other people paid " +
                        "for passes to whoever paid them."
                }
                Text(
                    "This permanently deletes ${target.name}, their balance of " +
                        "${amountString(target.balanceMinor, target.currency)}, and $entries." +
                        "$groupNote$sharedNote This can't be undone.",
                    color = theme.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePerson(target.person)
                    viewModel.confirmDelete(null)
                }) {
                    Text("Delete", color = theme.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmDelete(null) }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }

    // The outgoing half of the two-sided ledger. Building the message advances the share
    // watermark, so the Context work stays here and the ViewModel only hands back a String.
    ui.share?.let { share ->
        ShareLedgerSheet(
            state = share,
            onSenderNameChange = viewModel::setShareSenderName,
            onFullHistoryChange = viewModel::setShareFullHistory,
            onShare = {
                viewModel.prepareShareMessage { message ->
                    shareText(context, message, "Share ledger update via")
                }
            },
            onDismiss = viewModel::closeShareSheet
        )
    }

    // The incoming half. Rendered here rather than as its own screen so it sits above whatever
    // the user was already doing, and behind the app lock like everything else.
    // System file pickers. They have to live in the composable — a ViewModel cannot launch one —
    // so each hands the chosen uri straight back and does no work of its own.
    val saveBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> viewModel.writeBackupTo(uri?.toString()) }

    val openRestoreLauncher = rememberLauncherForActivityResult(
        // Both shapes, because a restore accepts a full backup or a ledger CSV. Some file
        // providers report JSON as octet-stream, so that is accepted rather than hiding the file
        // the user is looking straight at.
        ActivityResultContracts.OpenDocument()
    ) { uri -> viewModel.readRestoreFrom(uri?.toString()) }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Without taking the permission persistably, the weekly job loses access to the folder
            // the moment this process dies — which is exactly when it needs it.
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        viewModel.setBackupFolder(uri?.toString())
    }

    // The quiet once-a-day check. Runs on first composition only, and stays silent unless there
    // is genuinely a newer release — an app that interrupts you to say nothing has changed is
    // worse than one that never looks.
    LaunchedEffect(Unit) { viewModel.checkForUpdatesQuietly() }

    ui.moveDebt?.let { move ->
        MoveDebtDialog(
            state = move,
            people = persons,
            onKey = viewModel::onMoveDebtKey,
            onTargetChange = viewModel::setMoveDebtTarget,
            onNoteChange = viewModel::setMoveDebtNote,
            onConfirm = viewModel::confirmMoveDebt,
            onDismiss = viewModel::closeMoveDebt
        )
    }

    ui.update?.let { updateState ->
        UpdateDialog(
            state = updateState,
            onDownload = viewModel::downloadUpdate,
            onInstall = viewModel::installDownloadedUpdate,
            onGrantPermission = {
                context.startActivity(UpdateInstaller.installPermissionIntent(context))
                viewModel.closeUpdate()
            },
            onDismiss = viewModel::dismissUpdate,
            onClose = viewModel::closeUpdate
        )
    }

    ui.backup?.let { backupState ->
        BackupDialog(
            state = backupState,
            onSaveBackup = {
                saveBackupLauncher.launch(backupFileName(BackupWriter.stamp(System.currentTimeMillis())))
            },
            onRestore = {
                openRestoreLauncher.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"))
            },
            onPickFolder = { pickFolderLauncher.launch(null) },
            onTurnOffAuto = viewModel::turnOffAutomaticBackups,
            onBackUpNow = viewModel::backUpNow,
            onModeChange = viewModel::setRestoreMode,
            onApply = viewModel::applyRestore,
            onDismiss = viewModel::closeBackupScreen
        )
    }

    ui.import?.let { incoming ->
        ImportLedgerDialog(
            state = incoming,
            persons = persons,
            onTargetChange = viewModel::setImportTarget,
            onNewPersonNameChange = viewModel::setImportNewPersonName,
            onToggleItem = viewModel::toggleReconcileItem,
            onApply = viewModel::applyImport,
            onPasteChange = viewModel::setPasteText,
            onPasteSubmit = viewModel::submitPaste,
            onDismiss = viewModel::dismissImport
        )
    }
}
