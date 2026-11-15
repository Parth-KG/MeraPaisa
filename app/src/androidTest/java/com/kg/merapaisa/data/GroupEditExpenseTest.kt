package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.repository.GroupRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Editing a group expense (v3.7.0): the rows change in place, and everything derived follows. */
@RunWith(AndroidJUnit4::class)
class GroupEditExpenseTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: GroupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = GroupRepository(db.groupDao(), db.personDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun person(name: String): Long = db.personDao().insertPerson(Person(name = name, currency = "INR"))

    @Test
    fun anEditMovesBalancesAndThePlanAsAFreshExpenseWould() = runBlocking {
        val asha = person("Asha")
        val bilal = person("Bilal")
        val edited = repo.createGroup("Goa", "INR", listOf(asha, bilal))
        val fresh = repo.createGroup("Goa again", "INR", listOf(asha, bilal))
        val me = repo.self().id

        repo.addExpense(edited, "Hotel", 300_00, me, listOf(me, asha, bilal))
        val id = repo.groupDetail(edited).first().expenses.single().id
        val shares = mapOf(asha to 400_00L, bilal to 500_00L)
        repo.editExpense(id, "Hotel, two nights", 900_00, asha, shares)
        repo.addExpense(fresh, "Hotel, two nights", 900_00, asha, shares)

        val a = repo.groupDetail(edited).first()
        val b = repo.groupDetail(fresh).first()
        assertEquals(b.balances, a.balances)
        assertEquals(b.transfers, a.transfers)
        val row = a.expenses.single()
        assertEquals("Hotel, two nights", row.description)
        assertEquals(asha, row.paidByPersonId)
        assertEquals(shares, a.shares.associate { it.personId to it.shareMinor })
    }

    @Test
    fun theDateAndTheRowStay() = runBlocking {
        val asha = person("Asha")
        val group = repo.createGroup("Goa", "INR", listOf(asha))
        val me = repo.self().id
        repo.addExpense(group, "Cab", 100_00, me, listOf(me, asha))
        val before = repo.groupDetail(group).first().expenses.single()
        repo.editExpense(before.id, "Cab home", 120_00, me, mapOf(me to 60_00L, asha to 60_00L))
        val after = repo.groupDetail(group).first().expenses.single()
        assertEquals(before.id, after.id)
        assertEquals(before.timestamp, after.timestamp)
        assertEquals(false, after.isSettlement)
    }

    @Test
    fun customSharesAreStoredAsGivenAndReadBack() = runBlocking {
        val asha = person("Asha")
        val group = repo.createGroup("Goa", "INR", listOf(asha))
        val me = repo.self().id
        repo.addExpense(group, "Dinner", 1_000_00, me, mapOf(me to 700_00L, asha to 300_00L))
        val detail = repo.groupDetail(group).first()
        assertEquals(mapOf(me to 700_00L, asha to 300_00L), detail.shares.associate { it.personId to it.shareMinor })
        assertEquals(300_00L, detail.balances.single { it.personId == me }.amountMinor)
    }

    @Test
    fun anEditOfAnExpenseThatHasGoneWritesNothing() = runBlocking {
        val asha = person("Asha")
        val group = repo.createGroup("Goa", "INR", listOf(asha))
        val me = repo.self().id
        repo.addExpense(group, "Cab", 100_00, me, listOf(me, asha))
        val id = repo.groupDetail(group).first().expenses.single().id
        repo.deleteExpense(id)
        repo.editExpense(id, "Cab", 200_00, me, mapOf(me to 200_00L))
        assertEquals(0, repo.groupDetail(group).first().expenses.size)
        assertEquals(0, repo.groupDetail(group).first().shares.size)
    }
}
