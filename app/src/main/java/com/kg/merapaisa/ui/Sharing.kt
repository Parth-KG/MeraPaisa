package com.kg.merapaisa.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hands plain text — a reminder, a summary — straight to the share sheet. */
fun shareText(context: Context, text: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

/**
 * Writes an export into the cache directory and hands it to the share sheet. Sharing needs a
 * content:// URI — a file:// one throws FileUriExposedException — so it goes through the
 * FileProvider declared in the manifest.
 */
suspend fun writeExportToCache(context: Context, csv: String): Uri = withContext(Dispatchers.IO) {
    val exports = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
    // One file per export run, so an older share that is still open is not overwritten.
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val file = File(exports, "mera-paisa-$stamp.csv")
    file.writeText(csv)
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

fun shareCsv(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Mera Paisa ledger")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export ledger via"))
}

private const val EXPORT_DIR = "exports"
