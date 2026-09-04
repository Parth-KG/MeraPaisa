package com.kg.merapaisa

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kg.merapaisa.data.AppDatabase
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.data.appendAmountKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).personDao()

    val persons = dao.getPersonsWithBalances().stateIn(
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

    fun settlePerson(person: PersonWithBalance, context: Context) {
        viewModelScope.launch {
            dao.settle(person.id)
            triggerWidgetUpdate(context)
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
            val newId = dao.insertPerson(
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
        viewModelScope.launch {
            dao.insertTransaction(
                Transaction(personId = personId, amountMinor = amountMinor, note = note)
            )
        }
    }

    fun recordSplit(amountsByPerson: Map<Long, Long>, note: String) {
        viewModelScope.launch {
            val entries = amountsByPerson
                .filterValues { it != 0L }
                .map { (personId, amountMinor) ->
                    Transaction(personId = personId, amountMinor = amountMinor, note = note)
                }
            if (entries.isNotEmpty()) dao.insertTransactions(entries)
        }
    }

    fun clearTransactionsForPerson(personId: Long) {
        viewModelScope.launch {
            dao.clearTransactionsForPerson(personId)
        }
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
                    convertCurrency(snapshot.balanceMinor, snapshot.currency, currency)
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
                dao.insertTransaction(
                    Transaction(
                        personId = snapshot.id,
                        amountMinor = converted - snapshot.balanceMinor,
                        note = "Converted ${snapshot.currency} to $currency"
                    )
                )
            }

            // The edit is committed, so the photo it replaced is now unreferenced.
            if (snapshot.person.pfpType == "photo" && snapshot.person.pfpValue != pfpValue) {
                deleteProfilePhoto(getApplication(), snapshot.person.pfpValue)
            }

            dao.updatePerson(
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

    fun dismissConversionError() {
        _conversionError.value = null
    }

    fun triggerWidgetUpdate(context: Context) {
        val widgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val widgetComponent = android.content.ComponentName(context, com.kg.merapaisa.widget.DebtWidgetReceiver::class.java)
        val widgetIds = widgetManager.getAppWidgetIds(widgetComponent)
        if (widgetIds.isNotEmpty()) {
            val intent = android.content.Intent(context, com.kg.merapaisa.widget.DebtWidgetReceiver::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(intent)
        }
    }

    fun getTransactions(personId: Long) = dao.getTransactionsForPerson(personId)

    fun getTransactionCount(personId: Long) = dao.getTransactionCount(personId)

    /**
     * Converts between ISO 4217 codes. Amounts cross the wire in major units because that is
     * what the rates API speaks; the result comes back as minor units. Null means the rate
     * could not be fetched — callers must say so rather than carrying on with the old amount.
     */
    suspend fun convertCurrency(amountMinor: Long, from: String, to: String): Long? {
        if (from == to) return amountMinor
        val major = amountMinor / 100.0
        val rate = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { fetchConverted(major, from, to) }
        }
        return rate?.let { Math.round(it * 100) }
    }

    private fun fetchConverted(amountMajor: Double, from: String, to: String): Double? {
        val url = URL("https://api.frankfurter.app/latest?amount=$amountMajor&from=$from&to=$to")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).getJSONObject("rates").getDouble(to)
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch {
            deleteProfilePhoto(getApplication(), person.pfpValue.takeIf { person.pfpType == "photo" })
            dao.deletePersonWithHistory(person)
        }
    }

    fun rollbackToTransaction(person: PersonWithBalance, target: Transaction, context: Context) {
        viewModelScope.launch {
            dao.rollbackTo(person.id, target.timestamp)
            triggerWidgetUpdate(context)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
        const val REQUEST_TIMEOUT_MS = 12_000L
    }
}
