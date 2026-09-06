package com.kg.merapaisa.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One member's share of a group, as stored. Summed into balances by [groupBalances]. */
data class PersonAmount(val personId: Long, val amountMinor: Long)

@Dao
interface GroupDao {

    @Query("SELECT * FROM expense_groups WHERE archived = 0 ORDER BY createdAt DESC")
    fun getGroups(): Flow<List<Group>>

    @Query("SELECT * FROM expense_groups WHERE id = :groupId")
    fun getGroup(groupId: Long): Flow<Group?>

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

    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY timestamp DESC")
    fun getExpenses(groupId: Long): Flow<List<Expense>>

    @Insert
    suspend fun insertExpense(expense: Expense): Long

    @Insert
    suspend fun insertShares(shares: List<ExpenseShare>)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: Long)

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
     * Records an expense and its shares together — a half-written expense would make the
     * group's balances stop netting to zero, and settle-up would produce nonsense.
     */
    @androidx.room.Transaction
    suspend fun recordExpense(expense: Expense, shares: (Long) -> List<ExpenseShare>) {
        val id = insertExpense(expense)
        insertShares(shares(id))
    }
}
