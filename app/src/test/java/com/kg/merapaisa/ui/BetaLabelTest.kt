package com.kg.merapaisa.ui

import androidx.compose.ui.text.font.FontStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** "(beta)" in italics, and nothing else changed. */
class BetaLabelTest {

    @Test
    fun `the words read exactly as before`() {
        assertEquals("Send an update link (beta)", betaLabel("Send an update link").text)
    }

    @Test
    fun `only the tag is italic`() {
        val label = betaLabel("Check this update link")
        val italic = label.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertEquals(1, italic.size)
        assertEquals("(beta)", label.text.substring(italic.single().start, italic.single().end))
        assertEquals("no other styling", 1, label.spanStyles.size)
    }
}
