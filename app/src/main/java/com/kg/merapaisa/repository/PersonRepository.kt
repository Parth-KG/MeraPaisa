package com.kg.merapaisa.repository

import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonDao
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The only thing that touches the ledger. Every write goes through here and notifies the
 * widget on the way out, so refreshing it is no longer the UI's job to remember.
 */
class PersonRepository(
    private val dao: PersonDao,
    private val notifier: LedgerChangeNotifier = LedgerChangeNotifier.None
) {

    fun personsWithBalances(): Flow<List<PersonWithBalance>> = dao.getPersonsWithBalances()

    fun transactions(personId: Long): Flow<List<Transaction>> = dao.getTransactionsForPerson(personId)

    fun transactionCount(personId: Long): Flow<Int> = dao.getTransactionCount(personId)

    suspend fun addPerson(person: Person): Long = dao.insertPerson(person).also { notifier.onLedgerChanged() }

    suspend fun updatePerson(person: Person) {
        dao.updatePerson(person)
        notifier.onLedgerChanged()
    }

    suspend fun deletePerson(person: Person) {
        dao.deletePersonWithHistory(person)
        notifier.onLedgerChanged()
    }

    suspend fun recordAmount(personId: Long, amountMinor: Long, note: String = "") {
        dao.recordEntry(Transaction(personId = personId, amountMinor = amountMinor, note = note))
        notifier.onLedgerChanged()
    }

    suspend fun recordEntries(entries: List<Transaction>) {
        if (entries.isEmpty()) return
        dao.recordEntries(entries)
        notifier.onLedgerChanged()
    }

    suspend fun settle(personId: Long) {
        dao.settle(personId)
        notifier.onLedgerChanged()
    }

    suspend fun reopen(personId: Long) {
        dao.reopen(personId)
        notifier.onLedgerChanged()
    }

    suspend fun rollbackTo(personId: Long, since: Long) {
        dao.rollbackTo(personId, since)
        notifier.onLedgerChanged()
    }

    suspend fun clearTransactions(personId: Long) {
        dao.clearTransactionsForPerson(personId)
        notifier.onLedgerChanged()
    }
}
