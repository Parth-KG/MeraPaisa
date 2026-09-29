package com.kg.merapaisa.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.kg.merapaisa.BackupStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writing backups into a folder the user picked, and pruning the old ones.
 *
 * Goes through `DocumentsContract` rather than `androidx.documentfile`, which would be a new
 * dependency for something the framework already does, and this project adds none. The trade is
 * more verbose calls here in exchange for nothing new in the APK.
 *
 * The app holds no storage permission of any kind. It can write to the one folder that was handed
 * to it through the system picker, and to nowhere else.
 */
object BackupWriter {

    private const val MIME_JSON = "application/json"
    private const val PREFIX = "mera-paisa-backup-"

    /** `20260928-143000`, which sorts chronologically as text. The pruning relies on that. */
    fun stamp(at: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(at))

    /**
     * Writes [content] into [treeUri] as [fileName].
     *
     * Returns null rather than throwing: this runs from a background job where the folder may have
     * been deleted, unmounted, or had its permission revoked since it was chosen, and none of those
     * are exceptional enough to crash for.
     */
    suspend fun write(
        context: Context,
        treeUri: Uri,
        fileName: String,
        content: String
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val dirUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            val fileUri = DocumentsContract.createDocument(resolver, dirUri, MIME_JSON, fileName)
                ?: return@runCatching null
            resolver.openOutputStream(fileUri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@runCatching null
            fileUri
        }.getOrNull()
    }

    /**
     * Deletes all but the newest [keep] backups in the folder.
     *
     * Only files this app named are considered, matched on the `mera-paisa-backup-` prefix. The
     * user picked a real folder that may well hold their own files, and a backup job that tidies
     * up somebody's documents is a far worse bug than one that leaves too many backups.
     */
    suspend fun prune(
        context: Context,
        treeUri: Uri,
        keep: Int = BackupStore.KEEP_COUNT
    ): Int = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

            val ours = mutableListOf<Pair<String, String>>() // documentId to displayName
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    if (name.startsWith(PREFIX) && name.endsWith(".json")) ours.add(id to name)
                }
            }

            // The timestamp in the name sorts chronologically, so newest-first is a reverse sort by
            // name. Using the name rather than a modified time keeps this correct even if a sync
            // client rewrites file metadata.
            val doomed = ours.sortedByDescending { it.second }.drop(keep)
            var deleted = 0
            doomed.forEach { (id, _) ->
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                if (runCatching { DocumentsContract.deleteDocument(resolver, docUri) }.getOrDefault(false)) {
                    deleted++
                }
            }
            deleted
        }.getOrDefault(0)
    }

    /**
     * Whether the folder is still usable.
     *
     * A persisted permission can be lost: the folder deleted, an SD card removed, the user
     * revoking access in system settings. Checked before a backup rather than after, so the failure
     * is reported as "the folder is gone" instead of a silent no-op.
     */
    suspend fun canWriteTo(context: Context, treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val held = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isWritePermission
            }
            if (!held) return@runCatching false
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            context.contentResolver.query(docUri, null, null, null, null)?.use { true } ?: false
        }.getOrDefault(false)
    }
}
