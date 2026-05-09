package com.merapaisa

import android.net.Uri
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Close
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.clickable
import com.merapaisa.data.Transaction
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.filled.Undo
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Info
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.merapaisa.data.Person


fun formatAmount(balance: Double, currency: String = "₹"): String {
    val abs = "%.0f".format(Math.abs(balance))
    return if (balance < 0) "-$currency$abs" else "$currency$abs"
}

data class SplitParticipant(
    val id: Long,
    val name: String,
    val currency: String
)


private fun equalSplit(amount: Double, participants: List<SplitParticipant>): Map<Long, Double> {
    if (participants.isEmpty()) return emptyMap()
    val cents = (amount * 100).toLong()
    val baseCents = cents / participants.size
    val remainder = cents - (baseCents * participants.size)
    return participants.mapIndexed { i, p ->
        val withRemainder = if (i == 0) baseCents + remainder else baseCents
        p.id to withRemainder / 100.0
    }.toMap()
}

private fun redistribute(
    current: Map<Long, Double>,
    locked: Set<Long>,
    changedId: Long,
    newValue: Double,
    total: Double
): Map<Long, Double> {
    val updated = current.toMutableMap()
    updated[changedId] = newValue

    val lockedSum = updated.filterKeys { it in locked }.values.sum()
    val unlockedIds = updated.keys.filter { it !in locked }
    if (unlockedIds.isEmpty()) return updated

    val remaining = total - lockedSum
    val perUnlocked = remaining / unlockedIds.size

    unlockedIds.forEach { id ->
        updated[id] = kotlin.math.max(0.0, perUnlocked)
    }
    return updated
}
fun saveImageToInternalStorage(context: Context, uri: Uri): String {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
    val file = java.io.File(context.filesDir, "pfp_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { inputStream.copyTo(it) }
    return file.absolutePath
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var pendingReminder by remember { mutableStateOf<Person?>(null) }
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Person?>(null) }
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    var showSplitFlow by remember { mutableStateOf(false) }
    var splitAmount by remember { mutableStateOf("") }
    var splitStep by remember { mutableStateOf(0) }
    val persons by viewModel.persons.collectAsState()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var input by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showNote by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("active") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<Person?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var historyPerson by remember { mutableStateOf<Person?>(null) }
    var pd = 16
    BackHandler(enabled = selectedId != null) {
        selectedId = null
        input = ""
    }
    var showThemeDialog by remember { mutableStateOf(false) }
    var splitNote by remember { mutableStateOf("") }
    var splitSelectedIds by remember { mutableStateOf(setOf<Long>()) }
    var splitIncludeMe by remember { mutableStateOf(false) }
    val activePersons = persons.filter { !it.isSettled && it.balance != 0.0 }
    val settledPersons = persons.filter { it.isSettled || it.balance == 0.0 }
    val list = if (tab == "active") activePersons else settledPersons
    val selectedPerson = persons.find { it.id == selectedId }
    if (isGestureNavigation()) {
        pd = 16
    } else {
        pd = 40
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = pd.dp)) {
            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp, 48.dp, 24.dp, 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("active", "settled").forEach { t ->
                        Button(
                            onClick = { tab = t; selectedId = null; input = "" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tab == t) Color.White else Color.White.copy(alpha = 0.06f),
                                contentColor = if (tab == t) Color.Black else theme.textSecondary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 7.dp)
                        ) {
                            Text(t.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                IconButton(
                    onClick = { showThemeDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.06f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = "Theme",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (tab == "active") {
                val lastCurrency by CurrencyStore.getLastCurrency(context).collectAsState(initial = "INR")
                var netTotal by remember { mutableStateOf(0.0) }
                var loading by remember { mutableStateOf(false) }

                LaunchedEffect(list, lastCurrency) {
                    loading = true
                    netTotal = list.sumOf { person ->
                        if (person.currency == lastCurrency) person.balance
                        else viewModel.convertCurrency(person.balance, person.currency, lastCurrency) ?: 0.0
                    }
                    loading = false
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = when {
                            loading -> "Calculating..."
                            netTotal > 0 -> "Net owed to you"
                            netTotal < 0 -> "Net you owe"
                            else -> "All settled"
                        },
                        fontSize = 12.sp,
                        color = theme.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatAmount(kotlin.math.abs(netTotal), lastCurrency),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            netTotal > 0 -> Color(0xFF4ADE80)
                            netTotal < 0 -> Color(0xFFF87171)
                            else -> theme.textSecondary
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // People list
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 100.dp),  // <-- comma here
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.id }) { person ->
                    PersonRow(
                        person = person,
                        isSelected = selectedId == person.id,
                        onClick = {
                            selectedId = if (selectedId == person.id) null else person.id
                            input = ""
                        },
                        onDelete = { pendingDelete = person },
                        onSendReminder = { pendingReminder = person },
                        onEditClick = {
                            editingPerson = person
                            showEditDialog = true
                        },
                        onHistoryClick = {
                            historyPerson = person
                            showHistoryDialog = true
                        }
                    )
                }
            }

            // Numpad
            if (selectedPerson != null&& !showSplitFlow) {
                NumPad(
                    person = selectedPerson,
                    pd = pd,
                    input = input,
                    onSettle = {
                        viewModel.settlePerson(selectedPerson, context)
                        tab = "settled"
                        selectedId = null     // close numpad after settling
                        input = ""
                        note = ""
                    },
                    note = note,
                    onNoteChange = { note = it },
                    showNote = showNote,
                    onToggleNote = { showNote = !showNote },
                    onKey = { k ->
                        when (k) {
                            "⌫" -> input = input.dropLast(1)
                            "." -> if (!input.contains(".")) input += "."
                            else -> if (input.length < 8) input += k
                        }
                    },
                    onAdd = {
                        val amt = input.toDoubleOrNull() ?: return@NumPad
                        val person = selectedPerson ?: return@NumPad
                        val newBalance = person.balance + amt
                        viewModel.updateBalance(person, amt)
                        if (kotlin.math.abs(newBalance) < 0.005) {
                            tab = "settled"
                            selectedId = null
                        } else if (tab == "settled") {
                            tab = "active"
                        }
                        selectedId = null
                        input = ""
                        note = ""
                    },
                    onSubtract = {
                        val amt = input.toDoubleOrNull() ?: return@NumPad
                        val person = selectedPerson ?: return@NumPad
                        val newBalance = person.balance - amt
                        viewModel.updateBalance(person, -amt )
                        if (kotlin.math.abs(newBalance) < 0.005) {
                            tab = "settled"
                            selectedId = null
                        } else if (tab == "settled") {
                            tab = "active"
                        }
                        selectedId = null
                        input = ""
                        note = ""
                    }
                )
            }
        }
        // Bottom left - Add
        AnimatedVisibility(
            visible = selectedId == null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Box(
                modifier = Modifier.padding(pd.dp)
            ) {
                IconButton(
                    onClick = { showAddDialog = true },
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
            visible = selectedId == null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Box(
                modifier = Modifier.padding(pd.dp)
            ) {
                IconButton(
                    onClick = { showSplitFlow = true },
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

        if (showAddDialog) {
            AddPersonDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { name, pfpType, pfpValue, pfpColor, currency ->
                    viewModel.addPerson(name, pfpType, pfpValue, pfpColor, currency) { newId ->
                        selectedId = newId

                        scope.launch { CurrencyStore.setLastCurrency(context, currency) }
                        selectedId = newId
                        tab = "active"
                        input = ""
                        showAddDialog = false
                    }
                }
            )
        }
        if (showEditDialog && editingPerson != null) {
            EditPersonDialog(
                person = editingPerson!!,
                onDismiss = { showEditDialog = false; editingPerson = null },
                onSave = { name, pfpType, pfpValue, pfpColor, currency, shouldConvert ->
                    val personSnapshot = editingPerson!!
                    if (shouldConvert && currency != personSnapshot.currency) {
                        scope.launch {
                            val converted = viewModel.convertCurrency(personSnapshot.balance, personSnapshot.currency, currency)
                            viewModel.updatePerson(personSnapshot.copy(
                                name = name,
                                pfpType = pfpType,
                                pfpValue = pfpValue,
                                pfpColor = pfpColor,
                                currency = currency,
                                balance = converted ?: personSnapshot.balance
                            ))
                            CurrencyStore.setLastCurrency(context, currency)
                        }
                    } else {
                        scope.launch {
                            viewModel.updatePerson(personSnapshot.copy(
                                name = name,
                                pfpType = pfpType,
                                pfpValue = pfpValue,
                                pfpColor = pfpColor,
                                currency = currency
                            ))
                            CurrencyStore.setLastCurrency(context, currency)
                        }
                    }
                    showEditDialog = false
                    editingPerson = null
                }
            )
        }
        if (showHistoryDialog && historyPerson != null) {
            TransactionHistoryDialog(
                person = historyPerson!!,
                viewModel = viewModel,
                onDismiss = { showHistoryDialog = false; historyPerson = null }
            )
        }
        if (showThemeDialog) {
            ThemePickerDialog(
                currentThemeName = theme.name,
                onDismiss = { showThemeDialog = false },
                onApply = { selectedTheme ->
                    scope.launch { ThemeStore.setTheme(context, selectedTheme) }
                    showThemeDialog = false
                }
            )
        }
    }
    if (showSplitFlow) {
        when (splitStep) {
            0 -> SplitAmountScreen(
                amount = splitAmount,
                onAmountChange = { splitAmount = it },
                onCancel = {
                    showSplitFlow = false
                    splitAmount = ""
                    splitSelectedIds = setOf()
                    splitIncludeMe = false
                    splitStep = 0
                },
                onNext = { splitStep = 1 }
            )
            1 -> SplitPickerScreen(
                allPersons = viewModel.persons.collectAsState(initial = emptyList()).value,
                selectedIds = splitSelectedIds,
                includeMe = splitIncludeMe,
                onToggleMe = { splitIncludeMe = !splitIncludeMe },
                onTogglePerson = { id ->
                    splitSelectedIds = if (id in splitSelectedIds) splitSelectedIds - id else splitSelectedIds + id
                },
                onAddPerson = { showAddDialog = true },
                onBack = { splitStep = 0 },
                onCancel = {
                    showSplitFlow = false
                    splitAmount = ""
                    splitSelectedIds = setOf()
                    splitIncludeMe = false
                    splitStep = 0
                },
                onNext = { splitStep = 2 }
            )
            2 -> {
                val allPersons = viewModel.persons.collectAsState(initial = emptyList()).value
                val selectedPersons = allPersons.filter { it.id in splitSelectedIds }
                SplitAdjustmentsScreen(
                    viewModel = viewModel,
                    amount = splitAmount.toDoubleOrNull() ?: 0.0,
                    sourceCurrency = "₹",   // adjust if you track this elsewhere; see notes below
                    selectedPersons = selectedPersons,
                    includeMe = splitIncludeMe,
                    note = splitNote,
                    onNoteChange = { splitNote = it },
                    onBack = { splitStep = 1 },
                    onCancel = {
                        showSplitFlow = false
                        splitAmount = ""
                        splitSelectedIds = setOf()
                        splitIncludeMe = false
                        splitNote = ""
                        splitStep = 0
                        selectedId = null
                    },
                    onConfirm = { perPersonAmounts ->
                        // perPersonAmounts is Map<Long, Double> -- person id to amount in their currency
                        perPersonAmounts.forEach { (personId, amt) ->
                            val person = allPersons.find { it.id == personId } ?: return@forEach
                            viewModel.updateBalance(person, amt, splitNote.ifBlank { "Split" })
                        }
                        // reset
                        showSplitFlow = false
                        splitAmount = ""
                        splitSelectedIds = setOf()
                        splitIncludeMe = false
                        splitNote = ""
                        splitStep = 0
                        selectedId = null
                    }
                )
            }
        }
    }
    pendingReminder?.let { target ->
        ReminderDialog(
            person = target,
            viewModel = viewModel,
            onDismiss = { pendingReminder = null }
        )
    }
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = theme.card,
            title = {
                Text("Delete ${target.name}?", color = theme.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will permanently delete ${target.name} and all their transaction history. This can't be undone.",
                    color = theme.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePerson(target)
                    pendingDelete = null
                }) {
                    Text("Delete", color = theme.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }
}

@Composable
fun PersonRow(person: Person, isSelected: Boolean, onHistoryClick: () -> Unit, onClick: () -> Unit,onSendReminder: () -> Unit, onDelete: () -> Unit, onEditClick: () -> Unit) {
    val theme = LocalAppTheme.current
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f))
            .border(1.dp, if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { showMenu = true }
            )
            .padding(12.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PfpView(person = person, size = 44)
            Column {
                Text(person.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                Text(
                    if (person.balance > 0) "owes you" else if (person.balance < 0) "you owe" else "settled",
                    fontSize = 12.sp, color = theme.textSecondary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                formatAmount(person.balance, person.currency),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (person.balance > 0) theme.positive else if (person.balance < 0) theme.negative else theme.textSecondary
            )
            IconButton(onClick = onHistoryClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Info, contentDescription = "History", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
            }
            Box {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = theme.card
                ) {
                    DropdownMenuItem(
                        text = { Text("Send reminder", color = theme.textPrimary) },
                        onClick = { showMenu = false; onSendReminder() }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit", color = theme.textPrimary) },
                        onClick = { showMenu = false; onEditClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = theme.negative) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun PfpView(person: Person, size: Int) {
    val theme = LocalAppTheme.current
    val color = try { Color(android.graphics.Color.parseColor(person.pfpColor)) } catch (e: Exception) { theme.positive }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.32f).dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape((size * 0.32f).dp)),
        contentAlignment = Alignment.Center
    ) {
        when (person.pfpType) {
            "photo" -> AsyncImage(
                model = person.pfpValue,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            "emoji" -> Text(person.pfpValue, fontSize = (size * 0.45f).sp, textAlign = TextAlign.Center)
            else -> Text(
                person.pfpValue.take(2).uppercase(),
                fontSize = (size * 0.35f).sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun NumPad(person: Person, pd: Int, input: String, onKey: (String) -> Unit,onSettle: () -> Unit, onAdd: () -> Unit, onSubtract: () -> Unit, note: String, onNoteChange: (String) -> Unit, showNote: Boolean, onToggleNote: () -> Unit){
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.card)
            .padding(20.dp, 16.dp, 20.dp, pd.dp)
    ) {
        // Display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .padding(16.dp, 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(person.name, color = theme.textSecondary, fontSize = 16.sp)
            Text(
                if (input.isNotEmpty()) input else "0",
                color = theme.textPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium
            )
        }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Add description", color = theme.textSecondary, fontSize = 13.sp)
        Switch(
            checked = showNote,
            onCheckedChange = { onToggleNote() },
            colors = SwitchDefaults.colors(checkedThumbColor = theme.positive, checkedTrackColor = theme.positive.copy(alpha = 0.3f))
        )
    }

    if (showNote) {
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            placeholder = { Text("e.g. dinner, cab fare...", color = theme.textSecondary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.textPrimary,
                unfocusedTextColor = theme.textPrimary,
                focusedBorderColor = theme.positive,
                unfocusedBorderColor = theme.textSecondary
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

        // Keys
        val keys = listOf("1","2","3","4","5","6","7","8","9",".","0","⌫")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Button(
                            onClick = { onKey(k) },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (k == "⌫") theme.negative.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.06f),
                                contentColor = if (k == "⌫") theme.negative else theme.textPrimary
                            )
                        ) {
                            Text(k, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // +/- buttons
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onSubtract,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.negative.copy(alpha = 0.15f), contentColor = theme.negative)
            ) { Text("−", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = onAdd,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.positive.copy(alpha = 0.15f), contentColor = theme.positive)
            ) { Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onSettle,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.textSecondary.copy(alpha = 0.15f),
                contentColor = theme.textPrimary
            ),
            enabled = kotlin.math.abs(person.balance) > 0.005
        ) {
            Text("Settle", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun AddPersonDialog(onDismiss: () -> Unit, onAdd: (String, String, String, String, String) -> Unit) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var pfpType by remember { mutableStateOf("initials") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val savedPath = saveImageToInternalStorage(context, it)
            photoUri = Uri.parse(savedPath)
            pfpType = "photo"
        }
    }
    val lastCurrency by CurrencyStore.getLastCurrency(context).collectAsState(initial = "₹")
    var selectedCurrency by remember { mutableStateOf("₹") }
    LaunchedEffect(lastCurrency) { selectedCurrency = lastCurrency }
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("😊") }
    var selectedColor by remember { mutableStateOf("#4CAF50") }



    val colors = listOf("#E84B3A","#3A8FE8","#2ECC71","#F39C12","#9B59B6","#E91E63","#00BCD4","#FF5722")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = { Text("Add Person", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = theme.textSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.positive,
                        unfocusedBorderColor = theme.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Profile Picture", color = theme.textSecondary, fontSize = 13.sp)

                // PFP type selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("initials","emoji","photo").forEach { type ->
                        FilterChip(
                            selected = pfpType == type,
                            onClick = { pfpType = type; if (type == "photo") launcher.launch("image/*") },
                            label = { Text(type.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.positive.copy(alpha = 0.2f),
                                selectedLabelColor = theme.positive,
                                containerColor = Color.White.copy(alpha = 0.05f),
                                labelColor = theme.textSecondary
                            )
                        )
                    }
                }

                if (pfpType == "emoji") {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Emoji", color = theme.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            focusedBorderColor = theme.positive,
                            unfocusedBorderColor = theme.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (pfpType == "initials") {
                    Text("Color", color = theme.textSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(c)))
                                    .border(if (selectedColor == c) 2.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { selectedColor = c }
                            )
                        }
                    }
                }
                Text("Currency", color = theme.textSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("₹", "$", "€", "£", "¥").forEach { c ->
                        FilterChip(
                            selected = selectedCurrency == c,
                            onClick = { selectedCurrency = c },
                            label = { Text(c, fontSize = 14.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.positive.copy(alpha = 0.2f),
                                selectedLabelColor = theme.positive,
                                containerColor = Color.White.copy(alpha = 0.05f),
                                labelColor = theme.textSecondary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val pfpValue = when (pfpType) {
                            "emoji" -> emoji
                            "photo" -> photoUri?.toString() ?: ""
                            else -> name.take(2)
                        }
                        onAdd(name, pfpType, pfpValue, selectedColor,selectedCurrency)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.positive)
            ) { Text("Add", color = Color.Black, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textSecondary) }
        }
    )
}
@Composable
fun EditPersonDialog(person: Person, onDismiss: () -> Unit, onSave: (String, String, String, String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(person.name) }
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    var shouldConvert by remember { mutableStateOf(false) }
    var selectedCurrency by remember { mutableStateOf(person.currency) }
    var pfpType by remember { mutableStateOf(person.pfpType) }
    var emoji by remember { mutableStateOf(if (person.pfpType == "emoji") person.pfpValue else "😊") }
    var selectedColor by remember { mutableStateOf(person.pfpColor) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val savedPath = saveImageToInternalStorage(context, it)
            photoUri = Uri.parse(savedPath)
            pfpType = "photo"
        }
    }

    val colors = listOf("#E84B3A","#3A8FE8","#2ECC71","#F39C12","#9B59B6","#E91E63","#00BCD4","#FF5722")
    var showConvertAlert by remember { mutableStateOf(false) }
    var pendingCurrency by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = { Text("Edit Person", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = theme.textSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.positive,
                        unfocusedBorderColor = theme.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Profile Picture", color = theme.textSecondary, fontSize = 13.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("initials","emoji","photo").forEach { type ->
                        FilterChip(
                            selected = pfpType == type,
                            onClick = { pfpType = type; if (type == "photo") launcher.launch("image/*") },
                            label = { Text(type.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.positive.copy(alpha = 0.2f),
                                selectedLabelColor = theme.positive,
                                containerColor = Color.White.copy(alpha = 0.05f),
                                labelColor = theme.textSecondary
                            )
                        )
                    }
                }

                if (pfpType == "emoji") {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Emoji", color = theme.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            focusedBorderColor = theme.positive,
                            unfocusedBorderColor = theme.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (pfpType == "initials") {
                    Text("Color", color = theme.textSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(c)))
                                    .border(if (selectedColor == c) 2.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { selectedColor = c }
                            )
                        }
                    }
                }
                Text("Currency", color = theme.textSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("₹", "$", "€", "£", "¥").forEach { c ->
                        FilterChip(
                            selected = selectedCurrency == c,
                            onClick = {
                                if (c != selectedCurrency) {
                                    pendingCurrency = c
                                    showConvertAlert = true
                                }
                            },
                            label = { Text(c, fontSize = 14.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.positive.copy(alpha = 0.2f),
                                selectedLabelColor = theme.positive,
                                containerColor = Color.White.copy(alpha = 0.05f),
                                labelColor = theme.textSecondary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val pfpValue = when (pfpType) {
                            "emoji" -> emoji
                            "photo" -> photoUri?.toString() ?: person.pfpValue
                            else -> name.take(2)
                        }
                        onSave(name, pfpType, pfpValue, selectedColor, selectedCurrency,shouldConvert)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.positive)
            ) { Text("Save", color = Color.Black, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textSecondary) }
        }
    )
    if (showConvertAlert) {
        AlertDialog(
            onDismissRequest = { showConvertAlert = false },
            containerColor = theme.card,
            title = { Text("Change Currency", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Convert balance from $selectedCurrency to $pendingCurrency using live rates, or keep amount as-is?", color = theme.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        shouldConvert = false
                        selectedCurrency = pendingCurrency
                        showConvertAlert = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.positive)
                ) { Text("Keep as-is", color = Color.Black) }
            },
            dismissButton = {
                Button(
                    onClick = {
                        shouldConvert = true
                        selectedCurrency = pendingCurrency
                        showConvertAlert = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                ) { Text("Convert", color = theme.textPrimary) }
            }
        )
    }
}
@Composable
fun TransactionHistoryDialog(person: Person, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val transactions by viewModel.getTransactions(person.id).collectAsState(initial = emptyList())
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingRollback by remember { mutableStateOf<Transaction?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${person.name}'s History",
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (transactions.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Clear log", color = theme.negative, fontSize = 13.sp)
                    }
                }
            }
        },
        text = {
            if (transactions.isEmpty()) {
                Text("No transactions yet.", color = theme.textSecondary, fontSize = 14.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(transactions) { t ->
                        val date = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
                            .format(java.util.Date(t.timestamp))
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(date, color = theme.textSecondary, fontSize = 12.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        formatAmount(t.amount, person.currency),
                                        color = if (t.amount > 0) theme.positive else theme.negative,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(
                                        onClick = { pendingRollback = t },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Undo,
                                            contentDescription = "Rollback",
                                            tint = theme.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            if (t.note.isNotBlank()) {
                                Text(
                                    t.note,
                                    color = theme.textSecondary.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = theme.positive) }
        }
    )

    pendingRollback?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRollback = null },
            containerColor = theme.card,
            title = { Text("Rollback?", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will undo this transaction and all newer ones. The history entries will be kept.",
                    color = theme.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rollbackToTransaction(person, target, context)
                    pendingRollback = null
                }) { Text("Rollback", color = theme.negative) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRollback = null }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = theme.card,
            title = { Text("Clear log?", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This deletes all transaction history for ${person.name}. The current balance won't change. This can't be undone.",
                    color = theme.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearTransactionsForPerson(person.id)
                    showClearConfirm = false
                }) {
                    Text("Clear", color = theme.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }
}
@Composable
fun ThemePickerDialog(currentThemeName: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    val theme = LocalAppTheme.current
    var pendingTheme by remember { mutableStateOf(currentThemeName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = { Text("Choose Theme", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                items(themes) { t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (pendingTheme == t.name) t.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
                            .border(1.dp, if (pendingTheme == t.name) t.primary.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { pendingTheme = t.name }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(t.name, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(t.background).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape))
                            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(t.positive))
                            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(t.negative))
                        }
                    }
                }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "made for fun ❤️ by parth • github: @Parth-KG",
                    fontSize = 11.sp,
                    color = theme.textSecondary.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(pendingTheme) },
                colors = ButtonDefaults.buttonColors(containerColor = theme.positive)
            ) { Text("Apply", color = theme.background, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

@Composable
fun isGestureNavigation(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Secure.getInt(context.contentResolver, "navigation_mode") == 2
        } catch (e: Settings.SettingNotFoundException) {
            false
        }
    }
}

@Composable
fun ReminderDialog(
    person: Person,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val transactions by viewModel.getTransactions(person.id).collectAsState(initial = emptyList())

    var message by remember(person.id) { mutableStateOf(buildReminderText(person)) }
    var includeLog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = { Text("Send reminder", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.positive,
                        unfocusedBorderColor = theme.textSecondary
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeLog = !includeLog }
                ) {
                    Checkbox(
                        checked = includeLog,
                        onCheckedChange = { includeLog = it },
                        colors = CheckboxDefaults.colors(checkedColor = theme.positive)
                    )
                    Text("Include transaction history", color = theme.textSecondary, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalText = if (includeLog && transactions.isNotEmpty()) {
                    message + "\n\nTransaction history:\n" + buildTransactionLog(transactions, person.currency)
                } else {
                    message
                }
                shareReminder(context, finalText)
                onDismiss()
            }) {
                Text("Share", color = theme.positive)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

private fun buildReminderText(person: Person): String {
    val absAmt = formatAmount(kotlin.math.abs(person.balance), person.currency)
    return when {
        person.balance > 0.005 ->
            "Hey ${person.name}, friendly reminder — you owe me $absAmt."
        person.balance < -0.005 ->
            "Hey ${person.name}, friendly reminder — I owe you $absAmt."
        else ->
            "Hey ${person.name}, we're all settled up — thanks!"
    }
}

private fun buildTransactionLog(transactions: List<Transaction>, currency: String): String {
    val sorted = transactions.sortedBy { it.timestamp }
    val sdf = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
    var running = 0.0
    return sorted.joinToString("\n") { t ->
        running += t.amount
        val date = sdf.format(java.util.Date(t.timestamp))
        val sign = if (t.amount > 0) "+" else ""
        val amt = formatAmount(t.amount, currency)
        val noteStr = if (t.note.isNotBlank()) " (${t.note})" else ""
        val runningStr = formatAmount(running, currency)
        "$date: $sign$amt$noteStr  →  $runningStr"
    }
}

private fun shareReminder(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Send reminder via"))
}


@Composable
fun SplitAmountScreen(
    amount: String,
    onAmountChange: (String) -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit
) {
    val theme = LocalAppTheme.current

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Top bar with cancel + title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 48.dp, 16.dp, 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = theme.textPrimary)
                    }
                    Text(
                        "Split amount",
                        color = theme.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.size(48.dp)) // balances the close button
                }

                Spacer(modifier = Modifier.weight(1f))

                // Amount display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (amount.isNotEmpty()) amount else "0",
                        color = theme.textPrimary,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Light
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Numpad keys
                val keys = listOf("1","2","3","4","5","6","7","8","9",".","0","⌫")
                Column(
                    modifier = Modifier.padding(20.dp, 0.dp, 20.dp, 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keys.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { k ->
                                Button(
                                    onClick = {
                                        onAmountChange(
                                            when {
                                                k == "⌫" -> amount.dropLast(1)
                                                k == "." && amount.contains(".") -> amount
                                                k == "." && amount.isEmpty() -> "0."
                                                else -> amount + k
                                            }
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(60.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (k == "⌫") theme.negative.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.06f),
                                        contentColor = if (k == "⌫") theme.negative else theme.textPrimary
                                    )
                                ) {
                                    Text(k, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // Next button
                val amtValue = amount.toDoubleOrNull() ?: 0.0
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 8.dp, 20.dp, 24.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = amtValue > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.positive,
                        contentColor = theme.background,
                        disabledContainerColor = theme.positive.copy(alpha = 0.3f),
                        disabledContentColor = theme.background.copy(alpha = 0.5f)
                    )
                ) {
                    Text("Next", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


@Composable
fun SplitPickerScreen(
    allPersons: List<Person>,
    selectedIds: Set<Long>,
    includeMe: Boolean,
    onToggleMe: () -> Unit,
    onTogglePerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit
) {
    val theme = LocalAppTheme.current

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 48.dp, 16.dp, 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.textPrimary)
                    }
                    Text(
                        "Select people",
                        color = theme.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = theme.textPrimary)
                    }
                }

                // Selected count
                Text(
                    "${selectedIds.size + (if (includeMe) 1 else 0)} selected",
                    color = theme.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                // List
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {

                    // "Add person" row at top
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onAddPerson)
                                .padding(16.dp, 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = theme.positive,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Add new person",
                                color = theme.positive,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // "You" row
                    item {
                        SplitPickerRow(
                            label = "You",
                            sublabel = null,
                            selected = includeMe,
                            theme = theme,
                            onClick = onToggleMe
                        )
                    }

                    // All persons
                    items(allPersons, key = { it.id }) { person ->
                        SplitPickerRow(
                            label = person.name,
                            sublabel = "${person.currency}${kotlin.math.abs(person.balance).let { if (it < 0.005) "0" else "%.2f".format(it) }}",
                            selected = person.id in selectedIds,
                            theme = theme,
                            onClick = { onTogglePerson(person.id) }
                        )
                    }
                }

                // Next button
                val totalSelected = selectedIds.size + (if (includeMe) 1 else 0)
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 8.dp, 20.dp, 24.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = totalSelected >= 1,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.positive,
                        contentColor = theme.background,
                        disabledContainerColor = theme.positive.copy(alpha = 0.3f),
                        disabledContentColor = theme.background.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        if (totalSelected < 2) "Select at least 2 people" else "Next",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitPickerRow(
    label: String,
    sublabel: String?,
    selected: Boolean,
    theme: AppTheme,    // adjust this type to match your theme class name
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) theme.positive.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(16.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(checkedColor = theme.positive)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = theme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (sublabel != null) {
                Text(sublabel, color = theme.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SplitAdjustmentsScreen(
    viewModel: MainViewModel,
    amount: Double,
    sourceCurrency: String,
    selectedPersons: List<Person>,
    includeMe: Boolean,
    note: String,
    onNoteChange: (String) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (Map<Long, Double>) -> Unit
) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()

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
    var amountsInSource by remember(participants, amount) {
        mutableStateOf(equalSplit(amount, participants))
    }
    var lockedIds by remember(participants) { mutableStateOf(setOf<Long>()) }

    // Converted amounts (in each person's own currency) — recalculated when amountsInSource changes
    var convertedAmounts by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    var conversionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(amountsInSource, participants) {
        conversionError = null
        val result = mutableMapOf<Long, Double>()
        for (p in participants) {
            val srcAmt = amountsInSource[p.id] ?: 0.0
            if (p.currency == sourceCurrency) {
                result[p.id] = srcAmt
            } else {
                val converted = viewModel.convertCurrency(srcAmt, sourceCurrency, p.currency)
                if (converted == null) {
                    conversionError = "Couldn't convert to ${p.currency} for ${p.name}. Check internet or remove this person."
                    result[p.id] = srcAmt   // fallback, but warning is shown
                } else {
                    result[p.id] = converted
                }
            }
        }
        convertedAmounts = result
    }

    val total = amountsInSource.values.sum()
    val totalsMatch = kotlin.math.abs(total - amount) < 0.005

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(theme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 48.dp, 16.dp, 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.textPrimary)
                    }
                    Text("Adjust split", color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = theme.textPrimary)
                    }
                }

                // Total amount header
                Text(
                    "$sourceCurrency${"%.2f".format(amount)} total",
                    color = theme.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                // Optional note
                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    placeholder = { Text("Description (optional)", color = theme.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(20.dp, 12.dp, 20.dp, 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.positive,
                        unfocusedBorderColor = theme.textSecondary
                    )
                )

                // Per-person rows
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(participants, key = { it.id }) { p ->
                        SplitAdjustmentRow(
                            participant = p,
                            sourceCurrency = sourceCurrency,
                            amountInSource = amountsInSource[p.id] ?: 0.0,
                            convertedAmount = convertedAmounts[p.id],
                            locked = p.id in lockedIds,
                            onAmountChange = { newAmt ->
                                amountsInSource = redistribute(
                                    current = amountsInSource,
                                    locked = lockedIds + p.id,   // editing locks this row
                                    changedId = p.id,
                                    newValue = newAmt,
                                    total = amount
                                )
                                lockedIds = lockedIds + p.id
                            },
                            onToggleLock = {
                                lockedIds = if (p.id in lockedIds) lockedIds - p.id else lockedIds + p.id
                            },
                            theme = theme
                        )
                    }
                }

                // Total + warning
                Column(modifier = Modifier.padding(20.dp, 8.dp, 20.dp, 0.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total", color = theme.textSecondary, fontSize = 14.sp)
                        Text(
                            "$sourceCurrency${"%.2f".format(total)} of $sourceCurrency${"%.2f".format(amount)}",
                            color = if (totalsMatch) theme.textPrimary else theme.negative,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!totalsMatch) {
                        Text(
                            "Warning: totals don't match.",
                            color = theme.negative,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (conversionError != null) {
                        Text(
                            conversionError!!,
                            color = theme.negative,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Confirm
                Button(
                    onClick = {
                        // Build final map, excluding "You"
                        val finalMap = convertedAmounts
                            .filterKeys { it != youId }
                        onConfirm(finalMap)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 8.dp, 20.dp, 24.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = conversionError == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.positive,
                        contentColor = theme.background,
                        disabledContainerColor = theme.positive.copy(alpha = 0.3f),
                        disabledContentColor = theme.background.copy(alpha = 0.5f)
                    )
                ) {
                    Text("Confirm split", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SplitAdjustmentRow(
    participant: SplitParticipant,
    sourceCurrency: String,
    amountInSource: Double,
    convertedAmount: Double?,
    locked: Boolean,
    onAmountChange: (Double) -> Unit,
    onToggleLock: () -> Unit,
    theme: AppTheme
) {
    var text by remember(amountInSource) { mutableStateOf("%.2f".format(amountInSource)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(participant.name, color = theme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (participant.currency != sourceCurrency && convertedAmount != null) {
                Text(
                    "= ${participant.currency}${"%.2f".format(convertedAmount)}",
                    color = theme.textSecondary,
                    fontSize = 11.sp
                )
            }
        }

        BasicTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                newText.toDoubleOrNull()?.let { onAmountChange(it) }
            },
            textStyle = LocalTextStyle.current.copy(
                color = theme.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            cursorBrush = SolidColor(theme.positive),
            modifier = Modifier.width(80.dp).padding(end = 4.dp)
        )

        Text(sourceCurrency, color = theme.textSecondary, fontSize = 12.sp)

        IconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (locked) "Unlock" else "Lock",
                tint = if (locked) theme.positive else theme.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}