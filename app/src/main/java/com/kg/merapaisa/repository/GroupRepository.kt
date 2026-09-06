package com.kg.merapaisa.repository

import com.kg.merapaisa.data.Group
import com.kg.merapaisa.data.GroupDao
import com.kg.merapaisa.data.GroupMember
import com.kg.merapaisa.data.GroupSummary
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonDao
import com.kg.merapaisa.data.Expense
import com.kg.merapaisa.data.ExpenseShare
import com.kg.merapaisa.data.MemberBalance
import com.kg.merapaisa.data.evenShares
import com.kg.merapaisa.data.groupBalances
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Groups, and the person that is you. Every group includes you as a member — a trip you are
 * not part of is somebody else's ledger.
 */
class GroupRepository(
    private val groupDao: GroupDao,
    private val personDao: PersonDao,
    private val notifier: LedgerChangeNotifier = LedgerChangeNotifier.None
) {

    suspend fun self(): Person = personDao.ensureSelf()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun groupSummaries(): Flow<List<GroupSummary>> =
        flow { emit(self().id) }.flatMapLatest { groupDao.getGroupSummaries(it) }

    fun members(groupId: Long): Flow<List<Person>> = groupDao.getMembers(groupId)

    /** Creates a group with you and the chosen people in it. */
    suspend fun createGroup(name: String, currency: String, memberIds: List<Long>): Long {
        val selfId = self().id
        val groupId = groupDao.insertGroup(Group(name = name, currency = currency))
        val everyone = (memberIds + selfId).distinct()
        groupDao.addMembers(everyone.map { GroupMember(groupId = groupId, personId = it) })
        notifier.onLedgerChanged()
        return groupId
    }

    /**
     * Members, expenses and the balances that follow from them. Balances are recomputed from
     * the rows on every change rather than stored, so they cannot drift from the expenses.
     */
    fun groupDetail(groupId: Long): Flow<GroupDetail> = combine(
        groupDao.getMembers(groupId),
        groupDao.getExpenses(groupId),
        groupDao.getSharesForGroup(groupId)
    ) { members, expenses, shares ->
        GroupDetail(
            members = members,
            expenses = expenses,
            balances = groupBalances(
                memberIds = members.map { it.id },
                paidByPerson = expenses.groupBy { it.paidByPersonId }
                    .mapValues { (_, e) -> e.sumOf { it.amountMinor } },
                sharesByPerson = shares.groupBy { it.personId }
                    .mapValues { (_, s) -> s.sumOf { it.shareMinor } }
            )
        )
    }

    suspend fun addExpense(
        groupId: Long,
        description: String,
        amountMinor: Long,
        paidByPersonId: Long,
        sharedWith: List<Long>
    ) {
        groupDao.recordExpense(
            expense = Expense(
                groupId = groupId,
                description = description,
                amountMinor = amountMinor,
                paidByPersonId = paidByPersonId
            ),
            sharesByPerson = evenShares(amountMinor, sharedWith)
        )
        notifier.onLedgerChanged()
    }

    /**
     * Records one settle-up payment. A payment is just an expense the payer covered on the
     * payee's behalf, so it runs through the same machinery and shows up in the history
     * instead of silently adjusting a number.
     */
    suspend fun recordTransfer(groupId: Long, fromPersonId: Long, toPersonId: Long, amountMinor: Long) {
        groupDao.recordExpense(
            expense = Expense(
                groupId = groupId,
                description = "Settlement",
                amountMinor = amountMinor,
                paidByPersonId = fromPersonId
            ),
            sharesByPerson = mapOf(toPersonId to amountMinor)
        )
        notifier.onLedgerChanged()
    }

    suspend fun deleteExpense(expenseId: Long) {
        groupDao.deleteExpense(expenseId)
        notifier.onLedgerChanged()
    }

    suspend fun deleteGroup(groupId: Long) {
        groupDao.deleteGroup(groupId)
        notifier.onLedgerChanged()
    }
}

/** A group with everything the detail screen needs, balances included. */
data class GroupDetail(
    val members: List<com.kg.merapaisa.data.Person>,
    val expenses: List<com.kg.merapaisa.data.Expense>,
    val balances: List<com.kg.merapaisa.data.MemberBalance>
)
