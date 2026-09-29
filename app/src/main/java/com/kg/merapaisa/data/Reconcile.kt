package com.kg.merapaisa.data

/**
 * Works out where two ledgers disagree, before changing either of them.
 *
 * Until v2.5 the honest answer to "what happens if we disagree?" was that nothing did. Importing a
 * link appended the sender's entries, mirrored, and `applied_payloads` stopped the same *link*
 * landing twice. If they edited an amount, or deleted an entry, or you both recorded the same
 * dinner, the two ledgers drifted apart in silence and neither phone ever mentioned it.
 *
 * This file is the comparison that was missing. It is a pure function over two lists (the entries
 * a link carries and the entries already here) for the same reason [planRestore] is: this decides
 * what happens to somebody's money, and deciding it inside a database transaction would make it
 * both untestable without a device and impossible to show anyone first.
 *
 * ### What makes the comparison possible
 *
 * [Transaction.uid]. Both phones call the same debt by the same name, so entries can be lined up
 * by identity rather than guessed at by looking similar. Matching on amount-and-date instead would
 * fail in both directions at once: two genuinely separate ₹20 chais on one afternoon would merge,
 * and an edited amount would look like a deletion plus an unrelated addition.
 *
 * ### What absence means
 *
 * Everything difficult here is about entries that are *not* in the payload, and the answer depends
 * entirely on [ShareScope]. In a [ShareScope.Full] link, an entry of theirs that is missing was
 * deleted by them. In a [ShareScope.Incremental] one, a missing entry means nothing at all. It is
 * simply older than their watermark. So deletions are only ever inferred from a full link, and
 * [reconcile] will not produce a single [ReconcileItem.DeletedBySender] from an incremental one.
 *
 * The second half of that rule is [Transaction.fromShare]. A missing entry that *you* typed was
 * never theirs to delete; they have just never seen it. That is worth telling you (you probably
 * want to share it back), but it is not a deletion, and the two must never be confused, because
 * one of them throws away your own record on somebody else's say-so.
 */

/** One difference between the two ledgers, or one point of agreement. */
sealed interface ReconcileItem {

    /** The shared name for the debt. Unique within a plan. */
    val uid: String

    /** For sorting and for showing a date beside each row. */
    val timestamp: Long

    /**
     * An entry of theirs this phone has never seen. The ordinary case, and what every import
     * before v2.5 did with every entry it was given.
     */
    data class New(
        override val uid: String,
        override val timestamp: Long,
        /** Already mirrored: what it will read as *here*. */
        val amountMinor: Long,
        val note: String,
        /**
         * Dated before this history was cleared here. Clearing folded everything into an opening
         * balance and dropped the uids, so this may be counted already. Arrives unticked.
         */
        val predatesClear: Boolean = false
    ) : ReconcileItem

    /**
     * Both sides hold this debt and agree about it. Listed rather than dropped, because "17 of
     * your 20 entries already match" is the sentence that makes the other three believable.
     */
    data class Unchanged(
        override val uid: String,
        override val timestamp: Long,
        val amountMinor: Long,
        val note: String
    ) : ReconcileItem

    /**
     * Both sides hold this debt and disagree about the amount, the note, or both.
     *
     * Their version wins only if the user says so. There is no basis for deciding automatically:
     * the payload is unauthenticated and carries no clock anyone can trust, so "theirs is newer"
     * is not a fact this app can establish. Accepting silently would let a crafted link rewrite
     * amounts that are already agreed.
     */
    data class Edited(
        override val uid: String,
        override val timestamp: Long,
        val localId: Int,
        val localAmountMinor: Long,
        val localNote: String,
        /** Already mirrored. */
        val theirAmountMinor: Long,
        val theirNote: String,
        /** Their timestamp, which may differ from the local one and is shown when it does. */
        val theirTimestamp: Long
    ) : ReconcileItem {
        val amountDiffers: Boolean get() = localAmountMinor != theirAmountMinor
        val noteDiffers: Boolean get() = localNote != theirNote
    }

    /**
     * An entry that came from this sender, absent from a full link: they have deleted it.
     *
     * Only ever produced for rows with [Transaction.fromShare] set, and only from a
     * [ShareScope.Full] payload. Both conditions are load-bearing; see the file header.
     */
    data class DeletedBySender(
        override val uid: String,
        override val timestamp: Long,
        val localId: Int,
        val amountMinor: Long,
        val note: String
    ) : ReconcileItem

    /**
     * Something you recorded that their ledger has no idea about.
     *
     * Informational only. There is deliberately no action attached: this app cannot write to
     * anyone else's phone, and deleting your own entry because a friend has not heard of it would
     * be precisely backwards. What it is for is telling you to send a link back.
     */
    data class OnlyYours(
        override val uid: String,
        override val timestamp: Long,
        val localId: Int,
        val amountMinor: Long,
        val note: String
    ) : ReconcileItem
}

/**
 * The full comparison, in the order it should be read: what needs a decision, then what is new,
 * then what they have not seen, then what already agrees.
 */
data class ReconcilePlan(
    val items: List<ReconcileItem>,
    val scope: ShareScope,
    /**
     * False when the payload predates uids (version 1) or is missing one. The import screen must
     * then fall back to plain appending and say so. See [SharePayload.canReconcile].
     */
    val comparable: Boolean
) {
    val new: List<ReconcileItem.New> get() = items.filterIsInstance<ReconcileItem.New>()
    val newBeforeClear: List<ReconcileItem.New> get() = new.filter { it.predatesClear }
    val edited: List<ReconcileItem.Edited> get() = items.filterIsInstance<ReconcileItem.Edited>()
    val unchanged: List<ReconcileItem.Unchanged> get() = items.filterIsInstance<ReconcileItem.Unchanged>()
    val deletedBySender: List<ReconcileItem.DeletedBySender>
        get() = items.filterIsInstance<ReconcileItem.DeletedBySender>()
    val onlyYours: List<ReconcileItem.OnlyYours> get() = items.filterIsInstance<ReconcileItem.OnlyYours>()

    /** Anything at all that would change this ledger if accepted. */
    val hasChanges: Boolean get() = new.isNotEmpty() || edited.isNotEmpty() || deletedBySender.isNotEmpty()

    /** Anything the user is being asked to decide about, as opposed to told about. */
    val needsDecision: Boolean
        get() = edited.isNotEmpty() || deletedBySender.isNotEmpty() || newBeforeClear.isNotEmpty()

    /**
     * What is ticked when the screen opens.
     *
     * New entries, yes: that is what importing a link has always meant, and unticking them by
     * default would make the ordinary case a chore. Edits and deletions, no: those overwrite or
     * destroy something that is already in the ledger, and nothing here can establish that the
     * sender is who they claim. A link that arrives while the phone is on a table must not be able
     * to delete anything by being tapped twice.
     */
    val defaultSelection: Set<String>
        get() = new.filterNot { it.predatesClear }.map { it.uid }.toSet()

    /**
     * What the ticked items would move the balance by.
     *
     * Needed because the import screen promises a figure before anything is written, and that
     * promise has to follow the tick boxes. Found on a device: the screen said "you will owe ₹412"
     * (the whole payload appended) while the default ticks actually produced ₹402, because the
     * edit and the deletion were correctly left alone. A screen whose entire job is to say what is
     * about to happen cannot be out by the exact amount the user chose to decline.
     *
     * Computed from the plan rather than the database: an edit knows the amount it is replacing
     * and a deletion knows the amount it is removing, so no read is needed. The DAO still derives
     * the authoritative figure from the rows when it writes.
     */
    fun netChangeFor(selected: Set<String>): Long = items.sumOf { item ->
        if (comparable && item.uid !in selected) return@sumOf 0L
        when (item) {
            is ReconcileItem.New -> item.amountMinor
            is ReconcileItem.Edited -> item.theirAmountMinor - item.localAmountMinor
            is ReconcileItem.DeletedBySender -> -item.amountMinor
            is ReconcileItem.Unchanged, is ReconcileItem.OnlyYours -> 0L
        }
    }
}

/**
 * The writes a chosen set of items comes to. Every id is final; applying is a dumb three-step.
 */
data class ReconcileWrite(
    val inserts: List<Transaction>,
    val updates: List<Transaction>,
    val deleteIds: List<Int>
) {
    val isEmpty: Boolean get() = inserts.isEmpty() && updates.isEmpty() && deleteIds.isEmpty()

    /** What the person's balance will move by. Shown before anything is written. */
    val netChangeMinor: Long
        get() = inserts.sumOf { it.amountMinor }
}

/**
 * Compares an incoming payload against what this phone already holds for that person.
 *
 * [incoming] must already be **mirrored**: the amounts as they will read here, not as the sender
 * wrote them. Mirroring stays in [SharePayload.mirrored] so there is exactly one place that can
 * get the sign wrong, and this function is not it. Passing raw entries would classify every
 * matching debt as an edit that doubles it.
 *
 * [now] clamps timestamps forward-dated by a crafted link, exactly as the plain import path does.
 */
fun reconcile(
    personId: Long,
    incoming: List<SharedEntry>,
    local: List<Transaction>,
    scope: ShareScope,
    comparable: Boolean,
    now: Long
): ReconcilePlan {
    if (!comparable) {
        // No uids, so nothing can be lined up. Everything is new, which is what every version of
        // this app before v2.5 assumed unconditionally.
        return ReconcilePlan(
            items = incoming.map {
                ReconcileItem.New(
                    uid = "",
                    timestamp = clamp(it.timestamp, now),
                    amountMinor = it.amountMinor,
                    note = it.note
                )
            },
            scope = scope,
            comparable = false
        )
    }

    // First row wins where a uid somehow appears twice locally (a restore that ran twice, say).
    // The duplicates are still counted as present, so nothing offers to delete them as missing.
    val localByUid = LinkedHashMap<String, Transaction>()
    local.forEach { row -> if (row.uid.isNotEmpty()) localByUid.putIfAbsent(row.uid, row) }

    val incomingUids = incoming.map { it.uid }.toHashSet()
    val items = mutableListOf<ReconcileItem>()
    // When this history was last cleared, if it was: the newest opening entry a clear wrote.
    val clearedAt = local.filter { it.uid.startsWith(OPENING_UID_PREFIX) }.maxOfOrNull { it.timestamp }

    incoming.forEach { entry ->
        val existing = localByUid[entry.uid]
        val stamp = clamp(entry.timestamp, now)
        items += when {
            existing == null ->
                ReconcileItem.New(
                    entry.uid, stamp, entry.amountMinor, entry.note,
                    predatesClear = clearedAt != null && stamp < clearedAt
                )

            existing.amountMinor == entry.amountMinor && existing.note == entry.note ->
                ReconcileItem.Unchanged(entry.uid, existing.timestamp, existing.amountMinor, existing.note)

            else -> ReconcileItem.Edited(
                uid = entry.uid,
                timestamp = existing.timestamp,
                localId = existing.id,
                localAmountMinor = existing.amountMinor,
                localNote = existing.note,
                theirAmountMinor = entry.amountMinor,
                theirNote = entry.note,
                theirTimestamp = stamp
            )
        }
    }

    // Now the other direction: local entries the payload says nothing about.
    local.forEach { row ->
        if (row.uid.isNotEmpty() && row.uid in incomingUids) return@forEach
        when {
            // Absence proves nothing in an incremental link, so it is not reported at all.
            // Listing everything older than their watermark as "they have not seen this" would
            // be noise, and wrong.
            scope != ShareScope.Full -> Unit

            row.fromShare -> items += ReconcileItem.DeletedBySender(
                uid = row.uid, timestamp = row.timestamp, localId = row.id,
                amountMinor = row.amountMinor, note = row.note
            )

            else -> items += ReconcileItem.OnlyYours(
                uid = row.uid, timestamp = row.timestamp, localId = row.id,
                amountMinor = row.amountMinor, note = row.note
            )
        }
    }

    return ReconcilePlan(items = items.sortedWith(DISPLAY_ORDER), scope = scope, comparable = true)
}

/**
 * Turns the chosen items into rows to write.
 *
 * [selected] holds the uids the user left ticked. Anything unticked is left exactly as it is:
 * an unticked edit keeps the local amount, an unticked deletion keeps the entry. Doing nothing is
 * always a valid answer here, and has to remain one.
 */
fun ReconcilePlan.writesFor(personId: Long, selected: Set<String>): ReconcileWrite {
    val inserts = mutableListOf<Transaction>()
    val updates = mutableListOf<Transaction>()
    val deleteIds = mutableListOf<Int>()

    items.forEach { item ->
        // A v1 payload has no uids to tick, so every entry in it is taken; there is nothing to
        // choose between. `comparable` is false in exactly that case.
        if (comparable && item.uid !in selected) return@forEach
        when (item) {
            is ReconcileItem.New -> inserts += Transaction(
                personId = personId,
                amountMinor = item.amountMinor,
                timestamp = item.timestamp,
                note = item.note,
                // Their uid, kept as sent. This is the line that makes the *next* reconcile work.
                uid = item.uid.ifEmpty { newEntryUid() },
                fromShare = true
            )

            is ReconcileItem.Edited -> updates += Transaction(
                id = item.localId,
                personId = personId,
                amountMinor = item.theirAmountMinor,
                timestamp = item.theirTimestamp,
                note = item.theirNote,
                uid = item.uid,
                // Accepting their version makes this their row. If they later delete it, that is
                // now a deletion this phone will recognise rather than silently keep.
                fromShare = true
            )

            is ReconcileItem.DeletedBySender -> deleteIds += item.localId

            // Nothing to write for either: one is already correct, the other is yours to keep.
            is ReconcileItem.Unchanged, is ReconcileItem.OnlyYours -> Unit
        }
    }

    return ReconcileWrite(inserts, updates, deleteIds)
}

/** Decisions first, then additions, then what they are missing, then what already agrees. */
private val DISPLAY_ORDER = compareBy<ReconcileItem>(
    {
        when (it) {
            is ReconcileItem.Edited -> 0
            is ReconcileItem.DeletedBySender -> 1
            is ReconcileItem.New -> 2
            is ReconcileItem.OnlyYours -> 3
            is ReconcileItem.Unchanged -> 4
        }
    },
    { it.timestamp }
)

/**
 * A payload is untrusted input and a crafted one can claim the year 5000; left alone such an entry
 * sits at the top of the history forever and poisons the watermark for every later share. Only
 * ever moves a timestamp backwards to the present, so a genuine entry is untouched.
 */
private fun clamp(timestamp: Long, now: Long): Long = if (timestamp > now) now else timestamp
