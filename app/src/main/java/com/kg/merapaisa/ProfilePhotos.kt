package com.kg.merapaisa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Longest edge of a stored avatar. It backs a 44dp view, so anything larger is waste. */
internal const val MAX_AVATAR_PX = 256

/**
 * Copies a picked image into internal storage, downscaled, on a background dispatcher, and
 * removes the file it replaces. Returns the stored path, or null if the image could not be read.
 */
suspend fun saveProfilePhoto(
    context: Context,
    source: Uri,
    previousPath: String?
): String? = withContext(Dispatchers.IO) {
    val scaled = decodeScaled(context, source) ?: return@withContext null

    val file = File(context.filesDir, "pfp_${System.currentTimeMillis()}.jpg")
    try {
        file.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
    } catch (e: Exception) {
        file.delete()
        return@withContext null
    } finally {
        scaled.recycle()
    }

    // Only once the replacement is safely on disk.
    deleteProfilePhoto(context, previousPath)
    file.absolutePath
}

/** Removes a stored avatar, ignoring anything that is not ours to delete. */
fun deleteProfilePhoto(context: Context, path: String?) {
    if (path.isNullOrBlank()) return
    val file = File(path)
    if (file.parentFile == context.filesDir && file.isFile) file.delete()
}

/**
 * Largest power-of-two downscale that still leaves the long edge at or above the target.
 *
 * This was written as `generateSequence(1) { it * 2 }.last { ... }`, which never terminates
 * on its own: `last` consumes the whole sequence, so the factor doubled until Int overflowed
 * to zero and the division threw. Photos were silently never saved.
 */
internal fun sampleSizeFor(longEdgePx: Int, targetPx: Int = MAX_AVATAR_PX): Int {
    if (longEdgePx <= targetPx) return 1
    var sample = 1
    while (longEdgePx / (sample * 2) >= targetPx) sample *= 2
    return sample
}

/**
 * A picked Uri can go stale between the picker returning it and us reading it — the file
 * deleted, the permission revoked, the provider gone. `openInputStream` throws
 * `FileNotFoundException` in that case rather than returning null, and this runs on a
 * background dispatcher where nothing would catch it. The contract is a null, not a crash.
 */
private fun decodeScaled(context: Context, source: Uri): Bitmap? = try {
    decodeScaledOrThrow(context, source)
} catch (e: Exception) {
    null
}

private fun decodeScaledOrThrow(context: Context, source: Uri): Bitmap? {
    // A bounds pass deliberately returns no bitmap — the dimensions land in `bounds` instead.
    // Guarding this with `?: return null` on the decode result therefore failed every time,
    // which is why photos were never saved. Only the stream itself is worth null-checking.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(source) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (longEdge <= 0) return null

    // Cheap power-of-two decode first, then an exact scale down to the target.
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(longEdge) }
    val decoded = context.contentResolver.openInputStream(source)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null

    val decodedLongEdge = maxOf(decoded.width, decoded.height)
    if (decodedLongEdge <= MAX_AVATAR_PX) return decoded

    val ratio = MAX_AVATAR_PX.toFloat() / decodedLongEdge
    val exact = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * ratio).toInt().coerceAtLeast(1),
        (decoded.height * ratio).toInt().coerceAtLeast(1),
        true
    )
    if (exact != decoded) decoded.recycle()
    return exact
}
