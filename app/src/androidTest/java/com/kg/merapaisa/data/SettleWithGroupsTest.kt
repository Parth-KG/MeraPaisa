package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.repository.PersonRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settle up on someone who is also in a group with you.
 *
 * Their balance includes their share of group expenses between the two of you. Settle up used to
 * close only their direct entries, so they moved to the Settled tab still owing the group share.
 * Found on a phone: Asha settled, and still "owes you ₹1,000".
 */
@RunWith(AndroidJUnit4::class)
class SettleWithGroupsTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PersonDao
    private lateinit var groups: GroupDao
    private lateinit var repo: PersonRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.personDao()
        groups = db.groupDao()
        repo = PersonRepository(dao)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun balanceOf(personId: Long): Long {
        val self = dao.ensureSelf().id
        return dao.getPersonsWithBalancesNow(self).single { it.id == personId }.balanceMinor
    }

    /** A group where you paid [yourExpense] split evenly with [personId], and they paid [theirs]. */
    private suspend fun groupWith(personId: Long, yourExpense: Long, theirs: Long): Long {
        val self = dao.ensureSelf().id
        val group = groups.insertGroup(Group(name = "Goa"))
        groups.addMembers(listOf(GroupMember(group, self), GroupMember(group, personId)))
        groups.recordExpense(
            Expense(groupId = group, description = "Hotel", amountMinor = yourExpense, paidByPersonId = self),
            mapOf(self to yourExpense / 2, personId to yourExpense / 2)
        )
        if (theirs > 0) {
            groups.recordExpense(
                Expense(groupId = group, description = "Cab", amountMinor = theirs, paidByPersonId = personId),
                mapOf(self to theirs / 2, personId to theirs / 2)
            )
        }
        return group
    }

    @Test
    fun settlingClosesTheGroupShareAsWell() = runBlocking {
        val asha = dao.insertPerson(Person(name = "Asha"))
        dao.recordEntry(Transaction(personId = asha, amountMinor = 500_00, timestamp = 1_000))
        groupWith(asha, yourExpense = 2_000_00, theirs = 0)
        assertEquals("₹500 direct and ₹1,000 from the group", 1_500_00L, balanceOf(asha))

        repo.settle(asha)

        assertEquals("settled means nothing is owed either way", 0L, balanceOf(asha))
        assertTrue(dao.getPersonNow(asha)!!.isSettled)
    }

    @Test
    fun theGroupShowsThePaymentRatherThanAStrayEntry() = runBlocking {
        val asha = dao.insertPerson(Person(name = "Asha"))
        val group = groupWith(asha, yourExpense = 2_000_00, theirs = 600_00)
        val self = dao.ensureSelf().id

        repo.settle(asha)

        val payment = groups.getAllExpensesForBackup().single { it.groupId == group && it.isSettlement }
        assertEquals("they owed ₹1,000 less ₹300", 700_00L, payment.amountMinor)
        assertEquals("they pay you", asha, payment.paidByPersonId)
        assertTrue("the direct ledger is untouched", dao.getTransactionsForPersonNow(asha).isEmpty())
        assertEquals(0L, balanceOf(asha))
        assertEquals("your own position in the group closes too", 0L,
            dao.groupPositionsWith(asha, self).sumOf { it.amountMinor })
    }

    @Test
    fun whenYouOweThemInTheGroupYouPayThem() = runBlocking {
        val bilal = dao.insertPerson(Person(name = "Bilal"))
        val group = groupWith(bilal, yourExpense = 200_00, theirs = 1_000_00)
        assertEquals("you owe them ₹500 less ₹100", -400_00L, balanceOf(bilal))

        repo.settle(bilal)

        val payment = groups.getAllExpensesForBackup().single { it.groupId == group && it.isSettlement }
        assertEquals(dao.ensureSelf().id, payment.paidByPersonId)
        assertEquals(0L, balanceOf(bilal))
    }
}
