package com.kg.merapaisa.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kg.merapaisa.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * When the app last looked for an update, and which version the user already said no to.
 *
 * Both exist to stop the app being a nuisance. Without the timestamp it would call GitHub on every
 * launch; without the dismissal it would offer the same version every single time you opened it.
 */
object UpdateStore {
    private val LAST_CHECK_KEY = longPreferencesKey("update_last_check")
    private val DISMISSED_KEY = stringPreferencesKey("update_dismissed_version")

    /** Once a day is plenty for an app that ships every few weeks. */
    const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    suspend fun lastCheck(context: Context): Long =
        context.dataStore.data.map { it[LAST_CHECK_KEY] ?: 0L }.first()

    suspend fun recordCheck(context: Context, at: Long) {
        context.dataStore.edit { it[LAST_CHECK_KEY] = at }
    }

    suspend fun dismissedVersion(context: Context): String? =
        context.dataStore.data.map { it[DISMISSED_KEY] }.first()

    /** Records "not now" for one version only — the next release will still be offered. */
    suspend fun dismiss(context: Context, version: String) {
        context.dataStore.edit { it[DISMISSED_KEY] = version }
    }

    suspend fun isDue(context: Context, now: Long): Boolean =
        now - lastCheck(context) >= CHECK_INTERVAL_MS
}
