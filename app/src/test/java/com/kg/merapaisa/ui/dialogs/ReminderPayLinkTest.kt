package com.kg.merapaisa.ui.dialogs

import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.readPastedUpi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A reminder carries a pay link only to someone who owes you rupees, once your UPI ID is set. */
class ReminderPayLinkTest {

    private fun person(balance: Long, currency: String = "INR") =
        PersonWithBalance(Person(id = 1, name = "Asha", currency = currency), balance)

    @Test
    fun `someone who owes you rupees gets the link, the ID and the amount`() {
        val asha = person(500_00)
        val link = reminderPayLink(asha, "parth@okaxis", "Parth")!!
        assertEquals("upi://pay?pa=parth@okaxis&pn=Parth&am=500.00&cu=INR", link)
        val text = buildReminderText(asha, link, "parth@okaxis")
        assertTrue(text.startsWith("Hi Asha, you owe me ₹500. Send it when you can."))
        assertTrue(text.contains(link))
        assertTrue(text.endsWith("Or to parth@okaxis, ₹500"))
    }

    @Test
    fun `the link pays you, whatever else is in the message`() {
        val link = reminderPayLink(person(500_00), "parth@okaxis", null)!!
        assertEquals("parth@okaxis", readPastedUpi(link)?.id)
    }

    @Test
    fun `no link without your UPI ID, for someone you owe, at zero, or outside rupees`() {
        assertNull(reminderPayLink(person(500_00), null, "Parth"))
        assertNull(reminderPayLink(person(500_00), "not an id", "Parth"))
        assertNull(reminderPayLink(person(-40_00), "parth@okaxis", "Parth"))
        assertNull(reminderPayLink(person(0), "parth@okaxis", "Parth"))
        assertNull(reminderPayLink(person(50_00, "USD"), "parth@okaxis", "Parth"))
    }

    @Test
    fun `without a link the reminder reads as it always has`() {
        assertEquals("Hi Asha, you owe me ₹500. Send it when you can.", buildReminderText(person(500_00)))
        assertEquals("Hi Asha, I owe you ₹40. Will send it soon.", buildReminderText(person(-40_00)))
    }
}
