package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which payment questions are still asked on opening, and which lapse unasked. */
class PaymentQuestionsTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun q(id: Long, person: Long, createdAt: Long, balance: Long) =
        PaymentQuestion(id = id, personId = person, amountMinor = -balance, kind = QUESTION_REMINDER, createdAt = createdAt, balanceAtCreation = balance)

    @Test
    fun `a fresh question with the balance unchanged is asked`() {
        val fresh = q(1, 1, now - day, 500_00)
        assertEquals(listOf(fresh), questionsStillAsking(listOf(fresh), mapOf(1L to 500_00L), now))
    }

    @Test
    fun `a question lapses after seven days`() {
        val week = q(1, 1, now - 7 * day, 500_00)
        val older = q(2, 1, now - 7 * day - 1, 500_00)
        assertEquals(listOf(week), questionsStillAsking(listOf(week, older), mapOf(1L to 500_00L), now))
    }

    @Test
    fun `a balance changed some other way drops the question`() {
        assertEquals(emptyList<PaymentQuestion>(), questionsStillAsking(listOf(q(1, 1, now, 500_00)), mapOf(1L to 300_00L), now))
    }

    @Test
    fun `a person who has gone takes their questions`() {
        assertEquals(emptyList<PaymentQuestion>(), questionsStillAsking(listOf(q(1, 9, now, 500_00)), mapOf(1L to 500_00L), now))
    }

    @Test
    fun `a question from the future, a clock set back, is not trusted`() {
        assertEquals(emptyList<PaymentQuestion>(), questionsStillAsking(listOf(q(1, 1, now + day, 500_00)), mapOf(1L to 500_00L), now))
    }
}
