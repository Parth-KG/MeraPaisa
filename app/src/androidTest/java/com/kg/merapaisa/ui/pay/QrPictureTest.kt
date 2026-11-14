package com.kg.merapaisa.ui.pay

import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.QrInk
import com.kg.merapaisa.QrPaper
import com.kg.merapaisa.data.upiPayLink
import com.kg.merapaisa.ui.QUIET_MODULES
import com.kg.merapaisa.ui.qrFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reminder's QR picture is the same code Show QR draws, module for module, dark on light with
 * the quiet zone, and the caption sits under it rather than over the code.
 */
@RunWith(AndroidJUnit4::class)
class QrPictureTest {

    private val link = upiPayLink("parth@okaxis", "Parth", 500_00)

    @Test
    fun everyModuleMatchesShowQrsCode() {
        val qr = qrFor(link)
        val picture = drawQrPicture(link, "Scan to pay ₹500 to parth@okaxis", typeface = null)
        val cell = QR_PICTURE_MODULE_PX
        val quiet = QUIET_MODULES * cell
        assertEquals(qr.size * cell + quiet * 2, picture.width)
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                val pixel = picture.getPixel(quiet + x * cell + cell / 2, quiet + y * cell + cell / 2)
                val expected = if (qr.getModule(x, y)) QrInk.toArgb() else QrPaper.toArgb()
                assertEquals("module $x,$y", expected, pixel)
            }
        }
        picture.recycle()
    }

    @Test
    fun theQuietZoneIsLightAndTheCaptionIsUnderTheCode() {
        val picture = drawQrPicture(link, "Scan to pay ₹500 to parth@okaxis", typeface = null)
        val cell = QR_PICTURE_MODULE_PX
        val quiet = QUIET_MODULES * cell
        val side = picture.width
        assertTrue("room for the caption", picture.height > side)
        // The quiet zone round the code holds no ink.
        for (i in 0 until side step cell / 2) {
            assertEquals(QrPaper.toArgb(), picture.getPixel(i, quiet / 2))
            assertEquals(QrPaper.toArgb(), picture.getPixel(quiet / 2, i))
            assertEquals(QrPaper.toArgb(), picture.getPixel(side - quiet / 2, i))
        }
        // Some ink below the code: the caption.
        var inkBelow = false
        for (y in side until picture.height) for (x in 0 until side) {
            if (picture.getPixel(x, y) != QrPaper.toArgb()) { inkBelow = true; break }
        }
        assertTrue(inkBelow)
        assertEquals(Color.alpha(picture.getPixel(0, 0)), 255)
        picture.recycle()
    }

    @Test
    fun aLongUpiIdWrapsInsteadOfBeingCut() {
        val longId = "a.very.long.upi.id.for.someone.with.many.names@okaxis"
        val short = drawQrPicture(link, "Scan to pay ₹500 to parth@okaxis", typeface = null)
        val long = drawQrPicture(link, "Scan to pay ₹500 to $longId", typeface = null)
        assertEquals(short.width, long.width)
        assertTrue("a longer caption takes more lines", long.height > short.height)
        short.recycle()
        long.recycle()
    }
}
