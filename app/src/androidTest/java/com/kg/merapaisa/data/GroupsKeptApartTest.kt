package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.repository.GroupRepository
import com.kg.merapaisa.repository.PersonRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A group is its own ledger. Nothing in it reaches a person's balance or settled flag, and nothing
 * done to a person reaches their groups. Driven through the repositories, as the screens drive it.
 */
@RunWith(AndroidJUnit4::class)
class GroupsKeptApartTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PersonDao
    private lateinit var groupDao: GroupDao
    private lateinit var people: PersonRepository
    private lateinit var groups: GroupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.personDao()
        groupDao = db.groupDao()
        people = PersonRepository(dao)
        groups = GroupRepository(groupDao, dao)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun person(name: String, currency: String = "INR") =
        dao.insertPerson(Person(name = name, currency = currency))

    private suspend fun balanceOf(id: Long) = dao.getPersonsWithBalancesNow().single { it.id == id }.balanceMinor

    private suspend fun groupBalances(groupId: Long) =
        groups.groupDetail(groupId).first().balances.associate { it.personId to it.amountMinor }

    @Test
    fun deletingAGroupExpenseLeavesASettledPersonSettled() = runBlocking {
        val self = dao.ensureSelf().id
        val asha = person("Asha")
        val group = groups.createGroup("Goa", "INR", listOf(asha))
        groups.addExpense(group, "Hotel", 1_000_00, self, listOf(self, asha))
        people.settle(asha)

        val hotel = groupDao.getExpenses(group).first().single()
        groups.deleteExpense(hotel.id)

        assertTrue(dao.getPersonNow(asha)!!.isSettled)
        assertEquals(0L, balanceOf(asha))
    }

    @Test
    fun aDebtMovesOnlyAsFarAsTheDirectBalance() = runBlocking {
        val self = dao.ensureSelf().id
        val asha = person("Asha")
        val bilal = person("Bilal")
        dao.recordEntry(Transaction(personId = asha, amountMinor = 300_00))
        val group = groups.createGroup("Goa", "INR", listOf(asha))
        groups.addExpense(group, "Hotel", 2_000_00, self, listOf(self, asha))

        val tooMuch = people.moveDebt(asha, bilal, 500_00)
        assertTrue("the group's ₹1,000 is not hers to move: $tooMuch", tooMuch is MoveDebtResult.MoreThanOwed)
        assertEquals(300_00L, (tooMuch as MoveDebtResult.MoreThanOwed).availableMinor)
    }

    @Test
    fun changingSomeonesCurrencyLeavesTheirGroupsAsTheyWere() = runBlocking {
        val self = dao.ensureSelf().id
        val asha = person("Asha")
        dao.recordEntry(Transaction(personId = asha, amountMinor = 100_00))
        val group = groups.createGroup("Goa", "INR", listOf(asha))
        groups.addExpense(group, "Hotel", 1_000_00, self, listOf(self, asha))
        val before = groupBalances(group)

        people.convertCurrency(asha, "USD", 0.012)

        assertEquals("the group is in rupees whatever Asha's own currency", before, groupBalances(group))
        assertEquals("INR", groupDao.getGroup(group).first()!!.currency)
        assertEquals(1_20L, balanceOf(asha))
    }

    @Test
    fun someoneKeptInDollarsCanBeInARupeeGroup() = runBlocking {
        val self = dao.ensureSelf().id
        val ben = person("Ben", currency = "USD")
        dao.recordEntry(Transaction(personId = ben, amountMinor = 40_00))
        val group = groups.createGroup("Goa", "INR", listOf(ben))
        groups.addExpense(group, "Hotel", 900_00, self, listOf(self, ben))

        assertEquals(-450_00L, groupBalances(group)[ben])
        assertEquals("his own balance stays in dollars, untouched", 40_00L, balanceOf(ben))
    }

    @Test
    fun anEvenGroupShowsNoPaymentsEitherWay() = runBlocking {
        val self = dao.ensureSelf().id
        val asha = person("Asha")
        val xyz = person("xyz")
        val group = groups.createGroup("Goa", "INR", listOf(asha, xyz), simplifyDebts = false)
        groups.addExpense(group, "Hotel", 900_00, self, listOf(self, asha, xyz))
        groups.addExpense(group, "Cab", 300_00, asha, listOf(asha, xyz))
        // Squared through you: xyz pays you for Asha as well, and Asha pays you that much less.
        groups.recordTransfer(group, xyz, self, 450_00)
        groups.recordTransfer(group, asha, self, 150_00)

        assertTrue(groupBalances(group).values.all { it == 0L })
        assertTrue("even is even, however it got there", groups.groupDetail(group).first().transfers.isEmpty())
        groups.setSimplifyDebts(group, true)
        assertTrue(groups.groupDetail(group).first().transfers.isEmpty())
    }

    @Test
    fun settlingSomeoneLeavesTheirGroupExactlyAsItWas() = runBlocking {
        val self = dao.ensureSelf().id
        val asha = person("Asha")
        dao.recordEntry(Transaction(personId = asha, amountMinor = 50_00))
        val group = groups.createGroup("Goa", "INR", listOf(asha))
        groups.addExpense(group, "Hotel", 100_00, self, listOf(self, asha))
        val before = groupBalances(group)

        people.settle(asha)

        assertTrue(dao.getPersonNow(asha)!!.isSettled)
        assertEquals(0L, balanceOf(asha))
        assertEquals(before, groupBalances(group))
        assertEquals(1, groupDao.getExpenses(group).first().size)
    }
}
