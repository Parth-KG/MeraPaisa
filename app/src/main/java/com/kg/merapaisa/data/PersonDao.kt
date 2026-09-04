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

    /** One-shot version of the same query, for a snapshot such as an export. */
    @Query(
        """
        SELECT persons.*, COALESCE(SUM(transactions.amountMinor), 0) AS balanceMinor
        FROM persons
        LEFT JOIN transactions ON transactions.personId = persons.id
        GROUP BY persons.id
        ORDER BY persons.sortOrder ASC
        """
    )
    suspend fun getPersonsWithBalancesNow(): List<PersonWithBalance>

    @Query("SELECT * FROM transactions ORDER BY personId ASC, timestamp ASC")
    suspend fun getAllTransactionsNow(): List<Transaction>

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

    @Query("UPDATE persons SET isSettled = :settled WHERE id = :personId")
    suspend fun setSettled(personId: Long, settled: Boolean)

    /**
     * Closes a debt out: records an entry for exactly what is outstanding and marks the person
     * settled. The balance is read inside the transaction so two fast taps cannot both act on
     * the same stale figure. A person already at zero can still be settled — that is how you
     * file someone away without inventing a transaction.
     */
    @androidx.room.Transaction
    suspend fun settle(personId: Long, note: String = "Settled") {
        val outstanding = getBalanceNow(personId)
        if (outstanding != 0L) {
            insertTransaction(Transaction(personId = personId, amountMinor = -outstanding, note = note))
        }
        setSettled(personId, true)
        moveToEndOfList(personId)
    }

    /**
     * Puts a settled person back in the active list. The closing entry stays in their history —
     * it happened — so reopening does not resurrect the old balance, it just unfiles them.
     */
    suspend fun reopen(personId: Long) = setSettled(personId, false)

    /**
     * Records one entry. Money moving again means the debt is live again, so this also lifts
     * the settled flag; otherwise the entry would land on someone hidden in the Settled tab.
     */
    @androidx.room.Transaction
    suspend fun recordEntry(transaction: Transaction) {
        insertTransaction(transaction)
        if (transaction.amountMinor != 0L) setSettled(transaction.personId, false)
    }

    /** As [recordEntry], for a split that touches several people at once. */
    @androidx.room.Transaction
    suspend fun recordEntries(transactions: List<Transaction>) {
        insertTransactions(transactions)
        transactions.filter { it.amountMinor != 0L }
            .map { it.personId }
            .distinct()
            .forEach { setSettled(it, false) }
    }

    /** Reverses everything at or after [since] in one entry, computed under the transaction. */
    @androidx.room.Transaction
    suspend fun rollbackTo(personId: Long, since: Long, note: String = "Rollback") {
        val toReverse = sumTransactionsSince(personId, since)
        if (toReverse == 0L) return
        insertTransaction(Transaction(personId = personId, amountMinor = -toReverse, note = note))
        // The balance is non-zero again, so they are no longer settled.
        setSettled(personId, false)
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
