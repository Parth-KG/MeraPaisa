package com.kg.merapaisa

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the real save path against a real image. Both bugs that stopped photos ever
 * being written were invisible to the compiler and to every JVM test, because they were in
 * how BitmapFactory is called.
 */
@RunWith(AndroidJUnit4::class)
class ProfilePhotoSaveTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val written = mutableListOf<File>()

    @After
    fun tearDown() {
        written.forEach { it.delete() }
        context.filesDir.listFiles()?.filter { it.name.startsWith("pfp_") }?.forEach { it.delete() }
    }

    private fun sourceImage(width: Int, height: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val file = File(context.cacheDir, "src_${System.nanoTime()}.jpg").also { written += it }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    @Test
    fun savingALargePhotoWritesADownscaledFile() = runBlocking {
        val path = saveProfilePhoto(context, sourceImage(2000, 1500), previousPath = null)

        assertNotNull("a picked photo must actually be written to disk", path)
        val saved = File(path!!)
        assertTrue("the file should exist", saved.isFile)
        assertTrue("and not be empty", saved.length() > 0)
        assertEquals("stored inside the app's files dir", context.filesDir, saved.parentFile)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        saved.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        assertTrue(
            "long edge should be scaled down to ${MAX_AVATAR_PX}px, was ${bounds.outWidth}x${bounds.outHeight}",
            maxOf(bounds.outWidth, bounds.outHeight) <= MAX_AVATAR_PX
        )
        assertTrue("aspect ratio should survive", bounds.outWidth > bounds.outHeight)
    }

    @Test
    fun aSmallPhotoIsKeptAsItIs() = runBlocking {
        val path = saveProfilePhoto(context, sourceImage(100, 100), previousPath = null)
        assertNotNull(path)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        File(path!!).inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        assertEquals(100, bounds.outWidth)
        assertEquals(100, bounds.outHeight)
    }

    @Test
    fun replacingAPhotoRemovesTheOneItReplaced() = runBlocking {
        val first = saveProfilePhoto(context, sourceImage(600, 600), previousPath = null)
        assertNotNull(first)
        val second = saveProfilePhoto(context, sourceImage(600, 600), previousPath = first)

        assertNotNull(second)
        assertTrue("the replacement should exist", File(second!!).isFile)
        assertTrue("the one it replaced should be gone", !File(first!!).exists())
    }

    @Test
    fun anUnreadableSourceReturnsNullRatherThanThrowing() = runBlocking {
        val missing = Uri.fromFile(File(context.cacheDir, "does-not-exist.jpg"))
        assertEquals(null, saveProfilePhoto(context, missing, previousPath = null))
    }
}
