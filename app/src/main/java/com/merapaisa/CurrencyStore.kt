package com.kg.merapaisa

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kg.merapaisa.data.normaliseCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

object CurrencyStore {
    private val CURRENCY_KEY = stringPreferencesKey("last_currency")

    // Older builds stored the display symbol here, so normalise on read.
    fun getLastCurrency(context: Context): Flow<String> =
        context.dataStore.data.map { normaliseCurrency(it[CURRENCY_KEY] ?: "INR") }

    suspend fun setLastCurrency(context: Context, currency: String) {
        context.dataStore.edit { it[CURRENCY_KEY] = currency }
    }
}