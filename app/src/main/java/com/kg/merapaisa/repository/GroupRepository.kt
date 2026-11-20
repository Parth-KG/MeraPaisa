package com.kg.merapaisa.repository

import com.kg.merapaisa.data.Group
import com.kg.merapaisa.data.GroupDao
import com.kg.merapaisa.data.GroupMember
import com.kg.merapaisa.data.GroupSummary
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonDao
import com.kg.merapaisa.data.Expense
import com.kg.merapaisa.data.MemberBalance
import com.kg.merapaisa.data.MoveLine
import com.kg.merapaisa.data.MoveReceipt
import com.kg.merapaisa.data.evenShares
import com.kg.merapaisa.data.Transfer
import com.kg.merapaisa.data.settlePlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Groups, and the person that is you. Every group includes you as a member. A trip you are
 * not part of is somebody else's ledger.
 */
class GroupRepository(
    private val groupDao: GroupDao,
    private val personDao: PersonDao,
    private val notifier: LedgerChangeNotifier = LedgerChangeNotifier.None
) {

    suspend fun self(): Person = personDao.ensureSelf()

    /** True once any group exists, archived or not. */
    suspend fun hasAnyGroup(): Boolean = groupDao.countGroups() > 0

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun groupSummaries(): Flow<List<GroupSummary>> =
        selfIds().flatMapLatest { groupDao.getGroupSummaries(it) }

    /** The archived groups, with your position in each, for the Groups tab's Archived section. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun archivedGroupSummaries(): Flow<List<GroupSummary>> =
        selfIds().flatMapLatest { groupDao.getArchivedGroupSummaries(it) }

    /**
     * Archives a group, or brings it back (v3.7.0). An archived group leaves the main list for a
     * section of its own and stays openable and editable; its rows and balances are untouched.
     */
    suspend fun setArchived(groupId: Long, archived: Boolean) {
        groupDao.setArchived(groupId, archived)
        notifier.onLedgerChanged()
    }

    /** Your own row's id, created if missing, and followed across a restore. */
    fun selfIds(): Flow<Long> =
        flow { personDao.ensureSelf(); emitAll(personDao.selfIdFlow().filterNotNull().distinctUntilChanged()) }

    fun members(groupId: Long): Flow<List<Person>> = groupDao.getMembers(groupId)

    /** Group expenses this person fronted, which deleting them would take with it. */
    fun expensesPaidBy(personId: Long): Flow<Int> = groupDao.expenseCountPaidBy(personId)

    /** Group expenses this person only shared. Deleting them passes those shares to the payer. */
    fun expensesSharedBy(personId: Long): Flow<Int> = groupDao.expenseCountSharedBy(personId)

    /**
     * Renames a group, adds people to it and takes out people nothing in it involves. Adding
     * someone moves no balance: they owe nothing for expenses recorded before they joined, because
     * their share of those does not exist. Removing is refused, person by person, for anyone who
     * paid or holds a share of anything in the group, and never touches you (v3.7.0).
     */
    suspend fun editGroup(
        groupId: Long,
        name: String,
        newMemberIds: List<Long>,
        removedMemberIds: List<Long> = emptyList()
    ) {
        val group = groupDao.getGroup(groupId).first() ?: return
        if (name.isNotBlank() && name != group.name) groupDao.updateGroup(group.copy(name = name))
        if (newMemberIds.isNotEmpty()) {
            groupDao.addMembers(newMemberIds.distinct().map { GroupMember(groupId = groupId, personId = it) })
        }
        val selfId = self().id
        removedMemberIds.distinct().filter { it != selfId }.forEach { groupDao.removeMemberIfUninvolved(groupId, it) }
        notifier.onLedgerChanged()
    }

    /** Creates a group with you and the chosen people in it. */
    suspend fun createGroup(
        name: String,
        currency: String,
        memberIds: List<Long>,
        simplifyDebts: Boolean = true
    ): Long {
        val selfId = self().id
        val groupId = groupDao.insertGroup(
            Group(name = name, currency = currency, simplifyDebts = simplifyDebts)
        )
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
        groupDao.getSharesForGroup(groupId),
        groupDao.getGroup(groupId)
    ) { members, expenses, shares, group ->
        // Computed here rather than in the screen, so the plan and the balances it squares are
        // always derived from the same read of the same rows.
        val plan = settlePlan(members.map { it.id }, expenses, shares, simplify = group?.simplifyDebts != false)
        GroupDetail(
            members = members,
            expenses = expenses,
            shares = shares,
            balances = plan.balances,
            transfers = plan.transfers,
            simplifyDebts = group?.simplifyDebts != false
        )
    }

    /** Flips the plan between fewest-payments and as-it-happened. Writes nothing to the log. */
    suspend fun setSimplifyDebts(groupId: Long, simplify: Boolean) {
        groupDao.setSimplifyDebts(groupId, simplify)
    }

    suspend fun addExpense(
        groupId: Long,
        description: String,
        amountMinor: Long,
        paidByPersonId: Long,
        sharesByPerson: Map<Long, Long>
    ) {
        // Shares that miss the amount would stop the group netting to zero. The sheets never send
        // them, and a write refused here is better than a crash or a group that no longer adds up.
        if (sharesByPerson.values.sum() != amountMinor || sharesByPerson.isEmpty()) return
        groupDao.recordExpense(
            expense = Expense(
                groupId = groupId,
                description = description,
                amountMinor = amountMinor,
                paidByPersonId = paidByPersonId
            ),
            sharesByPerson = sharesByPerson
        )
        notifier.onLedgerChanged()
    }

    /** An expense split equally between [sharedWith], the remainder to the earliest. */
    suspend fun addExpense(
        groupId: Long,
        description: String,
        amountMinor: Long,
        paidByPersonId: Long,
        sharedWith: List<Long>
    ) = addExpense(
        groupId, description, amountMinor, paidByPersonId,
        evenShares(amountMinor, sharedWith, com.kg.merapaisa.data.minorStep(groupDao.currencyNow(groupId) ?: "INR"))
    )

    /**
     * Rewrites an expense: what for, the amount, who paid and the shares (v3.7.0). Balances and
     * the plan follow by themselves, since both are derived from the rows. Payments are not
     * edited; they are deleted and recorded again.
     */
    suspend fun editExpense(
        expenseId: Long,
        description: String,
        amountMinor: Long,
        paidByPersonId: Long,
        sharesByPerson: Map<Long, Long>
    ) {
        // Shares that miss the amount would stop the group netting to zero. The sheets never send
        // them, and a write refused here is better than a crash or a group that no longer adds up.
        if (sharesByPerson.values.sum() != amountMinor || sharesByPerson.isEmpty()) return
        if (groupDao.updateExpense(expenseId, description, amountMinor, paidByPersonId, sharesByPerson)) {
            notifier.onLedgerChanged()
        }
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
                paidByPersonId = fromPersonId,
                // Flagged, so the group screen can show repayments apart from spending. Until v2.4
                // the two were told apart only by this description.
                isSettlement = true
            ),
            sharesByPerson = mapOf(toPersonId to amountMinor)
        )
        notifier.onLedgerChanged()
    }

    /**
     * Moves your part of a group to your ledger (v3.7.0): one cancelling payment per pair in the
     * group, one entry per person in the ledger noted "From <group>". Null when the group changed
     * since [lines] were worked out, in which case nothing was written.
     */
    suspend fun moveToLedger(groupId: Long, groupName: String, lines: List<MoveLine>): MoveReceipt? {
        val receipt = groupDao.moveToLedger(groupId, self().id, lines, "From ${groupName.trim()}", System.currentTimeMillis())
        if (receipt != null) notifier.onLedgerChanged()
        return receipt
    }

    /** Undo for a move: its payment rows and its ledger entries go together. */
    suspend fun undoMove(receipt: MoveReceipt) {
        groupDao.undoMove(receipt)
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
    /** Every share of every expense in the group, for editing one and for the pair amounts. */
    val shares: List<com.kg.merapaisa.data.ExpenseShare> = emptyList(),
    val balances: List<com.kg.merapaisa.data.MemberBalance>,
    /**
     * Who pays whom, and how much, computed by whichever method this group is set to.
     *
     * Carried here rather than worked out in the screen so the plan and the balances it squares
     * come from one read of the same rows. A screen recomputing it separately could show a plan
     * that does not match the positions printed beside it.
     */
    val transfers: List<com.kg.merapaisa.data.Transfer> = emptyList(),
    /** Which method produced [transfers], so the screen can label the toggle honestly. */
    val simplifyDebts: Boolean = true
) {
    /** Spending, as opposed to people paying each other back. */
    val purchases: List<com.kg.merapaisa.data.Expense> get() = expenses.filterNot { it.isSettlement }

    /** Repayments. Same arithmetic as an expense, but not spending; see [Expense.isSettlement]. */
    val settlements: List<com.kg.merapaisa.data.Expense> get() = expenses.filter { it.isSettlement }
}
