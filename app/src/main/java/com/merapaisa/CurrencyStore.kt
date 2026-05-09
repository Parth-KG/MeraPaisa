package com.kg.merapaisa

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

object CurrencyStore {
    private val CURRENCY_KEY = stringPreferencesKey("last_currency")

    fun getLastCurrency(context: Context): Flow<String> =
        context.dataStore.data.map { it[CURRENCY_KEY] ?: "₹" }

    suspend fun setLastCurrency(context: Context, currency: String) {
        context.dataStore.edit { it[CURRENCY_KEY] = currency }
    }
}