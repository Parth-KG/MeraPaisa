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
import com.kg.merapaisa.data.formatMinor
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
            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(16.dp, 16.dp, 16.dp, 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Three tabs plus two icons overflow a narrow screen, so the tabs scroll
                // rather than clip. On a wide screen this is invisible.
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Tab.entries.forEach { t ->
                        Button(
                            onClick = { viewModel.selectTab(t) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ui.tab == t) theme.primary else theme.fill,
                                contentColor = if (ui.tab == t) theme.background else theme.textSecondary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(t.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            viewModel.exportLedgerCsv { csv ->
                                scope.launch { shareCsv(context, writeExportToCache(context, csv)) }
                            }
                        },
                        enabled = persons.isNotEmpty(),
                        modifier = Modifier
                            .size(40.dp)
                            .background(theme.fill, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Export ledger as CSV",
                            tint = theme.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.showSettingsDialog(true) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(theme.fill, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = theme.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (ui.tab == Tab.Active) {
                NetTotalCard(
                    totals = netTotalsByCurrency(list),
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Groups get their own list; Active and Settled share the people list.
            if (ui.tab == Tab.Groups) {
                if (groups.isEmpty()) {
                    GroupsEmptyState(
                        modifier = Modifier
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groups, key = { it.group.id }) { summary ->
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
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                )
            } else LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.id }) { person ->
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
        // Bottom left - Add
        AnimatedVisibility(
            visible = ui.selectedId == null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Box(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = {
                        if (ui.tab == Tab.Groups) viewModel.showCreateGroupDialog(true)
                        else viewModel.showAddDialog(true)
                    },
                    modifier = Modifier
                        .size(65.dp)
                        .background(theme.positive, RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = theme.background)
                }
            }
        }

// Bottom right - Split
        AnimatedVisibility(
            visible = ui.selectedId == null && ui.tab != Tab.Groups,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Box(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = { viewModel.startSplit() },
                    modifier = Modifier
                        .size(65.dp)
                        .background(theme.card, RoundedCornerShape(16.dp))
                        .border(1.dp, theme.textSecondary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = "Split",
                        tint = theme.textPrimary
                    )
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
                Text(
                    "This permanently deletes ${target.name}, their balance of " +
                        "${formatMinor(target.balanceMinor, target.currency)}, and $entries." +
                        "$groupNote This can't be undone.",
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
            onApply = viewModel::applyImport,
            onPasteChange = viewModel::setPasteText,
            onPasteSubmit = viewModel::submitPaste,
            onDismiss = viewModel::dismissImport
        )
    }
}
