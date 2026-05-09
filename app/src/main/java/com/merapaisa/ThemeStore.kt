package com.merapaisa

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object ThemeStore {
    private val THEME_KEY = stringPreferencesKey("selected_theme")

    fun getTheme(context: Context): Flow<String> =
        context.dataStore.data.map { it[THEME_KEY] ?: "Midnight" }

    suspend fun setTheme(context: Context, theme: String) {
        context.dataStore.edit { it[THEME_KEY] = theme }
    }
}