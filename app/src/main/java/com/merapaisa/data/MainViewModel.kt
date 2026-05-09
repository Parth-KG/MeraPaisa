package com.merapaisa

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import com.merapaisa.data.Transaction
import com.merapaisa.data.AppDatabase
import com.merapaisa.data.Person
import android.content.Context
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).personDao()

    val persons = dao.getAllPersons().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun settlePerson(person: Person, context: Context) {
        viewModelScope.launch {
            if (kotlin.math.abs(person.balance) < 0.005) return@launch

            updateBalance(person, -person.balance, "Settled")

            val maxOrder = persons.value.maxOfOrNull { it.sortOrder } ?: 0
            dao.updateSortOrder(person.id, maxOrder + 1)

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

    fun updateBalance(person: Person, amount: Double, note: String = "") {
        viewModelScope.launch {
            dao.updateBalance(person.id, person.balance + amount)
            addTransaction(person.id, amount, note)
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
    fun triggerWidgetUpdate(context: android.content.Context) {
        val widgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val widgetComponent = android.content.ComponentName(context, com.merapaisa.widget.DebtWidgetReceiver::class.java)
        val widgetIds = widgetManager.getAppWidgetIds(widgetComponent)
        if (widgetIds.isNotEmpty()) {
            val intent = android.content.Intent(context, com.merapaisa.widget.DebtWidgetReceiver::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(intent)
        }
    }
    fun addTransaction(personId: Long, amount: Double, note: String = "") {
        viewModelScope.launch {
            dao.insertTransaction(
                com.merapaisa.data.Transaction(
                    personId = personId,
                    amount = amount,
                    note = note
                )
            )
        }
    }

    fun getTransactions(personId : Long) = dao.getTransactionsForPerson(personId)

    suspend fun convertCurrency(amount: Double, from: String, to: String): Double? {
        val symbols = mapOf("₹" to "INR", "$" to "USD", "€" to "EUR", "£" to "GBP", "¥" to "JPY")
        val fromCode = symbols[from] ?: return null
        val toCode = symbols[to] ?: return null
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val url = java.net.URL("https://api.frankfurter.app/latest?amount=$amount&from=$fromCode&to=$toCode")
                val json = url.readText()
                org.json.JSONObject(json).getJSONObject("rates").getDouble(toCode)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch {
            dao.deleteTransactionsForPerson(person.id)
            dao.deletePerson(person)
        }
    }
    fun rollbackToTransaction(person: Person, target: Transaction, context: Context) {
        viewModelScope.launch {
            val all = dao.getTransactionsForPerson(person.id).first()
            val toUndo = all.filter { it.timestamp >= target.timestamp }
            val sumToReverse = toUndo.sumOf { it.amount }
            updateBalance(person, -sumToReverse, "Rollback")
            triggerWidgetUpdate(context)
        }
    }
}