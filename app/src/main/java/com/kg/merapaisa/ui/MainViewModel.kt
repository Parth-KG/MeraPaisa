package com.kg.merapaisa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.kg.merapaisa.CurrencyStore
import com.kg.merapaisa.data.AppDatabase
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.data.appendAmountKey
import com.kg.merapaisa.data.buildLedgerCsv
import com.kg.merapaisa.data.ImportOutcome
import com.kg.merapaisa.data.PayloadResult
import com.kg.merapaisa.data.SharePayload
import com.kg.merapaisa.data.buildPersonSummary
import com.kg.merapaisa.data.buildShareLink
import com.kg.merapaisa.data.claimedNameForDisplay
import com.kg.merapaisa.BackupStore
import com.kg.merapaisa.BuildConfig
import com.kg.merapaisa.backup.AutoExportWorker
import com.kg.merapaisa.backup.BackupWriter
import com.kg.merapaisa.data.BackupResult
import com.kg.merapaisa.data.BackupSnapshot
import com.kg.merapaisa.data.CsvImportResult
import com.kg.merapaisa.data.RestoreMode
import com.kg.merapaisa.data.backupFileName
import com.kg.merapaisa.data.buildShareMessage
import com.kg.merapaisa.data.decodeBackup
import com.kg.merapaisa.data.readLedgerCsv
import com.kg.merapaisa.repository.BackupRepository
import com.kg.merapaisa.repository.toSnapshot
import com.kg.merapaisa.data.decodePayload
import com.kg.merapaisa.data.encodePayload
import com.kg.merapaisa.data.extractPayloadBlob
import com.kg.merapaisa.data.formatMinor
import com.kg.merapaisa.data.normaliseCurrency
import com.kg.merapaisa.deleteProfilePhoto
import com.kg.merapaisa.network.ExchangeRateApi
import com.kg.merapaisa.repository.GroupDetail
import com.kg.merapaisa.repository.GroupRepository
import com.kg.merapaisa.repository.PersonRepository
import com.kg.merapaisa.widget.WidgetLedgerNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds screen state and orchestrates. Database work belongs to [PersonRepository] and network
 * work to [ExchangeRateApi]; nothing here takes a Context or sends a broadcast.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PersonRepository(
        dao = AppDatabase.getDatabase(application).personDao(),
        notifier = WidgetLedgerNotifier(application)
    )
    private val groupRepository = GroupRepository(
        groupDao = AppDatabase.getDatabase(application).groupDao(),
        personDao = AppDatabase.getDatabase(application).personDao(),
        notifier = WidgetLedgerNotifier(application)
    )
    private val backupRepository = BackupRepository(
        db = AppDatabase.getDatabase(application),
        personDao = AppDatabase.getDatabase(application).personDao(),
        groupDao = AppDatabase.getDatabase(application).groupDao(),
        notifier = WidgetLedgerNotifier(application)
    )
    private val exchangeRates = ExchangeRateApi()

    val groups = groupRepository.groupSummaries().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val persons = repository.personsWithBalances().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    /** Set while a currency conversion is in flight, so the edit dialog can block its save. */
    private val _converting = MutableStateFlow(false)
    val converting = _converting.asStateFlow()

    /** Surfaced in the edit dialog when a conversion fails, instead of silently doing nothing. */
    private val _conversionError = MutableStateFlow<String?>(null)
    val conversionError = _conversionError.asStateFlow()

    // --- screen state ---

    fun selectTab(tab: Tab) = _uiState.update {
        it.copy(tab = tab, selectedId = null, input = "")
    }

    fun togglePerson(personId: Long) = _uiState.update {
        val next = if (it.selectedId == personId) null else personId
        it.copy(selectedId = next, input = "")
    }

    fun clearSelection() = _uiState.update { it.copy(selectedId = null, input = "", note = "") }

    fun onKeyPress(key: String) = _uiState.update { it.copy(input = appendAmountKey(it.input, key)) }

    fun setNote(note: String) = _uiState.update { it.copy(note = note) }

    fun toggleNoteField() = _uiState.update { it.copy(showNote = !it.showNote) }

    fun showAddDialog(show: Boolean) = _uiState.update { it.copy(showAddDialog = show) }

    /**
     * Detail for whichever group is open, recomputed whenever its expenses change.
     *
     * [distinctUntilChanged] is what keeps "whenever its expenses change" true. Every field of
     * [MainUiState] shares one flow, so a tab switch, a numpad key or a dialog opening all
     * re-emit the same `openGroupId` — and without the filter each of those restarted
     * `groupDetail`, re-running its three Room queries for a group that had not changed.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val openGroup = _uiState
        .map { it.openGroupId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else groupRepository.groupDetail(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selfId = MutableStateFlow(0L)
    val selfId = _selfId.asStateFlow()

    init {
        viewModelScope.launch { _selfId.value = groupRepository.self().id }
    }

    fun openGroup(groupId: Long?) = _uiState.update {
        it.copy(openGroupId = groupId, showAddExpenseDialog = false, showSettleUp = false)
    }

    fun showAddExpenseDialog(show: Boolean) = _uiState.update { it.copy(showAddExpenseDialog = show) }

    fun showSettleUp(show: Boolean) = _uiState.update { it.copy(showSettleUp = show) }

    fun addExpense(description: String, amountMinor: Long, paidByPersonId: Long, sharedWith: List<Long>) {
        val groupId = _uiState.value.openGroupId ?: return
        viewModelScope.launch {
            groupRepository.addExpense(groupId, description, amountMinor, paidByPersonId, sharedWith)
            _uiState.update { it.copy(showAddExpenseDialog = false) }
        }
    }

    fun recordTransfer(fromPersonId: Long, toPersonId: Long, amountMinor: Long) {
        val groupId = _uiState.value.openGroupId ?: return
        viewModelScope.launch { groupRepository.recordTransfer(groupId, fromPersonId, toPersonId, amountMinor) }
    }

    fun deleteExpense(expenseId: Long) {
        viewModelScope.launch { groupRepository.deleteExpense(expenseId) }
    }

    fun showCreateGroupDialog(show: Boolean) = _uiState.update { it.copy(showCreateGroupDialog = show) }

    fun createGroup(name: String, currency: String, memberIds: List<Long>) {
        viewModelScope.launch {
            groupRepository.createGroup(name, currency, memberIds)
            _uiState.update { it.copy(showCreateGroupDialog = false) }
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch { groupRepository.deleteGroup(groupId) }
    }

    fun showSettingsDialog(show: Boolean) = _uiState.update { it.copy(showSettingsDialog = show) }

    fun editPerson(personId: Long?) = _uiState.update { it.copy(editingPersonId = personId) }

    fun showHistory(personId: Long?) = _uiState.update { it.copy(historyPersonId = personId) }

    fun confirmDelete(personId: Long?) = _uiState.update { it.copy(pendingDeleteId = personId) }

    fun composeReminder(personId: Long?) = _uiState.update { it.copy(pendingReminderId = personId) }

    /** Splits are entered in whichever currency you last used, not in a hardcoded one. */
    fun startSplit() {
        viewModelScope.launch {
            val currency = CurrencyStore.getLastCurrency(getApplication()).first()
            _uiState.update { it.copy(split = SplitFlowState(currency = currency)) }
        }
    }

    fun cancelSplit() = _uiState.update { it.copy(split = null, selectedId = null) }

    fun updateSplit(transform: (SplitFlowState) -> SplitFlowState) = _uiState.update {
        it.copy(split = it.split?.let(transform))
    }

    // --- writes ---

    fun settlePerson(person: PersonWithBalance) {
        viewModelScope.launch {
            repository.settle(person.id)
            _uiState.update { it.copy(tab = Tab.Settled, selectedId = null, input = "", note = "") }
        }
    }

    fun reopenPerson(person: PersonWithBalance) {
        viewModelScope.launch {
            repository.reopen(person.id)
            _uiState.update { it.copy(tab = Tab.Active, selectedId = null, input = "", note = "") }
        }
    }

    fun addPerson(
        name: String,
        pfpType: String,
        pfpValue: String,
        pfpColor: String,
        currency: String,
        onAdded: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val newId = repository.addPerson(
                Person(
                    name = name,
                    pfpType = pfpType,
                    pfpValue = pfpValue,
                    pfpColor = pfpColor,
                    currency = currency,
                    sortOrder = repository.nextSortOrder()
                )
            )
            CurrencyStore.setLastCurrency(getApplication(), currency)
            onAdded(newId)
        }
    }

    /** The balance follows from the rows, so recording the entry is the whole write. */
    fun recordAmount(personId: Long, amountMinor: Long, note: String = "") {
        viewModelScope.launch { repository.recordAmount(personId, amountMinor, note) }
    }

    fun recordSplit(amountsByPerson: Map<Long, Long>, note: String) {
        viewModelScope.launch {
            repository.recordEntries(
                amountsByPerson
                    .filterValues { it != 0L }
                    .map { (personId, amountMinor) ->
                        Transaction(personId = personId, amountMinor = amountMinor, note = note)
                    }
            )
        }
    }

    fun editTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.editTransaction(transaction) }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.deleteTransaction(transaction) }
    }

    fun clearTransactionsForPerson(personId: Long) {
        viewModelScope.launch { repository.clearTransactions(personId) }
    }

    /**
     * Saves an edit, converting the balance first when asked to. The save is only applied once
     * the conversion resolves: a failed rate lookup leaves both the amount and the currency
     * label alone and reports why, rather than relabelling money it never converted.
     */
    fun savePersonEdit(
        snapshot: PersonWithBalance,
        name: String,
        pfpType: String,
        pfpValue: String,
        pfpColor: String,
        currency: String,
        convertBalance: Boolean,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            _conversionError.value = null
            val currencyChanged = currency != snapshot.currency

            if (convertBalance && currencyChanged && snapshot.balanceMinor != 0L) {
                _converting.value = true
                val converted = try {
                    exchangeRates.convert(snapshot.balanceMinor, snapshot.currency, currency)
                } finally {
                    _converting.value = false
                }
                if (converted == null) {
                    _conversionError.value =
                        "Couldn't get a ${snapshot.currency} to $currency rate. " +
                        "Check your connection, or choose Keep as-is to relabel without converting."
                    return@launch
                }
                // Balance is derived, so record the difference the conversion makes.
                repository.recordAmount(
                    personId = snapshot.id,
                    amountMinor = converted - snapshot.balanceMinor,
                    note = "Converted ${snapshot.currency} to $currency"
                )
            }

            // The edit is committed, so the photo it replaced is now unreferenced.
            if (snapshot.person.pfpType == "photo" && snapshot.person.pfpValue != pfpValue) {
                deleteProfilePhoto(getApplication(), snapshot.person.pfpValue)
            }

            repository.updatePerson(
                snapshot.person.copy(
                    name = name,
                    pfpType = pfpType,
                    pfpValue = pfpValue,
                    pfpColor = pfpColor,
                    currency = currency
                )
            )
            CurrencyStore.setLastCurrency(getApplication(), currency)
            onSaved()
        }
    }

    /**
     * Builds a CSV of the whole ledger. The file itself is written and shared by the UI, which
     * is the only layer that should be holding a Context.
     */
    fun exportLedgerCsv(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(buildLedgerCsv(repository.ledgerSnapshot())) }
    }

    /** Plain text for one person, ready to paste into a chat with them. */
    fun personSummary(person: PersonWithBalance, onReady: (String) -> Unit) {
        viewModelScope.launch {
            onReady(buildPersonSummary(person, repository.transactionsNow(person.id)))
        }
    }

    fun dismissConversionError() {
        _conversionError.value = null
    }

    fun getTransactions(personId: Long) = repository.transactions(personId)

    fun getTransactionCount(personId: Long) = repository.transactionCount(personId)

    /** How much group history a delete would take with it, so the warning can say so. */
    fun getGroupExpenseCount(personId: Long) = groupRepository.expensesPaidBy(personId)

    suspend fun convertCurrency(amountMinor: Long, from: String, to: String): Long? =
        exchangeRates.convert(amountMinor, from, to)

    fun deletePerson(person: Person) {
        viewModelScope.launch {
            deleteProfilePhoto(getApplication(), person.pfpValue.takeIf { person.pfpType == "photo" })
            repository.deletePerson(person)
        }
    }

    fun rollbackToTransaction(person: PersonWithBalance, target: Transaction) {
        viewModelScope.launch { repository.rollbackTo(person.id, target.timestamp) }
    }

    // =========================================================================================
    // Two-sided ledger over share links — outgoing
    // =========================================================================================

    /**
     * Opens the share sheet for [personId], having worked out what the next link would carry.
     *
     * Nothing is sent and no watermark moves here. This only reads, so backing out of the sheet
     * leaves no trace — which matters because the sheet is also where an empty payload is
     * explained ("nothing new since you last shared").
     */
    fun openShareSheet(personId: Long) {
        viewModelScope.launch {
            val person = persons.value.firstOrNull { it.id == personId }
            val entries = repository.entriesToShare(personId, fullHistory = false)
            // Blank rather than "You": the sheet asks for a real name the first time, because
            // "You" means nothing on the recipient's phone.
            //
            // Read before the update rather than inside it. `update` is inline, so a suspend call
            // in its block compiles — but it retries the block on contention, which would mean
            // re-querying the database for every retry.
            val myName = repository.self().name.takeUnless { it == "You" } ?: ""

            _uiState.update {
                it.copy(
                    share = ShareFlowState(
                        personId = personId,
                        personName = person?.name ?: "",
                        currency = normaliseCurrency(person?.currency ?: "INR"),
                        senderName = myName,
                        entryCount = entries.size,
                        netMinor = entries.sumOf { e -> e.amountMinor }
                    )
                )
            }
        }
    }

    fun closeShareSheet() = _uiState.update { it.copy(share = null) }

    fun setShareSenderName(name: String) = _uiState.update {
        it.copy(share = it.share?.copy(senderName = name))
    }

    /** Flips between "only what is new" and the whole history, recounting what would be sent. */
    fun setShareFullHistory(full: Boolean) {
        val current = _uiState.value.share ?: return
        viewModelScope.launch {
            val entries = repository.entriesToShare(current.personId, fullHistory = full)
            _uiState.update {
                it.copy(
                    share = it.share?.copy(
                        fullHistory = full,
                        entryCount = entries.size,
                        netMinor = entries.sumOf { e -> e.amountMinor }
                    )
                )
            }
        }
    }

    /**
     * Builds the link and hands the finished message back through [onReady] for the share sheet.
     *
     * The watermark advances here, before we know whether the user actually sent the message —
     * Android does not report that. Advancing optimistically can skip entries if they back out,
     * which "send full history" exists to undo. The other order would double-send by default, and
     * a debt counted twice is worse than one that needs re-sending.
     *
     * The Context stays in the UI layer: this returns a String and never touches an Intent.
     */
    fun prepareShareMessage(onReady: (String) -> Unit) {
        val current = _uiState.value.share ?: return
        if (current.busy || current.hasNothingToSend) return
        _uiState.update { it.copy(share = it.share?.copy(busy = true)) }

        viewModelScope.launch {
            val senderName = current.senderName.trim().ifEmpty { "A friend" }
            repository.renameSelf(senderName)

            val built = repository.buildPayload(current.personId, senderName, current.fullHistory)
            if (built == null) {
                _uiState.update { it.copy(share = it.share?.copy(busy = false, entryCount = 0)) }
                return@launch
            }
            val (payload, upTo) = built
            repository.markShared(current.personId, upTo)

            val message = buildShareMessage(
                senderName = senderName,
                entryCount = payload.entries.size,
                netText = netPhrase(payload.netMinor, payload.currency),
                link = buildShareLink(encodePayload(payload))
            )
            _uiState.update { it.copy(share = null) }
            onReady(message)
        }
    }

    /** Reads from the recipient's side, which is the side that has to act on it. */
    private fun netPhrase(netMinor: Long, currency: String): String = when {
        netMinor > 0 -> "you owe ${formatMinor(netMinor, currency)}"
        netMinor < 0 -> "they owe you ${formatMinor(-netMinor, currency)}"
        else -> "nothing outstanding"
    }

    // =========================================================================================
    // Two-sided ledger over share links — incoming
    // =========================================================================================

    /**
     * Handles a link arriving from outside the app: tapped in a chat, or pasted by hand.
     *
     * Decodes and stops. Nothing is written until the user confirms, because the payload is
     * unauthenticated and their confirmation is the only thing standing in for a signature.
     */
    fun onShareLinkReceived(raw: String) {
        _uiState.update { it.copy(import = ImportFlowState.Reading) }

        val blob = extractPayloadBlob(raw)
        if (blob == null) {
            _uiState.update {
                it.copy(import = ImportFlowState.Unreadable(UnreadableReason.NotALink))
            }
            return
        }

        when (val result = decodePayload(blob)) {
            is PayloadResult.TooNew -> _uiState.update {
                it.copy(import = ImportFlowState.Unreadable(UnreadableReason.NewerVersion))
            }
            PayloadResult.Malformed -> _uiState.update {
                it.copy(import = ImportFlowState.Unreadable(UnreadableReason.Damaged))
            }
            is PayloadResult.Ok -> beginConfirming(result.payload)
        }
    }

    /**
     * Preselects the person the link most likely refers to, matching on name.
     *
     * A suggestion only — the user can change it, and must confirm either way. Matching on an
     * unverified name is a convenience, never a decision: a crafted link naming someone should
     * cost a tap to notice, not apply itself.
     */
    private fun beginConfirming(payload: SharePayload) {
        viewModelScope.launch {
            // Already applied? Say so now rather than after a preview that promises a change which
            // will not happen. Found on a device: re-opening a used link showed the full
            // "you will owe ₹240" screen, and only the tap revealed it was a no-op.
            val already = repository.appliedPayload(payload.payloadId)
            if (already != null) {
                val name = persons.value.firstOrNull { it.id == already.personId }?.name
                    ?: claimedNameForDisplay(already.senderName)
                _uiState.update {
                    it.copy(import = ImportFlowState.Done(
                        ImportOutcome.AlreadyApplied(already.appliedAt),
                        name
                    ))
                }
                return@launch
            }
            offerConfirmation(payload)
        }
    }

    private fun offerConfirmation(payload: SharePayload) {
        val claimed = payload.senderName.trim()
        // Currency is part of the match, not just the name. The import screen only offers people in
        // the payload's currency, so matching on name alone could preselect somebody who is not in
        // that list — leaving the dialog looking like nothing was chosen while "Record it" was live,
        // and ending in a currency refusal. A name match in the wrong currency falls through to
        // "add someone new", which creates them in the right one.
        val match = persons.value.firstOrNull {
            it.name.trim().equals(claimed, ignoreCase = true) &&
                normaliseCurrency(it.currency) == payload.currency
        }
        _uiState.update {
            it.copy(
                import = ImportFlowState.Confirming(
                    payload = payload,
                    targetPersonId = match?.id,
                    // Sanitised: this becomes a person's name if accepted, and it is
                    // attacker-chosen text. Matching above still uses the raw value.
                    newPersonName = if (match == null) claimedNameForDisplay(claimed) else null
                )
            )
        }
    }

    fun setImportTarget(personId: Long) = _uiState.update {
        val confirming = it.import as? ImportFlowState.Confirming ?: return@update it
        it.copy(import = confirming.copy(targetPersonId = personId, newPersonName = null))
    }

    /** Switches to creating someone new. Null name means "back to picking an existing person". */
    fun setImportNewPersonName(name: String?) = _uiState.update {
        val confirming = it.import as? ImportFlowState.Confirming ?: return@update it
        it.copy(import = confirming.copy(newPersonName = name, targetPersonId = null))
    }

    /**
     * Writes the link, creating the person first if that is what was chosen.
     *
     * A new person is created in the payload's currency, not the app default — creating them in the
     * wrong one would immediately trip the currency guard and refuse the very link that made them.
     */
    fun applyImport() {
        val confirming = _uiState.value.import as? ImportFlowState.Confirming ?: return
        if (!confirming.canApply) return
        _uiState.update { it.copy(import = confirming.copy(busy = true)) }

        viewModelScope.launch {
            val personId = confirming.targetPersonId ?: repository.addPerson(
                Person(
                    name = confirming.newPersonName!!.trim(),
                    pfpValue = confirming.newPersonName.trim().take(2).uppercase(),
                    sortOrder = repository.nextSortOrder(),
                    currency = confirming.payload.currency
                )
            )
            val name = persons.value.firstOrNull { it.id == personId }?.name
                ?: confirming.newPersonName?.trim()
                ?: confirming.payload.senderName

            val outcome = repository.importPayload(
                personId = personId,
                payload = confirming.payload,
                now = System.currentTimeMillis()
            )
            _uiState.update { it.copy(import = ImportFlowState.Done(outcome, name)) }
        }
    }

    /** Opens the paste box. See [ImportFlowState.Pasting] for why this is a primary path. */
    fun openPasteImport() = _uiState.update {
        it.copy(showSettingsDialog = false, import = ImportFlowState.Pasting())
    }

    fun setPasteText(text: String) = _uiState.update {
        val pasting = it.import as? ImportFlowState.Pasting ?: return@update it
        it.copy(import = pasting.copy(text = text))
    }

    /** Reads whatever was pasted, taking the same path a tapped link does. */
    fun submitPaste() {
        val pasting = _uiState.value.import as? ImportFlowState.Pasting ?: return
        onShareLinkReceived(pasting.text)
    }

    fun dismissImport() = _uiState.update { it.copy(import = null) }

    // =========================================================================================
    // Backup and restore
    // =========================================================================================

    fun openBackupScreen() {
        viewModelScope.launch {
            _uiState.update { it.copy(showSettingsDialog = false, backup = BackupFlowState.Menu()) }
            refreshBackupMenu()
        }
    }

    fun closeBackupScreen() = _uiState.update { it.copy(backup = null) }

    /** Re-reads the folder and last-run details behind the menu. */
    private suspend fun refreshBackupMenu() {
        val context = getApplication<Application>()
        val folder = BackupStore.folderUriNow(context)
        val lastRun = BackupStore.lastRun(context).first()
        val lastResult = BackupStore.lastResult(context).first()
        _uiState.update {
            it.copy(
                backup = BackupFlowState.Menu(
                    folderName = folder?.let(::folderLabel),
                    lastRun = lastRun,
                    lastResult = lastResult
                )
            )
        }
    }

    /**
     * Writes a full backup to the file the user picked.
     *
     * The JSON is built before the file is opened so that a failure to read the ledger cannot leave
     * an empty file sitting where a backup is supposed to be.
     */
    fun writeBackupTo(uriString: String?) {
        if (uriString == null) { viewModelScope.launch { refreshBackupMenu() }; return }
        _uiState.update { it.copy(backup = BackupFlowState.Working) }

        viewModelScope.launch {
            val context = getApplication<Application>()
            val result = runCatching {
                val json = backupRepository.exportJson(System.currentTimeMillis(), BuildConfig.VERSION_NAME)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uriString.toUri())?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: error("could not open the file for writing")
                }
                json.length
            }
            _uiState.update {
                it.copy(
                    backup = result.fold(
                        onSuccess = { size ->
                            BackupFlowState.Done(
                                "Backup saved",
                                "Everything is in that file — people, entries, groups and expenses. " +
                                    "Keep it somewhere that is not this phone. (${size / 1024} KB)"
                            )
                        },
                        onFailure = { e ->
                            BackupFlowState.Unreadable(
                                "Could not save the backup",
                                e.message ?: "The file could not be written."
                            )
                        }
                    )
                )
            }
        }
    }

    /**
     * Reads a file the user picked and works out what restoring it would do.
     *
     * Understands both shapes. A JSON backup restores everything; a CSV restores people and
     * entries only, which the preview then has to say out loud.
     */
    fun readRestoreFrom(uriString: String?) {
        if (uriString == null) { viewModelScope.launch { refreshBackupMenu() }; return }
        _uiState.update { it.copy(backup = BackupFlowState.Working) }

        viewModelScope.launch {
            val context = getApplication<Application>()
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uriString.toUri())
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("could not open the file")
                }
            }.getOrNull()

            if (text == null) {
                _uiState.update {
                    it.copy(backup = BackupFlowState.Unreadable(
                        "Could not read that file",
                        "It may have been moved or deleted since you picked it."
                    ))
                }
                return@launch
            }
            interpretRestoreFile(text)
        }
    }

    /** JSON first, then CSV, then give up — with a sentence that says which it tried. */
    private suspend fun interpretRestoreFile(text: String) {
        when (val backup = decodeBackup(text)) {
            is BackupResult.Ok ->
                beginReview(RestoreSource.Json, backup.snapshot, backup.exportedAt, backup.appVersion)

            is BackupResult.TooNew -> _uiState.update {
                it.copy(backup = BackupFlowState.Unreadable(
                    "This backup is too new",
                    "It was written by a newer version of Mera Paisa. Update the app and try again. " +
                        "Guessing at a format this build does not know could restore the wrong amounts."
                ))
            }

            BackupResult.Damaged -> _uiState.update {
                it.copy(backup = BackupFlowState.Unreadable(
                    "This backup is damaged",
                    "It is a Mera Paisa backup, but it is incomplete or has been edited into a " +
                        "shape that no longer adds up. Nothing has been changed. Try an older backup."
                ))
            }

            BackupResult.NotABackup -> when (val csv = readLedgerCsv(text)) {
                is CsvImportResult.Ok ->
                    beginReview(RestoreSource.Csv, csv.people.toSnapshot(), null, null)

                is CsvImportResult.Damaged -> _uiState.update {
                    it.copy(backup = BackupFlowState.Unreadable(
                        "That CSV could not be read",
                        "Line ${csv.line}: ${csv.reason}. Nothing has been changed."
                    ))
                }

                CsvImportResult.NotALedgerCsv -> _uiState.update {
                    it.copy(backup = BackupFlowState.Unreadable(
                        "That is not a Mera Paisa file",
                        "Pick a backup this app saved, or a ledger CSV it exported."
                    ))
                }
            }
        }
    }

    private suspend fun beginReview(
        source: RestoreSource,
        incoming: BackupSnapshot,
        exportedAt: Long?,
        appVersion: String?
    ) {
        val plan = backupRepository.plan(incoming, RestoreMode.Merge)
        _uiState.update {
            it.copy(backup = BackupFlowState.Reviewing(
                source = source,
                incoming = incoming,
                exportedAt = exportedAt,
                appVersion = appVersion,
                mode = RestoreMode.Merge,
                plan = plan
            ))
        }
    }

    /** Switching mode recomputes the plan, so the counts always describe the mode on screen. */
    fun setRestoreMode(mode: RestoreMode) {
        val reviewing = _uiState.value.backup as? BackupFlowState.Reviewing ?: return
        if (reviewing.mode == mode || reviewing.busy) return
        viewModelScope.launch {
            val plan = backupRepository.plan(reviewing.incoming, mode)
            _uiState.update {
                val current = it.backup as? BackupFlowState.Reviewing ?: return@update it
                it.copy(backup = current.copy(mode = mode, plan = plan))
            }
        }
    }

    fun applyRestore() {
        val reviewing = _uiState.value.backup as? BackupFlowState.Reviewing ?: return
        if (reviewing.busy) return
        _uiState.update { it.copy(backup = reviewing.copy(busy = true)) }

        viewModelScope.launch {
            val result = runCatching { backupRepository.apply(reviewing.plan) }
            _uiState.update {
                it.copy(backup = result.fold(
                    onSuccess = {
                        val i = reviewing.plan.inserts
                        val detail = buildString {
                            if (reviewing.mode == RestoreMode.Replace) {
                                append("Your ledger now matches the backup exactly. ")
                            }
                            append("Added ${i.people} ${if (i.people == 1) "person" else "people"}, ")
                            append("${i.transactions} ${if (i.transactions == 1) "entry" else "entries"}")
                            if (i.groups > 0) append(", ${i.groups} ${if (i.groups == 1) "group" else "groups"}")
                            append(".")
                            val skipped = reviewing.plan.alreadyPresent
                            if (reviewing.mode == RestoreMode.Merge && !skipped.isZero) {
                                append(" ${skipped.transactions} ${if (skipped.transactions == 1) "entry was" else "entries were"} already here and ${if (skipped.transactions == 1) "was" else "were"} left alone.")
                            }
                        }
                        BackupFlowState.Done("Restored", detail)
                    },
                    onFailure = { e ->
                        BackupFlowState.Unreadable(
                            "The restore did not finish",
                            "Nothing was changed — the whole restore runs as one database " +
                                "transaction, so a failure leaves your ledger as it was. " +
                                "(${e.message ?: e::class.simpleName})"
                        )
                    }
                ))
            }
        }
    }

    // --- the weekly job ----------------------------------------------------------------------

    /** Chooses the folder and starts the weekly job; a null uri means the picker was cancelled. */
    fun setBackupFolder(uriString: String?) {
        if (uriString == null) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            BackupStore.setFolderUri(context, uriString)
            AutoExportWorker.schedule(context)
            refreshBackupMenu()
        }
    }

    fun turnOffAutomaticBackups() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            AutoExportWorker.cancel(context)
            BackupStore.setFolderUri(context, null)
            refreshBackupMenu()
        }
    }

    /** Runs the weekly job's work immediately, so the folder can be proved to work now. */
    fun backUpNow() {
        val menu = _uiState.value.backup as? BackupFlowState.Menu ?: return
        if (menu.busy) return
        _uiState.update { it.copy(backup = menu.copy(busy = true)) }

        viewModelScope.launch {
            val context = getApplication<Application>()
            val folder = BackupStore.folderUriNow(context)
            val now = System.currentTimeMillis()
            val message = if (folder == null) "No folder chosen." else {
                val uri = folder.toUri()
                val json = backupRepository.exportJson(now, BuildConfig.VERSION_NAME)
                val written = BackupWriter.write(context, uri, backupFileName(BackupWriter.stamp(now)), json)
                if (written == null) "Could not write to the backup folder. Pick it again."
                else {
                    val pruned = BackupWriter.prune(context, uri)
                    "Backed up successfully." + if (pruned > 0) " $pruned older removed." else ""
                }
            }
            BackupStore.recordRun(context, now, message)
            refreshBackupMenu()
        }
    }

    /** The tail of a tree uri is the closest thing to a folder name SAF will give us. */
    private fun folderLabel(uri: String): String =
        uri.substringAfterLast("%2F").substringAfterLast("/").ifBlank { "the folder you chose" }
}
