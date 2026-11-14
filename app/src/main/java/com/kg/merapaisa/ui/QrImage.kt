package com.kg.merapaisa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.QrInk
import com.kg.merapaisa.QrPaper
import com.kg.merapaisa.ui.theme.Shapes
import io.nayuki.qrcodegen.QrCode

/** The quiet zone the QR standard asks for around the code, in modules. */
private const val QUIET_MODULES = 4

/**
 * The modules of a QR code for [text], at medium error correction, which survives a phone screen's
 * glare and a cracked corner while keeping a UPI link small enough to scan from across a table.
 */
internal fun qrFor(text: String): QrCode = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)

/**
 * A QR code on its own light card, the same in every theme (see [QrInk]). [description] is what
 * TalkBack says instead of the picture, since nobody can listen to a QR code.
 */
@Composable
internal fun QrImage(text: String, description: String, modifier: Modifier = Modifier) {
    val qr = remember(text) { qrFor(text) }
    Box(
        modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(Shapes.medium)
            .background(QrPaper)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val cells = qr.size + QUIET_MODULES * 2
            val cell = size.width / cells
            for (y in 0 until qr.size) {
                for (x in 0 until qr.size) {
                    if (qr.getModule(x, y)) {
                        drawRect(
                            QrInk,
                            topLeft = Offset((x + QUIET_MODULES) * cell, (y + QUIET_MODULES) * cell),
                            // A hair over a cell, so neighbouring modules meet without seams.
                            size = Size(cell + 0.5f, cell + 0.5f)
                        )
                    }
                }
            }
        }
    }
}
