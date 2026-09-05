package com.kg.merapaisa

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether the ledger is kept behind the device's own authentication. No secret of ours is
 * stored: unlocking goes through BiometricPrompt, which uses the fingerprint, face or PIN the
 * phone already trusts.
 */
object SecurityStore {
    private val APP_LOCK_KEY = booleanPreferencesKey("app_lock_enabled")

    /** How long the app may sit in the background before it asks again. */
    const val GRACE_MILLIS = 30_000L

    fun isAppLockEnabled(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[APP_LOCK_KEY] ?: false }

    suspend fun setAppLockEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[APP_LOCK_KEY] = enabled }
    }
}
