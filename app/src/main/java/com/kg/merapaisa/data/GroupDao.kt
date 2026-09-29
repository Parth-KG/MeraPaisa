package com.kg.merapaisa.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One member's share of a group, as stored. Summed into balances by [groupBalances]. */
data class PersonAmount(val personId: Long, val amountMinor: Long)

/** A group as the list screen needs it: who is in it, and where you stand. */
data class GroupSummary(
    @androidx.room.Embedded val group: Group,
    val memberCount: Int,
    /** Your own net position: what you fronted, less your share. Positive means you are owed. */
    val yourBalanceMinor: Long
)

@Dao
interface GroupDao {

    @Query("SELECT * FROM expense_groups WHERE archived = 0 ORDER BY createdAt DESC")
    fun getGroups(): Flow<List<Group>>

    /**
     * Every live group with your position in it, computed rather than stored: what you paid
     * into the group, less what you were assigned.
     */
    @Query(
        """
        SELECT expense_groups.*,
            (SELECT COUNT(*) FROM group_members WHERE group_members.groupId = expense_groups.id) AS memberCount,
            COALESCE((SELECT SUM(e.amountMinor) FROM expenses e
                      WHERE e.groupId = expense_groups.id AND e.paidByPersonId = :selfId), 0)
            - COALESCE((SELECT SUM(s.shareMinor) FROM expense_shares s
                        JOIN expenses e2 ON e2.id = s.expenseId
                        WHERE e2.groupId = expense_groups.id AND s.personId = :selfId), 0) AS yourBalanceMinor
        FROM expense_groups
        WHERE archived = 0
        ORDER BY createdAt DESC
        """
    )
    fun getGroupSummaries(selfId: Long): Flow<List<GroupSummary>>

    @Query("SELECT * FROM expense_groups WHERE id = :groupId")
    fun getGroup(groupId: Long): Flow<Group?>

    /**
     * Flips how this group's settle-up plan is worked out.
     *
     * Writes one column and nothing else. Switching between simplified and unsimplified changes
     * what the plan *says*, not what happened, so it leaves no trace in the expense list, and must
     * not, or the log would fill with entries recording that somebody changed their mind about a
     * view.
     */
    @Query("UPDATE expense_groups SET simplifyDebts = :simplify WHERE id = :groupId")
    suspend fun setSimplifyDebts(groupId: Long, simplify: Boolean)

    @Insert
    suspend fun insertGroup(group: Group): Long

    @Update
    suspend fun updateGroup(group: Group)

    @Query("DELETE FROM expense_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMembers(members: List<GroupMember>)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND personId = :personId")
    suspend fun removeMember(groupId: Long, personId: Long)

    @Query(
        """
        SELECT persons.* FROM persons
        JOIN group_members ON group_members.personId = persons.id
        WHERE group_members.groupId = :groupId
        ORDER BY persons.isSelf DESC, persons.name COLLATE NOCASE ASC
        """
    )
    fun getMembers(groupId: Long): Flow<List<Person>>

    @Query("SELECT personId FROM group_members WHERE groupId = :groupId")
    suspend fun getMemberIds(groupId: Long): List<Long>

    /** Every share in the group, so balances can be derived without a query per expense. */
    @Query(
        """
        SELECT expense_shares.* FROM expense_shares
        JOIN expenses ON expenses.id = expense_shares.expenseId
        WHERE expenses.groupId = :groupId
        """
    )
    fun getSharesForGroup(groupId: Long): Flow<List<ExpenseShare>>

    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY timestamp DESC")
    fun getExpenses(groupId: Long): Flow<List<Expense>>

    @Insert
    suspend fun insertExpense(expense: Expense): Long

    @Insert
    suspend fun insertShares(shares: List<ExpenseShare>)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: Long)

    /**
     * How many group expenses this person fronted. Deleting them cascades those expenses away,
     * which moves every other member's position, so the confirmation has to say so.
     */
    @Query("SELECT COUNT(*) FROM expenses WHERE paidByPersonId = :personId")
    fun expenseCountPaidBy(personId: Long): Flow<Int>

    /**
     * Expenses this person has a share of but did not pay for.
     *
     * Deleting them hands those shares to whoever fronted the money (see
     * [PersonDao.reassignGroupSharesToPayer]), so the payer ends up absorbing what can no longer be
     * collected. That changes what somebody else is owed, which belongs in the sentence asking for
     * confirmation just as much as the expenses being removed outright do.
     */
    @Query(
        """
        SELECT COUNT(*) FROM expense_shares
        JOIN expenses ON expenses.id = expense_shares.expenseId
        WHERE expense_shares.personId = :personId
          AND expenses.paidByPersonId != :personId
          AND expense_shares.shareMinor != 0
        """
    )
    fun expenseCountSharedBy(personId: Long): Flow<Int>

    /** What each member has fronted for the group. */
    @Query(
        """
        SELECT paidByPersonId AS personId, COALESCE(SUM(amountMinor), 0) AS amountMinor
        FROM expenses WHERE groupId = :groupId GROUP BY paidByPersonId
        """
    )
    suspend fun paidByMember(groupId: Long): List<PersonAmount>

    /** What each member has been assigned to cover. */
    @Query(
        """
        SELECT expense_shares.personId AS personId, COALESCE(SUM(expense_shares.shareMinor), 0) AS amountMinor
        FROM expense_shares
        JOIN expenses ON expenses.id = expense_shares.expenseId
        WHERE expenses.groupId = :groupId
        GROUP BY expense_shares.personId
        """
    )
    suspend fun owedByMember(groupId: Long): List<PersonAmount>

    /**
     * Records an expense and its shares together. A half-written expense would stop the
     * group's balances netting to zero, and settle-up would produce nonsense.
     */
    @androidx.room.Transaction
    suspend fun recordExpense(expense: Expense, sharesByPerson: Map<Long, Long>) {
        val id = insertExpense(expense)
        insertShares(sharesByPerson.map { (personId, share) ->
            ExpenseShare(expenseId = id, personId = personId, shareMinor = share)
        })
    }

    // -----------------------------------------------------------------------------------------
    // Backup and restore
    //
    // Raw reads and writes with ids intact. Every other query here is scoped to one group; a
    // backup needs all of them at once, and needs them to still join up after being written back.
    // -----------------------------------------------------------------------------------------

    @Query("SELECT * FROM expense_groups ORDER BY id ASC")
    suspend fun getAllGroupsForBackup(): List<Group>

    @Query("SELECT * FROM group_members ORDER BY groupId ASC, personId ASC")
    suspend fun getAllGroupMembersForBackup(): List<GroupMember>

    @Query("SELECT * FROM expenses ORDER BY id ASC")
    suspend fun getAllExpensesForBackup(): List<Expense>

    @Query("SELECT * FROM expense_shares ORDER BY expenseId ASC, personId ASC")
    suspend fun getAllExpenseSharesForBackup(): List<ExpenseShare>

    @Insert
    suspend fun insertGroups(groups: List<Group>)

    @Insert
    suspend fun insertExpenses(expenses: List<Expense>)

    /**
     * Deleting the groups is enough: members, expenses and shares all cascade from
     * `expense_groups`, which is exactly the behaviour MigrationTest pins down.
     */
    @Query("DELETE FROM expense_groups")
    suspend fun deleteAllGroups()
}
