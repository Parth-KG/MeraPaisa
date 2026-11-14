package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A payment question is answered once: the entry and the question go together. */
@RunWith(AndroidJUnit4::class)
class PaymentQuestionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PersonDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.personDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun bilalYouOwe40(): Long {
        val id = dao.insertPerson(Person(name = "Bilal", currency = "INR", upiId = "bilal@ybl"))
        dao.recordEntry(Transaction(personId = id, amountMinor = -40_00, note = "Cab"))
        return id
    }

    private fun question(personId: Long) =
        PaymentQuestion(personId = personId, amountMinor = 40_00, kind = QUESTION_PAY, createdAt = 1, balanceAtCreation = -40_00)

    @Test
    fun aYesRecordsThePaymentWithTheUpiMarkAndRemovesTheQuestion() = runBlocking {
        val id = bilalYouOwe40()
        val q = dao.insertQuestion(question(id))

        assertTrue(dao.answerQuestion(q, Transaction(personId = id, amountMinor = 40_00, paidByUpi = true)))

        assertEquals(0L, dao.getBalanceNow(id))
        assertEquals(1, dao.getTransactionsForPersonNow(id).count { it.paidByUpi })
        assertTrue(dao.questionsNow().isEmpty())
    }

    @Test
    fun aSecondYesRecordsNothing() = runBlocking {
        val id = bilalYouOwe40()
        val q = dao.insertQuestion(question(id))
        val entry = Transaction(personId = id, amountMinor = 40_00, paidByUpi = true)

        assertTrue(dao.answerQuestion(q, entry))
        assertFalse("the question has gone, so a second tap records nothing", dao.answerQuestion(q, entry.copy(uid = newEntryUid())))

        assertEquals(0L, dao.getBalanceNow(id))
        assertEquals(2, dao.getTransactionsForPersonNow(id).size)
    }

    @Test
    fun aQuestionGoesWithItsPerson() = runBlocking {
        val id = bilalYouOwe40()
        dao.insertQuestion(question(id))
        dao.deletePersonWithHistory(dao.getPersonNow(id)!!)
        assertTrue(dao.questionsNow().isEmpty())
    }

    @Test
    fun aUpiIdIsStoredOnThePerson() = runBlocking {
        val id = bilalYouOwe40()
        assertEquals("bilal@ybl", dao.getPersonNow(id)!!.upiId)
        assertEquals("bilal@ybl", dao.getPersonsWithBalances().first().single { it.id == id }.person.upiId)
    }
}
