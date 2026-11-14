package com.kg.merapaisa.ui.dialogs

import com.kg.merapaisa.data.upiReceiveLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the phone's prompt says before your own UPI ID is written, and what your QR carries. */
class OwnUpiPromptTest {

    @Test
    fun `setting, changing and removing each say so`() {
        assertEquals("Set your UPI ID", ownUpiPrompt(null, "me@okaxis").first)
        assertTrue(ownUpiPrompt(null, "me@okaxis").second.contains("me@okaxis"))
        assertEquals("Change your UPI ID", ownUpiPrompt("old@ybl", "me@okaxis").first)
        assertEquals("Remove your UPI ID", ownUpiPrompt("old@ybl", null).first)
    }

    @Test
    fun `your QR carries no amount`() {
        assertEquals("upi://pay?pa=me@okaxis&pn=Parth&cu=INR", upiReceiveLink("me@okaxis", "Parth"))
        assertEquals("upi://pay?pa=me@okaxis&cu=INR", upiReceiveLink("me@okaxis", null))
    }
}
