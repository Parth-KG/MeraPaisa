package com.kg.merapaisa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kg.merapaisa.CurrencyStore
import com.kg.merapaisa.data.AppDatabase
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.data.appendAmountKey
import com.kg.merapaisa.data.buildLedgerCsv
import com.kg.merapaisa.data.buildPersonSummary
import com.kg.merapaisa.deleteProfilePhoto
import com.kg.merapaisa.network.ExchangeRateApi
import com.kg.merapaisa.repository.PersonRepository
import com.kg.merapaisa.widget.WidgetLedgerNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds screen state and orchestrates. Database work belongs to [PersonRepository] and network
 * work to [ExchangeRateApi]; nothing here takes a Context or sends a broadcast.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PersonRepository(
        dao = AppDatabase.getDatabase(application).personDao(),
        notifier = WidgetLedgerNotifier(application)
    )
    private val exchangeRates = ExchangeRateApi()

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

    fun showThemeDialog(show: Boolean) = _uiState.update { it.copy(showThemeDialog = show) }

    fun editPerson(personId: Long?) = _uiState.update { it.copy(editingPersonId = personId) }

    fun showHistory(personId: Long?) = _uiState.update { it.copy(historyPersonId = personId) }

    fun confirmDelete(personId: Long?) = _uiState.update { it.copy(pendingDeleteId = personId) }

    fun composeReminder(personId: Long?) = _uiState.update { it.copy(pendingReminderId = personId) }

    fun startSplit() = _uiState.update { it.copy(split = SplitFlowState()) }

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
                    sortOrder = persons.value.size
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
}
