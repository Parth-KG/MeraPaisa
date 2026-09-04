package com.kg.merapaisa.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM persons ORDER BY sortOrder ASC")
    fun getAllPersons(): Flow<List<Person>>

    /** Every person with their balance derived in one pass, for the list screen and widget. */
    @Query(
        """
        SELECT persons.*, COALESCE(SUM(transactions.amountMinor), 0) AS balanceMinor
        FROM persons
        LEFT JOIN transactions ON transactions.personId = persons.id
        GROUP BY persons.id
        ORDER BY persons.sortOrder ASC
        """
    )
    fun getPersonsWithBalances(): Flow<List<PersonWithBalance>>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE personId = :personId")
    fun getBalance(personId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE personId = :personId")
    suspend fun getBalanceNow(personId: Long): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person): Long

    @Update
    suspend fun updatePerson(person: Person)

    @Delete
    suspend fun deletePerson(person: Person)

    @Insert
    suspend fun insertTransaction(transaction: Transaction)

    /** Room runs a list insert as a single transaction, so a split lands whole or not at all. */
    @Insert
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Query("SELECT * FROM transactions WHERE personId = :personId ORDER BY timestamp DESC")
    fun getTransactionsForPerson(personId: Long): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions WHERE personId = :personId")
    fun getTransactionCount(personId: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE personId = :personId AND timestamp >= :since")
    suspend fun sumTransactionsSince(personId: Long, since: Long): Long

    @Query("DELETE FROM transactions WHERE personId = :personId")
    suspend fun deleteTransactionsForPerson(personId: Long)

    @Query("UPDATE persons SET sortOrder = :newOrder WHERE id = :personId")
    suspend fun updateSortOrder(personId: Long, newOrder: Int)

    @Query("UPDATE persons SET sortOrder = COALESCE((SELECT MAX(sortOrder) FROM persons), 0) + 1 WHERE id = :personId")
    suspend fun moveToEndOfList(personId: Long)

    /**
     * Records a closing entry for whatever is outstanding and moves the person to the end of
     * the list. Reads the balance inside the transaction so two fast taps cannot both act on
     * the same stale figure.
     */
    @androidx.room.Transaction
    suspend fun settle(personId: Long, note: String = "Settled") {
        val outstanding = getBalanceNow(personId)
        if (outstanding == 0L) return
        insertTransaction(Transaction(personId = personId, amountMinor = -outstanding, note = note))
        moveToEndOfList(personId)
    }

    /** Reverses everything at or after [since] in one entry, computed under the transaction. */
    @androidx.room.Transaction
    suspend fun rollbackTo(personId: Long, since: Long, note: String = "Rollback") {
        val toReverse = sumTransactionsSince(personId, since)
        if (toReverse == 0L) return
        insertTransaction(Transaction(personId = personId, amountMinor = -toReverse, note = note))
    }

    /**
     * Clears the log without changing what is owed. Because the balance is derived from these
     * rows, the outstanding amount is carried across as a single opening entry.
     */
    @androidx.room.Transaction
    suspend fun clearTransactionsForPerson(personId: Long) {
        val outstanding = getBalanceNow(personId)
        deleteTransactionsForPerson(personId)
        if (outstanding != 0L) {
            insertTransaction(
                Transaction(personId = personId, amountMinor = outstanding, note = "Opening balance")
            )
        }
    }

    @androidx.room.Transaction
    suspend fun deletePersonWithHistory(person: Person) {
        deleteTransactionsForPerson(person.id)
        deletePerson(person)
    }
}
