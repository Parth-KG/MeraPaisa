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
    fun deletingTheSettleUpPaymentReopensThem() = runBlocking {
        val self = dao.ensureSelf().id
        val asha = dao.insertPerson(Person(name = "Asha"))
        val group = groupOf(asha)
        groups.recordExpense(
            Expense(groupId = group, description = "Hotel", amountMinor = 2_000_00, paidByPersonId = self),
            mapOf(self to 1_000_00, asha to 1_000_00)
        )
        dao.settle(asha, selfId = self)
        assertTrue(dao.getPersonNow(asha)!!.isSettled)

        val payment = groups.getAllExpensesForBackup().single { it.isSettlement }
        groups.deleteExpenseAndReopen(payment.id)

        assertFalse("she owes her share again, so she is live again", dao.getPersonNow(asha)!!.isSettled)
    }

    @Test
    fun deletingAGroupThatNetsToNothingLeavesThemSettled() = runBlocking {
        val self = dao.ensureSelf().id
        val asha = dao.insertPerson(Person(name = "Asha"))
        val group = groupOf(asha)
        groups.recordExpense(
            Expense(groupId = group, description = "Hotel", amountMinor = 2_000_00, paidByPersonId = self),
            mapOf(self to 1_000_00, asha to 1_000_00)
        )
        dao.settle(asha, selfId = self)

        groups.deleteGroupAndReopen(group)

        assertEquals(0L, dao.getFullBalanceNow(asha, self))
        assertTrue("the expense and its payment went together", dao.getPersonNow(asha)!!.isSettled)
    }

    @Test
    fun aGroupSquaredThroughYouLeavesNobodyOwingYou() = runBlocking {
        val self = dao.ensureSelf().id
        val asha = dao.insertPerson(Person(name = "Asha"))
        val xyz = dao.insertPerson(Person(name = "xyz"))
        val group = groupOf(asha, xyz)
        groups.recordExpense(
            Expense(groupId = group, description = "Hotel", amountMinor = 1_000_00, paidByPersonId = self),
            mapOf(self to 333_34, asha to 333_33, xyz to 333_33)
        )
        groups.recordExpense(
            Expense(groupId = group, description = "Cab", amountMinor = 300_00, paidByPersonId = asha),
            mapOf(asha to 150_00, xyz to 150_00)
        )
        // The routed plan: xyz pays you Asha's 150 too, and Asha pays you 150 less.
        groups.recordExpense(
            Expense(groupId = group, description = "Settlement", amountMinor = 483_33, paidByPersonId = xyz, isSettlement = true),
            mapOf(self to 483_33)
        )
        groups.recordExpense(
            Expense(groupId = group, description = "Settlement", amountMinor = 183_33, paidByPersonId = asha, isSettlement = true),
            mapOf(self to 183_33)
        )

        val balances = dao.getPersonsWithBalancesNow(self).associate { it.id to it.balanceMinor }
        assertEquals("the group is even, so it adds nothing", 0L, balances[asha])
        assertEquals(0L, balances[xyz])
        assertEquals(0L, dao.getFullBalanceNow(xyz, self))
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
