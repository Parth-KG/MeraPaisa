package com.kg.merapaisa.repository

import com.kg.merapaisa.data.AppliedPayload
import com.kg.merapaisa.data.ImportOutcome
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonDao
import com.kg.merapaisa.data.PersonLedger
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.SharePayload
import com.kg.merapaisa.data.SharedEntry
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.data.mirrored
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * The only thing that touches the ledger. Every write goes through here and notifies the
 * widget on the way out, so refreshing it is no longer the UI's job to remember.
 */
class PersonRepository(
    private val dao: PersonDao,
    private val notifier: LedgerChangeNotifier = LedgerChangeNotifier.None
) {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun personsWithBalances(): Flow<List<PersonWithBalance>> =
        flow { emit(dao.ensureSelf().id) }.flatMapLatest { dao.getPersonsWithBalances(it) }

    fun transactions(personId: Long): Flow<List<Transaction>> = dao.getTransactionsForPerson(personId)

    fun transactionCount(personId: Long): Flow<Int> = dao.getTransactionCount(personId)

    suspend fun transactionsNow(personId: Long): List<Transaction> =
        dao.getTransactionsForPersonNow(personId)

    /** Everyone and everything they have, read together, for an export. */
    suspend fun ledgerSnapshot(): List<PersonLedger> {
        val byPerson = dao.getAllTransactionsNow().groupBy { it.personId }
        return dao.getPersonsWithBalancesNow(dao.ensureSelf().id).map { person ->
            PersonLedger(person, byPerson[person.id].orEmpty())
        }
    }

    suspend fun nextSortOrder(): Int = dao.nextSortOrder()

    suspend fun addPerson(person: Person): Long = dao.insertPerson(person).also { notifier.onLedgerChanged() }

    /**
     * Converts a person's whole history to [toCurrency] at [rate], leaving no adjustment entry.
     *
     * Irreversible: the original amounts are gone. Until v2.2 this instead wrote a single
     * "Converted INR to USD" correction, which left a log where old entries read in one currency
     * and a mystery adjustment reconciled it to another.
     */
    suspend fun convertCurrency(personId: Long, toCurrency: String, rate: Double) {
        dao.convertPersonCurrency(personId, toCurrency, rate)
        notifier.onLedgerChanged()
    }

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

    suspend fun editTransaction(transaction: Transaction) {
        dao.editTransaction(transaction)
        notifier.onLedgerChanged()
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        dao.removeTransaction(transaction)
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

    // -----------------------------------------------------------------------------------------
    // Two-sided ledger over share links
    // -----------------------------------------------------------------------------------------

    /** The row that is you, created on first use. Its name is what a share link says it is from. */
    suspend fun self(): Person = dao.ensureSelf()

    /**
     * Renames the self row.
     *
     * This exists because the self row ships called "You", which is fine on your own screen and
     * useless on anyone else's — a link whose sender is "You" gives the recipient nothing to match
     * against. The share sheet captures a real name and saves it here, once.
     */
    suspend fun renameSelf(name: String) {
        val me = dao.ensureSelf()
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == me.name) return
        dao.updatePerson(me.copy(name = trimmed))
    }

    /**
     * Everyone a link could be filed against, for the import screen's picker.
     *
     * Balances come along because the picker needs them: choosing where ₹340 lands is a lot
     * easier next to what each person already stands at. Excludes the self row, which
     * `getPersonsWithBalancesNow` already filters out — you cannot owe yourself.
     */
    suspend fun personsForImport(): List<PersonWithBalance> =
        dao.getPersonsWithBalancesNow(dao.ensureSelf().id)

    /**
     * The entries the next link for this person would carry.
     *
     * [fullHistory] ignores the watermark, which is how someone recovers from a share that never
     * arrived — the alternative would be a debt neither ledger can reconcile and no way back.
     */
    suspend fun entriesToShare(personId: Long, fullHistory: Boolean): List<Transaction> {
        val since = if (fullHistory) 0L else dao.getPersonNow(personId)?.lastSharedAt ?: 0L
        return dao.getTransactionsSinceNow(personId, since)
    }

    /**
     * Builds the payload for [personId], newest entries only unless [fullHistory].
     *
     * The id is 16 hex characters rather than a full UUID: 64 bits is far more than enough to keep
     * two of this user's own links apart, and every character saved is a character of link that a
     * chat app cannot wrap or truncate.
     */
    suspend fun buildPayload(
        personId: Long,
        senderName: String,
        fullHistory: Boolean
    ): Pair<SharePayload, Long>? {
        val person = dao.getPersonNow(personId) ?: return null
        val entries = entriesToShare(personId, fullHistory)
        if (entries.isEmpty()) return null
        val payload = SharePayload(
            payloadId = UUID.randomUUID().toString().replace("-", "").take(16),
            senderName = senderName.trim().ifEmpty { "A friend" },
            currency = com.kg.merapaisa.data.normaliseCurrency(person.currency),
            entries = entries.map { SharedEntry(it.timestamp, it.amountMinor, it.note) }
        )
        // The watermark to commit once the link has actually gone out, not before.
        return payload to entries.maxOf { it.timestamp }
    }

    /**
     * Moves the watermark, so the next link carries only what comes after this one.
     *
     * Called when the share sheet is opened rather than when the message is confirmed sent —
     * Android does not tell us whether the user went through with it. Advancing optimistically can
     * therefore skip entries if they back out, which is exactly what `fullHistory` is the escape
     * hatch for. The other way round would double-send by default, and a debt counted twice is
     * worse than one that needs re-sending.
     */
    suspend fun markShared(personId: Long, upTo: Long) {
        dao.setLastSharedAt(personId, upTo)
    }

    /**
     * The record of this link having been applied before, if there is one.
     *
     * Read before the import screen offers a preview, not only when the user taps. Otherwise a
     * link that will do nothing still gets a full "this will record ..." screen, and the one thing
     * that screen exists to tell the truth about is what is going to happen.
     */
    suspend fun appliedPayload(payloadId: String): AppliedPayload? = dao.getAppliedPayload(payloadId)

    /**
     * Applies a decoded link to [personId], mirroring every sign on the way in.
     *
     * Timestamps are clamped to [now]. A payload is untrusted input and a crafted one can claim
     * the year 5000; left alone, such an entry would sit at the top of the history forever and
     * poison the watermark for every later share. Clamping only ever moves a timestamp backwards
     * to the present, so a genuine entry is untouched.
     */
    suspend fun importPayload(personId: Long, payload: SharePayload, now: Long): ImportOutcome {
        val entries = payload.mirrored().map {
            Transaction(
                personId = personId,
                amountMinor = it.amountMinor,
                timestamp = if (it.timestamp > now) now else it.timestamp,
                note = it.note
            )
        }
        return dao.applyPayload(personId, payload, entries, now)
            .also { if (it is ImportOutcome.Applied) notifier.onLedgerChanged() }
    }
}
