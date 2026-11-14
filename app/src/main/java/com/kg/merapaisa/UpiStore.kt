package com.kg.merapaisa

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Your own UPI ID, the one friends pay when they scan your QR, from Show QR or a reminder.
 *
 * Kept on this phone only. Setting or changing it always goes through the phone's fingerprint, face
 * or screen lock first (Parth, 14 Nov 2026): someone who set it before you would collect the money
 * your reminders and your QR ask for.
 */
object UpiStore {
    private val MY_UPI_ID_KEY = stringPreferencesKey("my_upi_id")

    fun myUpiId(context: Context): Flow<String?> =
        context.dataStore.data.map { it[MY_UPI_ID_KEY]?.takeIf { id -> id.isNotBlank() } }

    suspend fun setMyUpiId(context: Context, id: String?) {
        context.dataStore.edit {
            if (id.isNullOrBlank()) it.remove(MY_UPI_ID_KEY) else it[MY_UPI_ID_KEY] = id
        }
    }
}
