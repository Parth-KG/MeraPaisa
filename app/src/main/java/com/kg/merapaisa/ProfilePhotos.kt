package com.kg.merapaisa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.kg.merapaisa.repository.PhotoStore
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

/**
 * Saves a photo that arrived as bytes, from a backup being restored, and returns its path, or null
 * if the bytes are not an image.
 *
 * Each file gets a name no other can have. `saveProfilePhoto` names files by the millisecond, and a
 * restore saves several in the same millisecond, so they overwrote each other. The bytes are kept
 * as they are: they are a photo this app already scaled and saved once.
 */
fun saveProfilePhotoBytes(dir: File, bytes: ByteArray): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val file = try {
        File.createTempFile("pfp_", ".jpg", dir)
    } catch (e: Exception) {
        return null
    }
    return try {
        file.writeBytes(bytes)
        file.absolutePath
    } catch (e: Exception) {
        file.delete()
        null
    }
}

/**
 * The app's own photo files, for backups and restores: only `pfp_*.jpg` directly in [dir], the
 * private files directory, is read, listed or deleted. Tests pass a directory of their own, so a
 * Replace in a test can never clear away the photos of the app it runs in.
 */
class AppPhotoStore(context: Context, private val dir: File = context.filesDir) : PhotoStore {
    private fun ours(file: File) =
        file.parentFile == dir && file.name.startsWith("pfp_") && file.name.endsWith(".jpg")

    override fun read(path: String): ByteArray? = try {
        File(path).takeIf { ours(it) && it.isFile && it.length() in 1..MAX_PHOTO_FILE_BYTES }?.readBytes()
    } catch (e: Exception) {
        null
    }

    override fun save(bytes: ByteArray): String? = saveProfilePhotoBytes(dir, bytes)

    override fun delete(path: String) {
        val file = File(path)
        if (ours(file) && file.isFile) file.delete()
    }

    override fun all(): List<String> =
        dir.listFiles()?.filter { ours(it) && it.isFile }?.map { it.absolutePath }.orEmpty()
}

/** Far past any 256px JPEG this app writes; a file larger than this is not one of its photos. */
private const val MAX_PHOTO_FILE_BYTES = 2L * 1024 * 1024

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
 * A picked Uri can go stale between the picker returning it and us reading it: the file
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
    // A bounds pass deliberately returns no bitmap. The dimensions land in `bounds` instead.
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
