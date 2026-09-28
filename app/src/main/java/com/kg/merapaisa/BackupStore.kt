package com.kg.merapaisa

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Where automatic backups go, and when the last one happened.
 *
 * Only the folder's URI is kept — a `content://` tree from the system folder picker, whose read and
 * write permission is persisted separately by `ContentResolver.takePersistableUriPermission`. The
 * app never stores a filesystem path and never asks for storage permission, so it can write to a
 * folder the user chose and to nothing else.
 */
object BackupStore {
    private val FOLDER_KEY = stringPreferencesKey("backup_folder_uri")
    private val LAST_RUN_KEY = longPreferencesKey("backup_last_run")
    private val LAST_RESULT_KEY = stringPreferencesKey("backup_last_result")

    /** How many backups to keep in the folder before the oldest are pruned. */
    const val KEEP_COUNT = 12

    /** Null until a folder has been chosen; automatic backups do nothing until then. */
    fun folderUri(context: Context): Flow<String?> =
        context.dataStore.data.map { it[FOLDER_KEY] }

    suspend fun folderUriNow(context: Context): String? =
        context.dataStore.data.map { it[FOLDER_KEY] }.first()

    suspend fun setFolderUri(context: Context, uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(FOLDER_KEY) else it[FOLDER_KEY] = uri
        }
    }

    /** 0 when no automatic backup has run yet. */
    fun lastRun(context: Context): Flow<Long> =
        context.dataStore.data.map { it[LAST_RUN_KEY] ?: 0L }

    /**
     * What happened last time, in a sentence.
     *
     * Kept because an automatic backup runs when nobody is watching: without this, a job that has
     * been failing for a month looks exactly like one that has never been scheduled, and the whole
     * point of a backup is knowing you have one.
     */
    fun lastResult(context: Context): Flow<String?> =
        context.dataStore.data.map { it[LAST_RESULT_KEY] }

    suspend fun recordRun(context: Context, at: Long, result: String) {
        context.dataStore.edit {
            it[LAST_RUN_KEY] = at
            it[LAST_RESULT_KEY] = result
        }
    }
}
