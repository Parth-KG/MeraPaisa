package com.kg.merapaisa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Palette
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
import com.kg.merapaisa.ui.dialogs.ReminderDialog
import com.kg.merapaisa.ui.dialogs.ThemePickerDialog
import com.kg.merapaisa.ui.dialogs.TransactionHistoryDialog
import kotlinx.coroutines.launch
import com.kg.merapaisa.ThemeStore
import com.kg.merapaisa.data.netTotalsByCurrency

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val persons by viewModel.persons.collectAsState()
    val ui by viewModel.uiState.collectAsState()

    BackHandler(enabled = ui.selectedId != null) { viewModel.clearSelection() }

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

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(24.dp, 16.dp, 24.dp, 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tab.entries.forEach { t ->
                        Button(
                            onClick = { viewModel.selectTab(t) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ui.tab == t) theme.primary else theme.fill,
                                contentColor = if (ui.tab == t) theme.background else theme.textSecondary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 7.dp)
                        ) {
                            Text(t.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                IconButton(
                    onClick = { viewModel.showThemeDialog(true) },
                    modifier = Modifier
                        .size(40.dp)
                        .background(theme.fill, CircleShape)
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = "Theme",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
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

            // People list
            if (list.isEmpty()) {
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
                        }
                    )
                }
            }

            // Numpad
            if (selectedPerson != null && ui.split == null) {
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
                    onClick = { viewModel.showAddDialog(true) },
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
            visible = ui.selectedId == null,
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
        if (ui.showThemeDialog) {
            ThemePickerDialog(
                currentThemeName = theme.name,
                onDismiss = { viewModel.showThemeDialog(false) },
                onApply = { selectedTheme ->
                    scope.launch { ThemeStore.setTheme(context, selectedTheme) }
                    viewModel.showThemeDialog(false)
                }
            )
        }
    }
    ui.split?.let { split ->
        when (split.step) {
            0 -> SplitAmountScreen(
                amount = split.amount,
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
                sourceCurrency = "INR",
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
    pendingReminder?.let { target ->
        ReminderDialog(
            person = target,
            viewModel = viewModel,
            onDismiss = { viewModel.composeReminder(null) }
        )
    }
    pendingDelete?.let { target ->
        val transactionCount by viewModel.getTransactionCount(target.id).collectAsState(initial = 0)
        AlertDialog(
            onDismissRequest = { viewModel.confirmDelete(null) },
                title = {
                Text("Delete ${target.name}?", color = theme.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                val entries = if (transactionCount == 1) "1 transaction" else "$transactionCount transactions"
                Text(
                    "This permanently deletes ${target.name}, their balance of " +
                        "${formatMinor(target.balanceMinor, target.currency)}, and $entries. " +
                        "This can't be undone.",
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
}
