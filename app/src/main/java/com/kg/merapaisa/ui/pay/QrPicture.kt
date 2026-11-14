package com.kg.merapaisa.ui.pay

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.graphics.toArgb
import com.kg.merapaisa.QrInk
import com.kg.merapaisa.QrPaper
import com.kg.merapaisa.R
import com.kg.merapaisa.ui.QUIET_MODULES
import com.kg.merapaisa.ui.qrFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * The QR a reminder attaches, as a picture (v3.6.0). A friend saves it and scans it from their
 * gallery inside their own UPI app: the one route a UPI app accepted a payment on the phone
 * (15 Nov 2026), where a link opened from a chat was refused.
 */

/** Pixels per module: big enough to survive a chat app's compression, small enough to send fast. */
internal const val QR_PICTURE_MODULE_PX = 16

/**
 * The QR for [text], drawn from the same [qrFor] code as Show QR's, dark on light with the
 * four-module quiet zone, and [caption] under it so the picture reads on its own in a chat. The
 * caption wraps rather than shortens, since a UPI ID is never cut.
 */
internal fun drawQrPicture(text: String, caption: String, typeface: Typeface?): Bitmap {
    val qr = qrFor(text)
    val cell = QR_PICTURE_MODULE_PX
    val quiet = QUIET_MODULES * cell
    val side = qr.size * cell + quiet * 2

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = QrInk.toArgb()
        textSize = cell * 2.25f
        this.typeface = typeface
    }
    val captionWidth = side - quiet * 2
    val captionLayout = StaticLayout.Builder
        .obtain(caption, 0, caption.length, textPaint, captionWidth)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .build()

    // The code's own quiet zone sits above the caption; the same again closes the picture.
    val bitmap = Bitmap.createBitmap(side, side + captionLayout.height + quiet, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(QrPaper.toArgb())
    val ink = Paint().apply { color = QrInk.toArgb() }
    for (y in 0 until qr.size) {
        for (x in 0 until qr.size) {
            if (qr.getModule(x, y)) {
                val left = (quiet + x * cell).toFloat()
                val top = (quiet + y * cell).toFloat()
                canvas.drawRect(left, top, left + cell, top + cell, ink)
            }
        }
    }
    canvas.save()
    canvas.translate(quiet.toFloat(), side.toFloat())
    captionLayout.draw(canvas)
    canvas.restore()
    return bitmap
}

/**
 * Draws the picture into the cache's `qr/` folder and returns a `content://` URI for it, through
 * the FileProvider (a `file://` one throws). Earlier reminders' pictures are cleared first, so the
 * folder holds one at most, and each gets a new name, so an app that caches by address never sends
 * an older QR.
 */
internal suspend fun writeQrPicture(context: Context, text: String, caption: String): Uri = withContext(Dispatchers.IO) {
    val folder = File(context.cacheDir, QR_DIR).apply { mkdirs() }
    folder.listFiles()?.forEach { it.delete() }
    val typeface = ResourcesCompat.getFont(context, R.font.anek_latin_semibold)
    val bitmap = drawQrPicture(text, caption, typeface)
    val file = File(folder, "mera-paisa-qr-${System.currentTimeMillis()}.png")
    try {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
        bitmap.recycle()
    }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Hands [text] and the picture at [picture] to the share sheet together. Chat apps such as WhatsApp
 * send the text as the picture's caption; an app that keeps only the picture still has the amount
 * and UPI ID drawn on it.
 */
internal fun shareTextWithPicture(context: Context, text: String, picture: Uri, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, picture)
        putExtra(Intent.EXTRA_TEXT, text)
        // The grant travels with the clip, so the app picked in the chooser can read the picture.
        clipData = ClipData.newRawUri(null, picture)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private const val QR_DIR = "qr"
