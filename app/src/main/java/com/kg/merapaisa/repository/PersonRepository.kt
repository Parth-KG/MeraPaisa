package com.kg.merapaisa.repository

import com.kg.merapaisa.ui.format.amountString
import com.kg.merapaisa.data.AppliedPayload
import com.kg.merapaisa.data.ImportOutcome
import com.kg.merapaisa.data.MoveDebtResult
import com.kg.merapaisa.data.normaliseCurrency
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonDao
import com.kg.merapaisa.data.PersonLedger
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.ReconcilePlan
import com.kg.merapaisa.data.ReconcileWrite
import com.kg.merapaisa.data.ShareScope
import com.kg.merapaisa.data.SharePayload
import com.kg.merapaisa.data.SharedEntry
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.data.mirrored
import com.kg.merapaisa.data.reconcile
import com.kg.merapaisa.data.writesFor
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The only thing that touches the ledger. Every write goes through here and notifies the
 * widget on the way out, so refreshing it is no longer the UI's job to remember.
 */
class PersonRepository(
    private val dao: PersonDao,
    private val notifier: LedgerChangeNotifier = LedgerChangeNotifier.None
) {

    /** Everyone with their balance: direct entries only, since groups are kept apart. */
    fun personsWithBalances(): Flow<List<PersonWithBalance>> =
        flow { dao.ensureSelf(); emitAll(dao.getPersonsWithBalances()) }

    fun transactions(personId: Long): Flow<List<Transaction>> = dao.getTransactionsForPerson(personId)

    fun transactionCount(personId: Long): Flow<Int> = dao.getTransactionCount(personId)

    suspend fun transactionsNow(personId: Long): List<Transaction> =
        dao.getTransactionsForPersonNow(personId)

    /** Everyone and everything they have, read together, for an export. */
    suspend fun ledgerSnapshot(): List<PersonLedger> {
        val byPerson = dao.getAllTransactionsNow().groupBy { it.personId }
        return dao.getPersonsWithBalancesNow().map { person ->
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

    /**
     * Moves part of one person's balance onto another. "Rondu owes you ₹624; move ₹100 to Sasti."
     *
     * Two equal and opposite entries sharing a timestamp, written together, so the total owed to
     * you never changes, only who owes it. Useful when somebody pays on another's behalf, or when
     * a debt genuinely changes hands.
     *
     * Returns a [MoveDebtResult] rather than throwing, because every refusal here is something the
     * user needs told rather than an error: refusing is the normal outcome of a mistaken tap.
     */
    suspend fun moveDebt(
        fromPersonId: Long,
        toPersonId: Long,
        amountMinor: Long,
        note: String = ""
    ): MoveDebtResult {
        if (fromPersonId == toPersonId) return MoveDebtResult.SamePerson
        if (amountMinor <= 0L) return MoveDebtResult.NotAnAmount

        val from = dao.getPersonNow(fromPersonId) ?: return MoveDebtResult.PersonGone
        val to = dao.getPersonNow(toPersonId) ?: return MoveDebtResult.PersonGone

        // Same reasoning as an incoming share link in another currency: the two amounts are minor
        // units with no rate attached, so moving ₹100 onto a dollar balance would silently claim
        // $100. Converting would mean inventing a rate nobody agreed to.
        val fromCurrency = normaliseCurrency(from.currency)
        val toCurrency = normaliseCurrency(to.currency)
        if (fromCurrency != toCurrency) {
            return MoveDebtResult.CurrencyMismatch(fromCurrency, toCurrency)
        }

        val available = dao.getBalanceNow(fromPersonId)
        if (available <= 0L) return MoveDebtResult.NothingToMove(available)
        if (amountMinor > available) return MoveDebtResult.MoreThanOwed(available)

        dao.moveDebt(fromPersonId, toPersonId, amountMinor, from.name, to.name, note)
        notifier.onLedgerChanged()
        return MoveDebtResult.Moved(amountMinor)
    }

    /**
     * [moveDebt] across currencies, at a rate the caller has just fetched.
     *
     * [moveDebt] still refuses a currency mismatch on its own: it has no rate, and inventing one
     * would claim ₹100 as $100. This is the path that has one. [convertedMinor] is [amountMinor]
     * in the receiver's currency. The checks are the same, run against the sender's balance.
     */
    suspend fun moveDebtConverted(
        fromPersonId: Long,
        toPersonId: Long,
        amountMinor: Long,
        convertedMinor: Long,
        note: String = ""
    ): MoveDebtResult {
        if (fromPersonId == toPersonId) return MoveDebtResult.SamePerson
        if (amountMinor <= 0L || convertedMinor <= 0L) return MoveDebtResult.NotAnAmount

        val from = dao.getPersonNow(fromPersonId) ?: return MoveDebtResult.PersonGone
        val to = dao.getPersonNow(toPersonId) ?: return MoveDebtResult.PersonGone

        val available = dao.getBalanceNow(fromPersonId)
        if (available <= 0L) return MoveDebtResult.NothingToMove(available)
        if (amountMinor > available) return MoveDebtResult.MoreThanOwed(available)

        // Grouped like every other figure in the history: "₹1,50,000", not "₹150000".
        val conversion = "${amountString(amountMinor, from.currency)} as " +
            "${amountString(convertedMinor, to.currency)} at the day's rate"
        dao.moveDebtConverted(fromPersonId, toPersonId, amountMinor, convertedMinor, from.name, to.name, conversion, note)
        notifier.onLedgerChanged()
        return MoveDebtResult.Moved(amountMinor)
    }

    suspend fun editTransaction(transaction: Transaction) {
        dao.editTransaction(transaction)
        notifier.onLedgerChanged()
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        dao.removeTransaction(transaction)
        notifier.onLedgerChanged()
    }

    /** Closes a person's direct balance. Groups are settled inside the group. */
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
     * useless on anyone else's: a link whose sender is "You" gives the recipient nothing to match
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
     * `getPersonsWithBalancesNow` already filters out, since you cannot owe yourself.
     */
    suspend fun personsForImport(): List<PersonWithBalance> =
        dao.getPersonsWithBalancesNow()

    /**
     * The entries the next link for this person would carry.
     *
     * [fullHistory] ignores the watermark, which is how someone recovers from a share that never
     * arrived. The alternative would be a debt neither ledger can reconcile and no way back.
     */
    suspend fun entriesToShare(personId: Long, fullHistory: Boolean): List<Transaction> {
        if (fullHistory) return dao.getTransactionsSinceNow(personId, 0L)
        val since = dao.getPersonNow(personId)?.lastSharedAt ?: 0L
        // An update leaves out what came from their own links: sending it back told them "1 new
        // entry, they owe you ₹500" about the entry they had just sent. A full share keeps it,
        // because the other phone reads a full share as the whole ledger.
        return dao.getTransactionsSinceNow(personId, since).filterNot { it.fromShare }
    }

    /**
     * True when an update would be empty only because everything new came from their own links,
     * so the share sheet can say that rather than "nothing new since you last sent".
     */
    suspend fun onlyTheirEntriesAreNew(personId: Long): Boolean {
        val since = dao.getPersonNow(personId)?.lastSharedAt ?: 0L
        val pending = dao.getTransactionsSinceNow(personId, since)
        return pending.isNotEmpty() && pending.all { it.fromShare }
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
            senderName = senderName.trim().ifEmpty { "Someone" },
            currency = com.kg.merapaisa.data.normaliseCurrency(person.currency),
            // The uid travels with each entry: it is what lets the other phone recognise this same
            // debt the next time either side shares. See Transaction.uid.
            entries = entries.map { SharedEntry(it.timestamp, it.amountMinor, it.note, it.uid) },
            // Only a full share can prove a deletion, so only a full share is allowed to claim one.
            scope = if (fullHistory) ShareScope.Full else ShareScope.Incremental
        )
        // The watermark to commit once the link has actually gone out, not before.
        return payload to entries.maxOf { it.timestamp }
    }

    /**
     * Moves the watermark, so the next link carries only what comes after this one.
     *
     * Called when the share sheet is opened rather than when the message is confirmed sent,
     * because Android does not tell us whether the user went through with it. Advancing
     * optimistically can therefore skip entries if they back out, which is exactly what
     * `fullHistory` is the escape hatch for. The other way round would double-send by default, and a debt counted twice is
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
                note = it.note,
                // Their uid is kept, so this entry can be recognised again the next time either
                // side shares. A version 1 link has none to keep and gets a fresh one, which is
                // why an old link imports fine and still cannot be reconciled afterwards.
                uid = it.uid.ifEmpty { com.kg.merapaisa.data.newEntryUid() },
                fromShare = true
            )
        }
        return dao.applyPayload(personId, payload, entries, now)
            .also { if (it is ImportOutcome.Applied) notifier.onLedgerChanged() }
    }

    /**
     * What this link would change, without changing anything.
     *
     * Read against the person the link is about to be filed against, so switching the target in the
     * import screen has to recompute it: the same payload compared against a different person is a
     * different answer, usually "all of this is new".
     */
    suspend fun previewReconcile(personId: Long, payload: SharePayload, now: Long): ReconcilePlan =
        reconcile(
            personId = personId,
            // Mirrored here, once, so the comparison sees amounts as they will read on this phone.
            // Handing `reconcile` raw entries would classify every agreed debt as an edit.
            incoming = payload.mirrored(),
            local = dao.getTransactionsForPersonNow(personId),
            scope = payload.scope,
            comparable = payload.canReconcile,
            now = now,
            retired = dao.getRetiredUidsNow(personId).associate { it.uid to it.reason }
        )

    /**
     * Applies the ticked items of a plan.
     *
     * The plan is recomputed here rather than trusted from the screen. A plan built when the dialog
     * opened can be stale by the time it is confirmed (an entry added in another window, a restore
     * finishing in the background), and applying a stale plan would write row ids that have since
     * moved. The screen's selection is carried across by uid, which survives all of that.
     */
    suspend fun applyReconcile(
        personId: Long,
        payload: SharePayload,
        /**
         * The uids the user ticked, or null when they never saw a plan to tick.
         *
         * Null is not the same as empty. Empty means "they looked and chose nothing"; null means
         * the screen was confirmed before the comparison finished loading, and the safe reading of
         * that is the default selection: additions only, nothing overwritten or deleted.
         */
        selected: Set<String>?,
        now: Long
    ): ImportOutcome {
        val plan = previewReconcile(personId, payload, now)
        val write: ReconcileWrite = plan.writesFor(personId, selected ?: plan.defaultSelection)
        return dao.applyReconcile(personId, payload, write, now)
            .also { if (it is ImportOutcome.Reconciled && !it.changedNothing) notifier.onLedgerChanged() }
    }
}
