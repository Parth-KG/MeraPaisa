package com.kg.merapaisa.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM persons ORDER BY sortOrder ASC")
    fun getAllPersons(): Flow<List<Person>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person) : Long

    @Update
    suspend fun updatePerson(person: Person)

    @Delete
    suspend fun deletePerson(person: Person)

    @Query("UPDATE persons SET balance = :balance WHERE id = :id")
    suspend fun updateBalance(id: Long, balance: Double)

    @Insert
    suspend fun insertTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE personId = :personId ORDER BY timestamp DESC")
    fun getTransactionsForPerson(personId: Long): Flow<List<Transaction>>

    @Query("DELETE FROM transactions WHERE personId = :personId")
    suspend fun deleteTransactionsForPerson(personId: Long)

    @Query("DELETE FROM `transactions` WHERE personId = :personId")
    suspend fun clearTransactionsForPerson(personId: Long)

    @Query("UPDATE persons SET sortOrder = :newOrder WHERE id = :personId")
    suspend fun updateSortOrder(personId: Long, newOrder: Int)
}