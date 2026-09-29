package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.repository.GroupRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Groups shipped in v2.0 with no test of any kind. Covers the write path and the invariant
 * every group balance rests on: the shares always add back up to the expense.
 */
@RunWith(AndroidJUnit4::class)
class GroupRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: GroupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        repo = GroupRepository(db.groupDao(), db.personDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun person(name: String): Long =
        db.personDao().insertPerson(Person(name = name, currency = "INR"))

    @Test
    fun anExpenseSplitThreeWaysAddsBackUpToItself() = runBlocking {
        val asha = person("Asha")
        val bilal = person("Bilal")
        val groupId = repo.createGroup("Goa", "INR", listOf(asha, bilal))
        val me = repo.self().id

        repo.addExpense(groupId, "Hotel", 500_00, me, listOf(me, asha, bilal))

        val owed = db.groupDao().owedByMember(groupId).associate { it.personId to it.amountMinor }
        assertEquals("shares must reconstruct the expense exactly", 500_00L, owed.values.sum())
        assertTrue(
            "500 cannot be divided three ways, so a share may be off by a paisa and no more",
            owed.values.all { it == 166_66L || it == 166_67L }
        )

        val detail = repo.groupDetail(groupId).first()
        assertEquals(3, detail.balances.size)
        assertEquals("a group always nets to zero", 0L, detail.balances.sumOf { it.amountMinor })
        assertEquals(
            "you fronted the lot, so you are owed all of it but your own share",
            500_00L - owed.getValue(me),
            detail.balances.single { it.personId == me }.amountMinor
        )
    }

    @Test
    fun everyoneIsAMemberIncludingYou() = runBlocking {
        val asha = person("Asha")
        val groupId = repo.createGroup("Flat 402", "INR", listOf(asha))

        val members = db.groupDao().getMembers(groupId).first()
        assertEquals(2, members.size)
        assertTrue("a group you are not in is somebody else's ledger", members.any { it.isSelf })
    }

    @Test
    fun recordingEverySuggestedTransferLeavesTheGroupSquare() = runBlocking {
        val asha = person("Asha")
        val bilal = person("Bilal")
        val groupId = repo.createGroup("Goa", "INR", listOf(asha, bilal))
        val me = repo.self().id

        repo.addExpense(groupId, "Hotel", 500_00, me, listOf(me, asha, bilal))
        repo.addExpense(groupId, "Cab", 90_00, asha, listOf(me, asha))

        val before = repo.groupDetail(groupId).first().balances
        settleUp(before).forEach { repo.recordTransfer(groupId, it.fromPersonId, it.toPersonId, it.amountMinor) }

        val after = repo.groupDetail(groupId).first().balances
        assertTrue("settling every suggested transfer must leave nobody owing", after.all { it.amountMinor == 0L })
    }

    @Test
    fun deletingAnExpenseTakesItsSharesWithIt() = runBlocking {
        val asha = person("Asha")
        val groupId = repo.createGroup("Goa", "INR", listOf(asha))
        val me = repo.self().id

        repo.addExpense(groupId, "Hotel", 100_00, me, listOf(me, asha))
        val expense = db.groupDao().getExpenses(groupId).first().single()
        repo.deleteExpense(expense.id)

        assertTrue(
            "orphaned shares would leave the group's balances refusing to net to zero",
            db.groupDao().owedByMember(groupId).isEmpty()
        )
        assertTrue(repo.groupDetail(groupId).first().balances.all { it.amountMinor == 0L })
    }

    // -----------------------------------------------------------------------------------------
    // Deleting a member
    //
    // The delete confirmation counts the expenses a person *paid for*, because those cascade away
    // and move everyone else's position. It says nothing about the expenses they merely had a
    // share of, and those shares cascade away too, leaving an expense that is no longer fully
    // shared out. Every balance in the group then quietly stops adding up to zero.
    // -----------------------------------------------------------------------------------------

    @Test
    fun deletingAMemberLeavesEveryExpenseStillFullySharedOut() = runBlocking {
        val asha = person("Asha")
        val bilal = person("Bilal")
        val groupId = repo.createGroup("Goa", "INR", listOf(asha, bilal))
        val me = repo.self().id

        // Asha fronts it; all three share it. Bilal did not pay for anything.
        repo.addExpense(groupId, "Hotel", 300_00, asha, listOf(me, asha, bilal))

        db.personDao().deletePersonWithHistory(db.personDao().getPersonNow(bilal)!!)

        val expenses = db.groupDao().getAllExpensesForBackup()
        val shares = db.groupDao().getAllExpenseSharesForBackup()
        expenses.forEach { e ->
            assertEquals(
                "expense ${e.description} is no longer fully shared out",
                e.amountMinor,
                shares.filter { it.expenseId == e.id }.sumOf { it.shareMinor }
            )
        }
    }

    /**
     * The same thing stated as the property everything downstream depends on: `settleUp` documents
     * that balances sum to zero because every expense is fully shared out. If that stops being
     * true, the payment plan it produces leaves somebody unsquared and nobody is told.
     */
    @Test
    fun deletingAMemberLeavesTheGroupBalancesSummingToZero() = runBlocking {
        val asha = person("Asha")
        val bilal = person("Bilal")
        val groupId = repo.createGroup("Goa", "INR", listOf(asha, bilal))
        val me = repo.self().id
        repo.addExpense(groupId, "Hotel", 300_00, asha, listOf(me, asha, bilal))

        db.personDao().deletePersonWithHistory(db.personDao().getPersonNow(bilal)!!)

        val members = db.groupDao().getMemberIds(groupId)
        val expenses = db.groupDao().getAllExpensesForBackup()
        val shares = db.groupDao().getAllExpenseSharesForBackup()
        val balances = groupBalances(
            memberIds = members,
            paidByPerson = expenses.groupBy { it.paidByPersonId }.mapValues { (_, e) -> e.sumOf { it.amountMinor } },
            sharesByPerson = shares.groupBy { it.personId }.mapValues { (_, s) -> s.sumOf { it.shareMinor } }
        )

        assertEquals("the group's positions must still cancel out", 0L, balances.sumOf { it.amountMinor })
    }

    /** And no third party may be moved by it: only the payer absorbs what cannot be collected. */
    @Test
    fun deletingAMemberDoesNotChangeWhatAnyoneElseOwes() = runBlocking {
        val asha = person("Asha")
        val bilal = person("Bilal")
        val groupId = repo.createGroup("Goa", "INR", listOf(asha, bilal))
        val me = repo.self().id
        repo.addExpense(groupId, "Hotel", 300_00, asha, listOf(me, asha, bilal))

        val mineBefore = db.groupDao().getAllExpenseSharesForBackup().filter { it.personId == me }.sumOf { it.shareMinor }
        db.personDao().deletePersonWithHistory(db.personDao().getPersonNow(bilal)!!)
        val mineAfter = db.groupDao().getAllExpenseSharesForBackup().filter { it.personId == me }.sumOf { it.shareMinor }

        assertEquals("my share must not move because somebody else was deleted", mineBefore, mineAfter)
        assertEquals(
            "the payer absorbs the share that can no longer be collected",
            200_00L,
            db.groupDao().getAllExpenseSharesForBackup().filter { it.personId == asha }.sumOf { it.shareMinor }
        )
    }

    /** Deleting the person who paid takes the expense with it, so nothing needs reassigning. */
    @Test
    fun deletingThePayerRemovesTheExpenseEntirely() = runBlocking {
        val asha = person("Asha")
        val bilal = person("Bilal")
        val groupId = repo.createGroup("Goa", "INR", listOf(asha, bilal))
        val me = repo.self().id
        repo.addExpense(groupId, "Hotel", 300_00, asha, listOf(me, asha, bilal))

        db.personDao().deletePersonWithHistory(db.personDao().getPersonNow(asha)!!)

        assertEquals(0, db.groupDao().getAllExpensesForBackup().size)
        assertEquals(0, db.groupDao().getAllExpenseSharesForBackup().size)
    }
}
