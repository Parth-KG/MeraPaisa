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

    /**
     * Every person with their balance derived in one pass, for the list screen and widget.
     *
     * A balance is their direct transactions plus your position with them inside groups:
     * their share of what you paid, less your share of what they paid. That slice of a group
     * is exactly the part that is between the two of you, so it belongs on the home screen —
     * and deriving it here means nothing is written twice or counted twice.
     */
    @Query(
        """
        SELECT persons.*,
            COALESCE(SUM(transactions.amountMinor), 0)
            + COALESCE((SELECT SUM(s.shareMinor) FROM expense_shares s
                        JOIN expenses e ON e.id = s.expenseId
                        WHERE s.personId = persons.id AND e.paidByPersonId = :selfId), 0)
            - COALESCE((SELECT SUM(s2.shareMinor) FROM expense_shares s2
                        JOIN expenses e2 ON e2.id = s2.expenseId
                        WHERE s2.personId = :selfId AND e2.paidByPersonId = persons.id), 0)
            AS balanceMinor
        FROM persons
        LEFT JOIN transactions ON transactions.personId = persons.id
        WHERE persons.isSelf = 0
        GROUP BY persons.id
        ORDER BY persons.sortOrder ASC
        """
    )
    fun getPersonsWithBalances(selfId: Long): Flow<List<PersonWithBalance>>

    /** One-shot version of the same query, for a snapshot such as an export. */
    @Query(
        """
        SELECT persons.*,
            COALESCE(SUM(transactions.amountMinor), 0)
            + COALESCE((SELECT SUM(s.shareMinor) FROM expense_shares s
                        JOIN expenses e ON e.id = s.expenseId
                        WHERE s.personId = persons.id AND e.paidByPersonId = :selfId), 0)
            - COALESCE((SELECT SUM(s2.shareMinor) FROM expense_shares s2
                        JOIN expenses e2 ON e2.id = s2.expenseId
                        WHERE s2.personId = :selfId AND e2.paidByPersonId = persons.id), 0)
            AS balanceMinor
        FROM persons
        LEFT JOIN transactions ON transactions.personId = persons.id
        WHERE persons.isSelf = 0
        GROUP BY persons.id
        ORDER BY persons.sortOrder ASC
        """
    )
    suspend fun getPersonsWithBalancesNow(selfId: Long): List<PersonWithBalance>

    @Query("SELECT * FROM transactions ORDER BY personId ASC, timestamp ASC")
    suspend fun getAllTransactionsNow(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE personId = :personId ORDER BY timestamp ASC")
    suspend fun getTransactionsForPersonNow(personId: Long): List<Transaction>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE personId = :personId")
    suspend fun getBalanceNow(personId: Long): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person): Long

    @Update
    suspend fun updatePerson(person: Person)

    /**
     * The bare row delete. **Call [deletePersonWithHistory] instead.**
     *
     * On its own this leaves a group inconsistent: `expense_shares` cascades on `personId`, so the
     * person's share of an expense somebody else paid for vanishes while the expense stays, and the
     * group's balances stop summing to zero. [deletePersonWithHistory] hands those shares to the
     * payer first. This is kept only because that method needs it.
     */
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

    /**
     * The next free position. Counting the existing rows collided after any deletion — delete
     * the middle of three people and the next person added would reuse an order already taken.
     */
    @Query("SELECT * FROM persons WHERE isSelf = 1 LIMIT 1")
    suspend fun getSelf(): Person?

    /**
     * Returns the row that represents you, creating it if absent. Migration 5 -> 6 makes one
     * for existing installs, but a fresh install builds the schema directly and never runs it.
     */
    @androidx.room.Transaction
    suspend fun ensureSelf(): Person {
        getSelf()?.let { return it }
        insertPerson(Person(name = "You", pfpValue = "You", sortOrder = -1, isSelf = true))
        return getSelf()!!
    }

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM persons WHERE isSelf = 0")
    suspend fun nextSortOrder(): Int

    @Query("UPDATE persons SET isSettled = :settled WHERE id = :personId")
    suspend fun setSettled(personId: Long, settled: Boolean)

    /**
     * Closes a debt out: records an entry for exactly what is outstanding and marks the person
     * settled. The balance is read inside the transaction so two fast taps cannot both act on
     * the same stale figure. A person already at zero can still be settled; that is how you
     * file someone away without inventing a transaction. There is no need to reorder them:
     * isSettled is what moves them out of the active list.
     *
     * Given [selfId], group activity is closed too. A person's balance includes their share of
     * group expenses between them and you, and settling only the direct entries left them in the
     * Settled tab still owing that share. Each group where the two of you still owe each other
     * gets a real payment for exactly that amount, as the group's own Settle up would record, so
     * the person, the group and your totals all end at zero together. A single direct entry for
     * the whole amount would not do: the group would still show the debt, and recording it there
     * later would count the same money twice.
     */
    @androidx.room.Transaction
    suspend fun settle(personId: Long, note: String = "Settled", selfId: Long? = null) {
        val at = System.currentTimeMillis()
        val outstanding = getBalanceNow(personId)
        if (outstanding != 0L) {
            insertTransaction(Transaction(personId = personId, amountMinor = -outstanding, timestamp = at, note = note))
        }
        if (selfId != null && selfId != personId) {
            groupPositionsWith(personId, selfId).filter { it.amountMinor != 0L }.forEach { position ->
                // Positive: they owe you in this group, so they pay you. Negative: you pay them.
                val theyPay = position.amountMinor > 0
                val amount = if (theyPay) position.amountMinor else -position.amountMinor
                val expenseId = insertExpenseRow(
                    Expense(
                        groupId = position.groupId,
                        description = "Settlement",
                        amountMinor = amount,
                        paidByPersonId = if (theyPay) personId else selfId,
                        timestamp = at,
                        isSettlement = true
                    )
                )
                insertShareRows(
                    listOf(ExpenseShare(expenseId, personId = if (theyPay) selfId else personId, shareMinor = amount))
                )
            }
        }
        setSettled(personId, true)
    }

    /**
     * What a person and you owe each other inside each group, by group: their share of what you
     * paid, less your share of what they paid. Positive means they owe you. The same terms the
     * balance query adds to a person's direct entries, split out per group.
     */
    @Query(
        """
        SELECT e.groupId AS groupId,
            COALESCE(SUM(CASE WHEN e.paidByPersonId = :selfId AND s.personId = :personId THEN s.shareMinor ELSE 0 END), 0)
            - COALESCE(SUM(CASE WHEN e.paidByPersonId = :personId AND s.personId = :selfId THEN s.shareMinor ELSE 0 END), 0)
            AS amountMinor
        FROM expenses e
        JOIN expense_shares s ON s.expenseId = e.id
        WHERE (e.paidByPersonId = :selfId AND s.personId = :personId)
           OR (e.paidByPersonId = :personId AND s.personId = :selfId)
        GROUP BY e.groupId
        """
    )
    suspend fun groupPositionsWith(personId: Long, selfId: Long): List<GroupPosition>

    @Insert
    suspend fun insertExpenseRow(expense: Expense): Long

    @Insert
    suspend fun insertShareRows(shares: List<ExpenseShare>)

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

    /**
     * Moves [amountMinor] of what [fromPersonId] owes onto [toPersonId].
     *
     * Two entries, equal and opposite, sharing one timestamp so they sort together and read as the
     * single event they are. One transaction, because half of this landing would invent or destroy
     * money: the whole point is that the total owed to you does not change, only who owes it.
     *
     * Each entry names the other person, so a year later the pair explains itself without needing
     * the other half in view.
     */
    @androidx.room.Transaction
    suspend fun moveDebt(
        fromPersonId: Long,
        toPersonId: Long,
        amountMinor: Long,
        fromName: String,
        toName: String,
        note: String = ""
    ) {
        val at = System.currentTimeMillis()
        val suffix = if (note.isBlank()) "" else ": $note"
        insertTransactions(
            listOf(
                Transaction(
                    personId = fromPersonId,
                    amountMinor = -amountMinor,
                    timestamp = at,
                    note = "Moved to $toName$suffix"
                ),
                Transaction(
                    personId = toPersonId,
                    amountMinor = amountMinor,
                    timestamp = at,
                    note = "Moved from $fromName$suffix"
                )
            )
        )
        // The receiver now owes something, so they belong back in the active list.
        setSettled(toPersonId, false)
    }

    /**
     * [moveDebt] between two people kept in different currencies.
     *
     * The sender goes down by [fromAmountMinor] in their currency and the receiver up by
     * [toAmountMinor] in theirs, converted by the caller at the day's rate. [conversion] says what
     * was converted to what, and goes on both entries, so either half explains the other a year
     * later. Same timestamp, one transaction: a failure writes neither.
     */
    @androidx.room.Transaction
    suspend fun moveDebtConverted(
        fromPersonId: Long,
        toPersonId: Long,
        fromAmountMinor: Long,
        toAmountMinor: Long,
        fromName: String,
        toName: String,
        conversion: String,
        note: String = ""
    ) {
        val at = System.currentTimeMillis()
        val suffix = if (note.isBlank()) "" else ": $note"
        insertTransactions(
            listOf(
                Transaction(
                    personId = fromPersonId,
                    amountMinor = -fromAmountMinor,
                    timestamp = at,
                    note = "Moved to $toName, $conversion$suffix"
                ),
                Transaction(
                    personId = toPersonId,
                    amountMinor = toAmountMinor,
                    timestamp = at,
                    note = "Moved from $fromName, $conversion$suffix"
                )
            )
        )
        setSettled(toPersonId, false)
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

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    /** Room runs a list update as one transaction, so a currency conversion lands whole. */
    @Update
    suspend fun updateTransactions(transactions: List<Transaction>)

    /**
     * Rewrites every one of a person's entries at [rate], and relabels them.
     *
     * The balance is derived from these rows, so converting them *is* converting the balance —
     * there is no separate figure to keep in step. `convertAll` guarantees the rewritten entries
     * still sum to the converted total, which is what stops the log contradicting the balance it
     * produces.
     *
     * One transaction: a failure halfway would leave a history half in each currency, with no
     * record of which entries were which.
     */
    @androidx.room.Transaction
    suspend fun convertPersonCurrency(personId: Long, toCurrency: String, rate: Double) {
        val entries = getTransactionsForPersonNow(personId)
        if (entries.isNotEmpty()) {
            val converted = convertAll(entries.map { it.amountMinor }, rate)
            updateTransactions(entries.mapIndexed { i, t -> t.copy(amountMinor = converted[i]) })
        }
        val person = getPersonNow(personId) ?: return
        updatePerson(person.copy(currency = toCurrency))
    }

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransaction(transactionId: Int)

    /**
     * Corrects a single entry in place, keeping its original timestamp. A correction can move
     * the balance off zero, so the settled flag is re-derived rather than left stale.
     */
    @androidx.room.Transaction
    suspend fun editTransaction(transaction: Transaction) {
        updateTransaction(transaction)
        if (getBalanceNow(transaction.personId) != 0L) setSettled(transaction.personId, false)
    }

    /** Removes one entry outright — a mistyped amount should not have to live in the history. */
    @androidx.room.Transaction
    suspend fun removeTransaction(transaction: Transaction) {
        deleteTransaction(transaction.id)
        if (getBalanceNow(transaction.personId) != 0L) setSettled(transaction.personId, false)
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
        reassignGroupSharesToPayer(person.id)
        deleteTransactionsForPerson(person.id)
        deletePerson(person)
    }

    /**
     * Every expense this person had a share of, that somebody else paid for.
     *
     * Their own expenses are excluded because those cascade away with them, taking their shares
     * with them — there is nothing left to be inconsistent about.
     */
    @Query(
        """
        SELECT expenses.id AS expenseId, expenses.paidByPersonId AS payerId, expense_shares.shareMinor AS shareMinor
        FROM expense_shares
        JOIN expenses ON expenses.id = expense_shares.expenseId
        WHERE expense_shares.personId = :personId AND expenses.paidByPersonId != :personId
        """
    )
    suspend fun orphanedSharesOf(personId: Long): List<OrphanedShare>

    @Query("SELECT shareMinor FROM expense_shares WHERE expenseId = :expenseId AND personId = :personId")
    suspend fun shareOf(expenseId: Long, personId: Long): Long?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun putShare(share: ExpenseShare)

    /**
     * Hands a departing member's share of other people's expenses to whoever fronted the money.
     *
     * `expense_shares` cascades on `personId`, so deleting someone used to silently remove their
     * share of an expense that stayed behind — leaving a ₹300 dinner with ₹200 of shares against
     * it. Every group balance is derived as *paid minus shared*, so the group stopped summing to
     * zero, and `settleUp` documents that sum as the reason it can square everyone. It produced a
     * plan that left somebody holding a figure nobody owed, and said nothing.
     *
     * Giving the share to the payer is the honest answer rather than a convenient one. The money
     * was really spent and can no longer be collected from someone who is no longer tracked, so
     * the person who put it up is the one out of pocket — which is what happens in life. It also
     * leaves every *other* member's position exactly where it was, so deleting one person cannot
     * quietly change what a third party owes.
     */
    @androidx.room.Transaction
    suspend fun reassignGroupSharesToPayer(personId: Long) {
        orphanedSharesOf(personId).forEach { orphan ->
            if (orphan.shareMinor == 0L) return@forEach
            val existing = shareOf(orphan.expenseId, orphan.payerId) ?: 0L
            putShare(
                ExpenseShare(
                    expenseId = orphan.expenseId,
                    personId = orphan.payerId,
                    shareMinor = existing + orphan.shareMinor
                )
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // Two-sided ledger over share links
    // -----------------------------------------------------------------------------------------

    /**
     * Everything for this person newer than the last share, which is what the next link carries.
     *
     * Strictly greater than, so the entry that ended the previous payload is not sent twice. The
     * watermark stores that entry's own timestamp rather than the moment of sharing, so an entry
     * back-dated between two shares is still picked up.
     */
    @Query(
        "SELECT * FROM transactions WHERE personId = :personId AND timestamp > :since " +
            "ORDER BY timestamp ASC"
    )
    suspend fun getTransactionsSinceNow(personId: Long, since: Long): List<Transaction>

    @Query("UPDATE persons SET lastSharedAt = :timestamp WHERE id = :personId")
    suspend fun setLastSharedAt(personId: Long, timestamp: Long)

    @Query("SELECT * FROM applied_payloads WHERE payloadId = :payloadId LIMIT 1")
    suspend fun getAppliedPayload(payloadId: String): AppliedPayload?

    @Insert
    suspend fun insertAppliedPayload(record: AppliedPayload)

    @Query("SELECT * FROM persons WHERE id = :personId LIMIT 1")
    suspend fun getPersonNow(personId: Long): Person?

    /**
     * Applies a decoded link to one person, or refuses and says why.
     *
     * The dedupe read and the writes share one transaction deliberately: without that, tapping a
     * forwarded link twice in quick succession could pass the "already applied?" check twice and
     * write the entries twice. The primary key on `applied_payloads` is the backstop if it ever
     * does, but the transaction is what makes the common case correct rather than lucky.
     *
     * [entries] arrives already mirrored and already bounded by the caller — this method does not
     * flip signs, so a caller that forgets to would write a debt pointing the wrong way. That is
     * why mirroring lives in one place, `SharePayload.mirrored()`, and is tested on its own.
     */
    @androidx.room.Transaction
    suspend fun applyPayload(
        personId: Long,
        payload: SharePayload,
        entries: List<Transaction>,
        now: Long
    ): ImportOutcome {
        getAppliedPayload(payload.payloadId)?.let { return ImportOutcome.AlreadyApplied(it.appliedAt) }

        val person = getPersonNow(personId)
        if (person != null && normaliseCurrency(person.currency) != payload.currency) {
            return ImportOutcome.CurrencyMismatch(payload.currency, normaliseCurrency(person.currency))
        }

        if (entries.isNotEmpty()) {
            insertTransactions(entries)
            // Money moved, so they are live again — same rule as recordEntries.
            if (entries.any { it.amountMinor != 0L }) setSettled(personId, false)
        }

        insertAppliedPayload(
            AppliedPayload(
                payloadId = payload.payloadId,
                appliedAt = now,
                personId = personId,
                senderName = payload.senderName,
                entryCount = entries.size,
                netMinor = entries.sumOf { it.amountMinor }
            )
        )

        return ImportOutcome.Applied(entries.size, entries.sumOf { it.amountMinor })
    }

    /**
     * Applies a reconciled link: the inserts, updates and deletions the user actually ticked.
     *
     * One transaction, for the same reason [applyPayload] is one: the dedupe read and the writes
     * have to be atomic or a link tapped twice in quick succession can pass the check twice. It
     * also means a plan that is half-applied cannot exist, which matters more here than it did
     * before — a reconcile that added three entries and then failed before removing one would
     * leave a ledger that matches neither phone.
     *
     * [write] is computed by `ReconcilePlan.writesFor` and arrives final: already mirrored, already
     * clamped, already filtered to what was chosen. Nothing is decided in here.
     */
    @androidx.room.Transaction
    suspend fun applyReconcile(
        personId: Long,
        payload: SharePayload,
        write: ReconcileWrite,
        now: Long
    ): ImportOutcome {
        getAppliedPayload(payload.payloadId)?.let { return ImportOutcome.AlreadyApplied(it.appliedAt) }

        val person = getPersonNow(personId)
        if (person != null && normaliseCurrency(person.currency) != payload.currency) {
            return ImportOutcome.CurrencyMismatch(payload.currency, normaliseCurrency(person.currency))
        }

        // The balance moves by more than the inserts: replacing an amount moves it by the
        // difference, and removing an entry moves it by the whole of what was there. Read before
        // anything is written, because afterwards the old rows are gone.
        var net = write.inserts.sumOf { it.amountMinor }
        if (write.updates.isNotEmpty() || write.deleteIds.isNotEmpty()) {
            val before = getTransactionsForPersonNow(personId).associateBy { it.id }
            write.updates.forEach { net += it.amountMinor - (before[it.id]?.amountMinor ?: 0L) }
            write.deleteIds.forEach { net -= before[it]?.amountMinor ?: 0L }
        }

        if (write.inserts.isNotEmpty()) insertTransactions(write.inserts)
        if (write.updates.isNotEmpty()) updateTransactions(write.updates)
        write.deleteIds.forEach { deleteTransaction(it) }

        // Money moved, so they are live again — same rule as recordEntries. Deletions count: a
        // removed entry changes the balance just as an added one does.
        if (net != 0L) setSettled(personId, false)

        // Recorded as applied only when it actually wrote something. A link the user looked at and
        // ticked nothing on has not been applied — it has been considered — and filing it here
        // would make re-opening it report "already applied" and offer no way to change their mind.
        if (!write.isEmpty) {
            insertAppliedPayload(
                AppliedPayload(
                    payloadId = payload.payloadId,
                    appliedAt = now,
                    personId = personId,
                    senderName = payload.senderName,
                    entryCount = write.inserts.size,
                    netMinor = net
                )
            )
        }

        return ImportOutcome.Reconciled(
            added = write.inserts.size,
            updated = write.updates.size,
            removed = write.deleteIds.size,
            netMinor = net
        )
    }

    // -----------------------------------------------------------------------------------------
    // Backup and restore
    //
    // These read and write raw rows with their ids intact, which nothing else in this DAO does.
    // A backup is only a backup if the tables still join up afterwards, so restoring has to put
    // rows back exactly where they were rather than letting autoGenerate invent new keys.
    // -----------------------------------------------------------------------------------------

    /** Every person including the self row, which the list queries deliberately hide. */
    @Query("SELECT * FROM persons ORDER BY id ASC")
    suspend fun getAllPersonsForBackup(): List<Person>

    @Query("SELECT * FROM applied_payloads ORDER BY appliedAt ASC")
    suspend fun getAllAppliedPayloadsForBackup(): List<AppliedPayload>

    @Insert
    suspend fun insertPersons(persons: List<Person>)

    @Insert
    suspend fun insertAppliedPayloads(records: List<AppliedPayload>)

    /**
     * Everyone, self included, for a Replace restore.
     *
     * The self row goes too, because the backup carries its own and keeping both would leave two
     * rows claiming to be you — which `ensureSelf` would then pick between arbitrarily. Callers
     * must run `ensureSelf()` afterwards so a backup without a self row still ends up with one.
     *
     * Cascades take transactions, group memberships, expenses and shares with it.
     */
    @Query("DELETE FROM persons")
    suspend fun deleteAllPersons()

    /** Self keeps no transactions of their own, but a restored backup may have given them some. */
    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM applied_payloads")
    suspend fun deleteAllAppliedPayloads()
}

/** What a person and you owe each other inside one group. Positive: they owe you. */
data class GroupPosition(val groupId: Long, val amountMinor: Long)
