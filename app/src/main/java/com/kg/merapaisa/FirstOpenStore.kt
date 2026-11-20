package com.kg.merapaisa

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The newest version this phone has been told about, by the walkthrough or "What's new" (v3.8.0).
 *
 * Absent on a first run, and on every phone that had the app before v3.8.0. It rides in Android's
 * own backup with the rest of DataStore, so a new phone restored with its ledger is an existing
 * user and gets "What's new", not the walkthrough.
 */
object FirstOpenStore {
    private val LAST_SEEN_KEY = intPreferencesKey("last_seen_version_code")

    suspend fun lastSeen(context: Context): Int? = context.dataStore.data.map { it[LAST_SEEN_KEY] }.first()

    /** Only ever moves forward, so an older build opened later doesn't show the same news again. */
    suspend fun markSeen(context: Context, versionCode: Int) {
        context.dataStore.edit { prefs ->
            prefs[LAST_SEEN_KEY] = maxOf(prefs[LAST_SEEN_KEY] ?: 0, versionCode)
        }
    }
}
