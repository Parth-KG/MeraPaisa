package com.kg.merapaisa

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kg.merapaisa.data.AppDatabase
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).personDao()

    val persons = dao.getPersonsWithBalances().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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

    fun updatePerson(person: Person) {
        viewModelScope.launch {
            dao.updatePerson(person)
        }
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

    /**
     * Converts between ISO 4217 codes. Amounts cross the wire in major units because that is
     * what the rates API speaks; the result comes back as minor units.
     */
    suspend fun convertCurrency(amountMinor: Long, from: String, to: String): Long? {
        if (from == to) return amountMinor
        return try {
            withContext(Dispatchers.IO) {
                val major = amountMinor / 100.0
                val url = java.net.URL("https://api.frankfurter.app/latest?amount=$major&from=$from&to=$to")
                val json = url.readText()
                val converted = org.json.JSONObject(json).getJSONObject("rates").getDouble(to)
                Math.round(converted * 100)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch {
            dao.deletePersonWithHistory(person)
        }
    }

    fun rollbackToTransaction(person: PersonWithBalance, target: Transaction, context: Context) {
        viewModelScope.launch {
            dao.rollbackTo(person.id, target.timestamp)
            triggerWidgetUpdate(context)
        }
    }
}
