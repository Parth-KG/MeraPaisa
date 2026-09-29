package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.repository.PersonRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two places where a group share was left out of a person's balance, found in a review pass.
 *
 * A group expense did not reopen a settled member, so their new debt sat in the Settled tab, out
 * of the net position and the widget. And moving a debt checked only direct entries, so the sheet
 * offered the whole balance and then refused the group part of it.
 */
@RunWith(AndroidJUnit4::class)
class GroupReopensAndMoveLimitTest {

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

    private suspend fun groupOf(vararg people: Long): Long {
        val self = dao.ensureSelf().id
        val group = groups.insertGroup(Group(name = "Goa"))
        groups.addMembers((people.toList() + self).map { GroupMember(group, it) })
        return group
    }

    @Test
    fun aGroupExpenseYouPaidReopensASettledMember() = runBlocking {
        val self = dao.ensureSelf().id
        val asha = dao.insertPerson(Person(name = "Asha"))
        dao.setSettled(asha, true)
        val group = groupOf(asha)

        groups.recordExpense(
            Expense(groupId = group, description = "Hotel", amountMinor = 2_000_00, paidByPersonId = self),
            mapOf(self to 1_000_00, asha to 1_000_00)
        )

        assertFalse("she owes you her share now, so she is live again", dao.getPersonNow(asha)!!.isSettled)
    }

    @Test
    fun anExpenseBetweenTwoOthersLeavesThemAsTheyWere() = runBlocking {
        val asha = dao.insertPerson(Person(name = "Asha"))
        val bilal = dao.insertPerson(Person(name = "Bilal"))
        dao.setSettled(asha, true)
        dao.setSettled(bilal, true)
        val group = groupOf(asha, bilal)

        groups.recordExpense(
            Expense(groupId = group, description = "Cab", amountMinor = 600_00, paidByPersonId = asha),
            mapOf(asha to 300_00, bilal to 300_00)
        )

        assertTrue("nothing between either of them and you moved", dao.getPersonNow(asha)!!.isSettled)
        assertTrue(dao.getPersonNow(bilal)!!.isSettled)
    }

    @Test
    fun aDebtCanMoveUpToTheWholeBalanceIncludingTheGroupShare() = runBlocking {
        val self = dao.ensureSelf().id
        val asha = dao.insertPerson(Person(name = "Asha"))
        val bilal = dao.insertPerson(Person(name = "Bilal"))
        dao.recordEntry(Transaction(personId = asha, amountMinor = 500_00, timestamp = 1_000))
        val group = groupOf(asha)
        groups.recordExpense(
            Expense(groupId = group, description = "Hotel", amountMinor = 2_000_00, paidByPersonId = self),
            mapOf(self to 1_000_00, asha to 1_000_00)
        )

        val moved = repo.moveDebt(asha, bilal, 1_200_00)
        assertTrue("₹1,500 is owed in all, so ₹1,200 can move: $moved", moved is MoveDebtResult.Moved)

        val tooMuch = repo.moveDebt(asha, bilal, 400_00)
        assertTrue("only ₹300 is left now: $tooMuch", tooMuch is MoveDebtResult.MoreThanOwed)
        assertEquals(300_00L, (tooMuch as MoveDebtResult.MoreThanOwed).availableMinor)
    }
}
