package com.kg.merapaisa

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Which view of the overall total the top of Active shows: the net, or both sides. A tap on the
 * total switches it, and it stays that way until tapped again, closing the app included.
 */
object TotalViewStore {
    private val BOTH_SIDES_KEY = booleanPreferencesKey("total_shows_both_sides")

    fun showsBothSides(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[BOTH_SIDES_KEY] ?: false }

    suspend fun setShowsBothSides(context: Context, bothSides: Boolean) {
        context.dataStore.edit { it[BOTH_SIDES_KEY] = bothSides }
    }
}
