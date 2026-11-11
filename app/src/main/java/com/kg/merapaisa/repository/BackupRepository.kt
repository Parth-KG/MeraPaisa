package com.kg.merapaisa.repository

import androidx.room.withTransaction
import com.kg.merapaisa.data.AppDatabase
import com.kg.merapaisa.data.BackupSnapshot
import com.kg.merapaisa.data.GroupDao
import com.kg.merapaisa.data.ImportedPerson
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonDao
import com.kg.merapaisa.data.RestoreMode
import com.kg.merapaisa.data.RestorePlan
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.data.base64UrlDecode
import com.kg.merapaisa.data.encodeBackup
import com.kg.merapaisa.data.planRestore

/**
 * Reading the whole ledger out, and putting one back.
 *
 * Restores run inside a single database transaction spanning both DAOs. Room's `@Transaction` only
 * covers one DAO, and a restore touches every table, so a failure partway through without this
 * would leave people with no transactions, or expenses with no shares, which is a worse state than
 * the one the user was trying to recover from.
 */
class BackupRepository(
    private val db: AppDatabase,
    private val personDao: PersonDao,
    private val groupDao: GroupDao,
    private val notifier: LedgerChangeNotifier = LedgerChangeNotifier.None,
    private val photos: PhotoStore = PhotoStore.None
) {

    /**
     * Every table, read together inside one transaction so they cannot disagree, then the photo
     * files of the people who have one, read after it so no file is read while the database is
     * held.
     */
    suspend fun snapshot(): BackupSnapshot {
        val tables = readTables()
        return tables.copy(photos = photosOf(tables.persons, photos::read))
    }

    private suspend fun readTables(): BackupSnapshot = db.withTransaction {
        BackupSnapshot(
            persons = personDao.getAllPersonsForBackup(),
            transactions = personDao.getAllTransactionsNow(),
            groups = groupDao.getAllGroupsForBackup(),
            groupMembers = groupDao.getAllGroupMembersForBackup(),
            expenses = groupDao.getAllExpensesForBackup(),
            expenseShares = groupDao.getAllExpenseSharesForBackup(),
            appliedPayloads = personDao.getAllAppliedPayloadsForBackup(),
            retiredUids = personDao.getAllRetiredUidsForBackup()
        )
    }

    suspend fun exportJson(exportedAt: Long, appVersion: String): String =
        encodeBackup(snapshot(), exportedAt, appVersion)

    /** What a restore would do, without doing any of it. */
    suspend fun plan(incoming: BackupSnapshot, mode: RestoreMode): RestorePlan {
        val selfId = personDao.ensureSelf().id
        return planRestore(snapshot(), incoming, mode, selfId)
    }

    /**
     * Applies a plan.
     *
     * Deletes run first for Replace, in an order the foreign keys allow; inserts then run parents
     * before children for the same reason. The plan already resolved every id, so nothing here
     * decides anything. That is what makes the decisions testable without a device.
     */
    suspend fun apply(plan: RestorePlan) {
        // Photo files first, outside the transaction, so no file is written while the database is
        // held. If the transaction then fails, the files it would have pointed at go with it.
        val saved = HashMap<Long, String>()
        plan.photos.forEach { (personId, photo) ->
            val bytes = base64UrlDecode(photo) ?: return@forEach
            photos.save(bytes)?.let { saved[personId] = it }
        }
        val inserted = plan.persons.map { person ->
            saved[person.id]?.let { person.copy(pfpType = "photo", pfpValue = it) } ?: person
        }
        val insertedIds = plan.persons.map { it.id }.toSet()

        try {
            db.withTransaction { applyTables(plan, inserted, saved.filterKeys { it !in insertedIds }) }
        } catch (e: Throwable) {
            saved.values.forEach(photos::delete)
            throw e
        }
        notifier.onLedgerChanged()
    }

    /** The inserts, in an order the foreign keys allow. Runs inside [apply]'s transaction. */
    private suspend fun applyTables(
        plan: RestorePlan,
        persons: List<Person>,
        photosForPeopleHere: Map<Long, String>
    ) {
        if (plan.mode == RestoreMode.Replace) {
            groupDao.deleteAllGroups()
            personDao.deleteAllPersons()
            personDao.deleteAllAppliedPayloads()
        }

        if (persons.isNotEmpty()) personDao.insertPersons(persons)
        photosForPeopleHere.forEach { (personId, path) -> personDao.setPhoto(personId, path) }
        // A backup with no self row of its own still needs one, and a Replace just deleted the
        // local one. Runs after the inserts so it adopts the backup's row when there was one.
        personDao.ensureSelf()

        if (plan.transactions.isNotEmpty()) personDao.insertTransactions(plan.transactions)
        if (plan.groups.isNotEmpty()) groupDao.insertGroups(plan.groups)
        if (plan.groupMembers.isNotEmpty()) groupDao.addMembers(plan.groupMembers)
        if (plan.expenses.isNotEmpty()) groupDao.insertExpenses(plan.expenses)
        if (plan.expenseShares.isNotEmpty()) groupDao.insertShares(plan.expenseShares)
        if (plan.appliedPayloads.isNotEmpty()) personDao.insertAppliedPayloads(plan.appliedPayloads)
        if (plan.retiredUids.isNotEmpty()) personDao.insertRetiredUids(plan.retiredUids)
    }
}

/**
 * Turns a parsed CSV into a snapshot, so it can go through exactly the same planner as a JSON
 * backup rather than growing a second, less-tested restore path.
 *
 * The ids here are synthetic and local to this snapshot (a CSV has none). The planner only uses
 * them to join its own rows together before allocating real ones.
 *
 * **There are no groups in it, and that is not an oversight.** A CSV carries no group data at all,
 * so a Merge leaves existing groups alone, while a Replace would delete them and restore none. The
 * preview has to say that out loud before anyone taps it.
 */
fun List<ImportedPerson>.toSnapshot(): BackupSnapshot {
    val persons = mutableListOf<Person>()
    val transactions = mutableListOf<Transaction>()
    var nextTransactionId = 1

    forEachIndexed { index, imported ->
        val personId = (index + 1).toLong()
        persons.add(
            Person(
                id = personId,
                name = imported.name,
                pfpValue = com.kg.merapaisa.data.initialsOf(imported.name),
                sortOrder = index,
                isSettled = imported.isSettled,
                currency = imported.currency
            )
        )
        imported.transactions.forEach { t ->
            transactions.add(
                Transaction(
                    id = nextTransactionId++,
                    personId = personId,
                    amountMinor = t.amountMinor,
                    timestamp = t.timestamp,
                    note = t.note
                )
            )
        }
    }

    return BackupSnapshot(
        persons = persons,
        transactions = transactions,
        groups = emptyList(),
        groupMembers = emptyList(),
        expenses = emptyList(),
        expenseShares = emptyList(),
        appliedPayloads = emptyList()
    )
}
