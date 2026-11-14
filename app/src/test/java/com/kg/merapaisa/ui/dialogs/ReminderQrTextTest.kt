package com.kg.merapaisa.ui.dialogs

import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** What a reminder sends with the QR attached and without it, and what you edit before it goes. */
class ReminderQrTextTest {

    private val asha = PersonWithBalance(Person(id = 1, name = "Asha", currency = "INR"), 500_00)

    @Test
    fun `the draft you edit never carries the UPI line`() {
        val draft = buildReminderText(asha)
        assertEquals("Hi Asha, you owe me ₹500. Send it when you can.", draft)
        assertFalse(draft.contains("UPI"))
    }

    @Test
    fun `with the QR on, the UPI line follows your message`() {
        assertEquals(
            "Hi Asha, you owe me ₹500. Send it when you can.\n\nScan the QR to pay me by UPI, or pay parth@okaxis.",
            reminderToSend(buildReminderText(asha), log = null, qrLine = reminderQrLine("parth@okaxis"))
        )
    }

    @Test
    fun `the UPI line sits between your message and the entries`() {
        assertEquals(
            "Sorted?\n\nScan the QR to pay me by UPI, or pay parth@okaxis.\n\nEvery entry so far:\nthe log",
            reminderToSend("Sorted?", log = "the log", qrLine = reminderQrLine("parth@okaxis"))
        )
    }

    @Test
    fun `with the QR off, your message goes alone, as you left it`() {
        assertEquals("Paid yet? It's for the cab.", reminderToSend("Paid yet? It's for the cab.", log = null, qrLine = null))
        assertEquals(
            "Paid yet?\n\nEvery entry so far:\nthe log",
            reminderToSend("Paid yet?", log = "the log", qrLine = null)
        )
    }

    @Test
    fun `the picture's caption names the amount and your UPI ID`() {
        assertEquals("Scan to pay ₹1,200.50 to parth@okaxis", reminderQrCaption(1_200_50, "parth@okaxis"))
    }

    @Test
    fun `the other two shapes of a reminder read as they always have`() {
        assertEquals("Hi Asha, I owe you ₹40. Will send it soon.", buildReminderText(asha.copy(balanceMinor = -40_00)))
        assertEquals("Hi Asha, we're even now.", buildReminderText(asha.copy(balanceMinor = 0)))
    }
}
