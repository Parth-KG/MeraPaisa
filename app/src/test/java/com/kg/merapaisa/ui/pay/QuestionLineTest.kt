package com.kg.merapaisa.ui.pay

import com.kg.merapaisa.data.PaymentQuestion
import com.kg.merapaisa.data.QUESTION_PAY
import com.kg.merapaisa.data.QUESTION_REMINDER
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/** The words on "Did these go through?". */
class QuestionLineTest {

    @Test
    fun `a reminder asks whether they paid you`() {
        val q = PaymentQuestion(personId = 1, amountMinor = -500_00, kind = QUESTION_REMINDER, createdAt = 0, balanceAtCreation = 500_00)
        assertEquals("Did Asha pay you ₹500?", questionLine(q, "Asha"))
    }

    @Test
    fun `a pay asks whether yours went through`() {
        val q = PaymentQuestion(personId = 2, amountMinor = 40_00, kind = QUESTION_PAY, createdAt = 0, balanceAtCreation = -40_00)
        assertEquals("Did your ₹40 to Bilal go through?", questionLine(q, "Bilal"))
    }

    @Test
    fun `each says when it happened`() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        try {
            val at = 1_794_431_994_730L // 12 Nov 2026
            assertEquals("Reminder sent 12 Nov", questionWhen(PaymentQuestion(personId = 1, amountMinor = -1, kind = QUESTION_REMINDER, createdAt = at, balanceAtCreation = 1), Locale.UK))
            assertEquals("UPI app opened 12 Nov", questionWhen(PaymentQuestion(personId = 1, amountMinor = 1, kind = QUESTION_PAY, createdAt = at, balanceAtCreation = -1), Locale.UK))
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
