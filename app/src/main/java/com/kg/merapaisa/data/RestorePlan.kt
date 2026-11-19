package com.kg.merapaisa.data

/**
 * Works out exactly what a restore would do, before it does any of it.
 *
 * Deliberately a pure function over two snapshots. The alternative (deciding what to insert while
 * inserting it, inside a database transaction) is both untestable without a device and impossible
 * to preview, and this is a screen whose entire job is to tell someone what is about to happen to
 * their ledger. Everything difficult (matching, id allocation, deduplication) happens here, in
 * plain Kotlin, under unit tests. Applying the result is then a dumb sequence of inserts.
 */

enum class RestoreMode {
    /**
     * Add what is missing and leave everything else alone. Re-running the same restore changes
     * nothing the second time, which makes it safe to tap when you are not sure.
     *
     * It cannot undo a deletion: something you deleted since the backup comes back.
     */
    Merge,

    /**
     * Wipe the ledger and lay the backup down exactly as it was. The right answer on a fresh
     * install, and destructive anywhere else, which is why the preview counts the deletions.
     */
    Replace
}

/**
 * Rows to insert, plus what the preview needs to describe the change in a sentence.
 *
 * Every id in here is final. The planner resolves them so that applying is a dumb insert with no
 * further decisions. See [planRestore].
 */
data class RestorePlan(
    val mode: RestoreMode,
    val persons: List<Person>,
    val transactions: List<Transaction>,
    val groups: List<Group>,
    val groupMembers: List<GroupMember>,
    val expenses: List<Expense>,
    val expenseShares: List<ExpenseShare>,
    /**
     * Merge only: shares on expenses already here to set back, for a payer who was handed the
     * share of someone deleted since the backup who now comes back ([expenseShares] carries
     * theirs). Applied by replacing the payer's row.
     */
    val sharesHandedBack: List<ExpenseShare> = emptyList(),
    /** Merge only: as [sharesHandedBack], where the payer had no share of their own before. */
    val sharesTakenBack: List<ExpenseShare> = emptyList(),
    val appliedPayloads: List<AppliedPayload>,
    val retiredUids: List<RetiredUid> = emptyList(),
    /**
     * Photos to put back, by final person id, as Base64URL of the image. For people this plan
     * inserts, and in Merge also for people already here who have no photo of their own.
     */
    val photos: Map<Long, String> = emptyMap(),
    /**
     * How many of [photos] go to people already on the phone, for the preview to say so. Merge
     * otherwise changes nothing about someone it matched; a photo for someone with none is the
     * one exception (Parth, 11 Nov 2026), since otherwise it would silently not come back.
     */
    val photosForPeopleAlreadyHere: Int = 0,
    /**
     * Merge only: people in the file whose entries are here on someone kept in another currency
     * now, so they come back as a second person (v3.7.1). By name, for the preview to say so.
     */
    val secondPeople: List<String> = emptyList(),
    /** What Replace would delete first. Zeroes in Merge, which deletes nothing. */
    val deletes: RestoreCounts,
    /** What Merge recognised and skipped. Zeroes in Replace, which starts from nothing. */
    val alreadyPresent: RestoreCounts
) {
    val inserts: RestoreCounts
        get() = RestoreCounts(
            people = persons.count { !it.isSelf },
            transactions = transactions.size,
            groups = groups.size,
            expenses = expenses.size
        )

    /** True when the restore would leave the ledger exactly as it is. */
    val changesNothing: Boolean
        get() = inserts.isZero && deletes.isZero && photosForPeopleAlreadyHere == 0 &&
            sharesHandedBack.isEmpty() && sharesTakenBack.isEmpty()
}

data class RestoreCounts(
    val people: Int = 0,
    val transactions: Int = 0,
    val groups: Int = 0,
    val expenses: Int = 0
) {
    val isZero: Boolean get() = people == 0 && transactions == 0 && groups == 0 && expenses == 0
}

/**
 * Builds the plan.
 *
 * [existing] is the ledger as it stands, [incoming] the backup. [localSelfId] is this phone's own
 * self row, which is never duplicated: a backup's self row is the same person as this device's, so
 * it is mapped rather than inserted.
 */
fun planRestore(
    existing: BackupSnapshot,
    incoming: BackupSnapshot,
    mode: RestoreMode,
    localSelfId: Long
): RestorePlan = when (mode) {
    RestoreMode.Replace -> planReplace(existing, incoming)
    RestoreMode.Merge -> planMerge(existing, incoming, localSelfId)
}

/**
 * Replace keeps the backup's own ids, because everything they pointed at is being deleted first.
 * The result is the backup, exactly.
 */
private fun planReplace(existing: BackupSnapshot, incoming: BackupSnapshot) = RestorePlan(
    mode = RestoreMode.Replace,
    persons = incoming.persons,
    transactions = incoming.transactions,
    groups = incoming.groups,
    groupMembers = incoming.groupMembers,
    expenses = incoming.expenses,
    expenseShares = incoming.expenseShares,
    appliedPayloads = incoming.appliedPayloads,
    retiredUids = incoming.retiredUids,
    photos = incoming.photos.filterKeys { id -> incoming.persons.any { it.id == id } },
    deletes = RestoreCounts(
        people = existing.persons.count { !it.isSelf },
        transactions = existing.transactions.size,
        groups = existing.groups.size,
        expenses = existing.expenses.size
    ),
    alreadyPresent = RestoreCounts()
)

private fun planMerge(
    existing: BackupSnapshot,
    incoming: BackupSnapshot,
    localSelfId: Long
): RestorePlan {
    // New ids are allocated above everything already in use, so an id in the backup can never
    // collide with an unrelated row that happens to share it.
    var nextPersonId = ((existing.persons.maxOfOrNull { it.id } ?: 0L) + 1)
    var nextGroupId = ((existing.groups.maxOfOrNull { it.id } ?: 0L) + 1)
    var nextExpenseId = ((existing.expenses.maxOfOrNull { it.id } ?: 0L) + 1)
    var nextTransactionId = ((existing.transactions.maxOfOrNull { it.id } ?: 0) + 1)

    val personIdMap = HashMap<Long, Long>()
    val newPersons = mutableListOf<Person>()
    var peopleAlreadyPresent = 0
    val photos = HashMap<Long, String>()
    var photosForPeopleAlreadyHere = 0

    val minted = incoming.mintedUids
    val herePeople = existing.persons.filter { !it.isSelf }
    val matchOf = HashMap<Long, Person>()

    // First by the entries themselves (v3.7.1). An entry keeps its uid through an edit, a rename of
    // its person and a currency change, so someone whose entries are here is that person, whatever
    // either is now called. Most shared entries first. A uid counts as shared when the same entry is
    // here on that person the same way round (a link puts the same uid on the other phone with the
    // sign turned), or when it was cleared here. Only within one currency: someone converted since
    // the backup has the old entries in the old currency, which cannot be put on her as they are.
    val hereSign = HashMap<String, MutableMap<Long, Int>>()
    existing.transactions.forEach { t ->
        hereSign.getOrPut(t.uid) { HashMap() }[t.personId] = java.lang.Long.signum(t.amountMinor)
    }
    val clearedHereByUid = existing.retiredUids
        .filter { it.reason == RETIRED_CLEARED }
        .groupBy({ it.uid }, { it.personId })
    val shared = HashMap<Pair<Long, Long>, Int>()
    incoming.transactions.forEach { t ->
        if (t.uid in minted) return@forEach
        val sign = java.lang.Long.signum(t.amountMinor)
        hereSign[t.uid]?.forEach { (hereId, hereSignOf) ->
            if (hereSignOf == sign) shared.merge(t.personId to hereId, 1, Int::plus)
        }
        clearedHereByUid[t.uid]?.forEach { hereId -> shared.merge(t.personId to hereId, 1, Int::plus) }
    }
    val incomingById = incoming.persons.filter { !it.isSelf }.associateBy { it.id }
    val hereById = herePeople.associateBy { it.id }
    val takenHere = HashSet<Long>()
    val inOtherCurrency = HashSet<Long>()
    shared.entries
        .sortedWith(
            compareByDescending<Map.Entry<Pair<Long, Long>, Int>> { it.value }
                .thenBy { if (it.key.first == it.key.second) 0 else 1 }
                .thenBy { it.key.first }
                .thenBy { it.key.second }
        )
        .forEach { (pair, _) ->
            val fromFile = incomingById[pair.first] ?: return@forEach
            val onPhone = hereById[pair.second] ?: return@forEach
            if (sameCurrency(fromFile.currency, onPhone.currency)) {
                if (fromFile.id !in matchOf && onPhone.id !in takenHere) {
                    matchOf[fromFile.id] = onPhone
                    takenHere += onPhone.id
                }
            } else {
                inOtherCurrency += fromFile.id
            }
        }

    // Then name and currency together, for everyone the entries did not settle, because the app
    // itself treats the same name in two currencies as two people. Case and surrounding space are
    // ignored: "asha " and "Asha" are one person. The app lets two people share a name and currency
    // (it only warns), so a key can stand for several people. Matching every one of them to the same
    // person put two people's money on one; see [pairSameNamed] for how they are paired instead.
    val existingByKey = herePeople
        .filter { it.id !in takenHere }
        .groupBy { personKey(it.name, it.currency) }
    val existingEntries = existing.transactions
        .groupBy({ it.personId }, { it })
        .mapValues { (_, rows) -> rows.flatMap { listOf(uidKey(it.uid), entryKey(it.timestamp, it.amountMinor, it.note)) }.toHashSet() }
    // Each entry in the file is known by its contents, and by its uid unless that was made up.
    val incomingEntries = incoming.transactions
        .groupBy({ it.personId }, {
            val content = entryKey(it.timestamp, it.amountMinor, it.note)
            if (it.uid in minted) setOf(content) else setOf(content, uidKey(it.uid))
        })
    incoming.persons
        .filter { !it.isSelf && it.id !in matchOf }
        .groupBy { personKey(it.name, it.currency) }
        .forEach { (key, people) ->
            matchOf.putAll(pairSameNamed(people, existingByKey[key].orEmpty(), incomingEntries, existingEntries))
        }

    incoming.persons.forEach { person ->
        if (person.isSelf) {
            // Never duplicated: the backup's "you" and this phone's "you" are the same person.
            personIdMap[person.id] = localSelfId
            return@forEach
        }
        val match = matchOf[person.id]
        val photo = incoming.photos[person.id]
        if (match != null) {
            personIdMap[person.id] = match.id
            peopleAlreadyPresent++
            // Their own photo wins; a photo is only given to someone with none. Once given, they
            // have one, so the same Merge run again gives nothing. "None" means no photo this phone
            // can read (`existing.photos` holds only readable ones), not only no photo set: Android's
            // own backup carried the row pointing at a photo but, before v3.7.1, not the file, so
            // after a device transfer she showed initials and Merge never gave her picture back.
            if (photo != null && match.id !in existing.photos) {
                photos[match.id] = photo
                photosForPeopleAlreadyHere++
            }
        } else {
            val id = nextPersonId++
            personIdMap[person.id] = id
            newPersons.add(person.copy(id = id))
            if (photo != null) photos[id] = photo
        }
    }

    // An entry is the same entry if it has the same uid on the same person (v3.7.1): it keeps its
    // uid through an edit, so an entry edited since the backup is not brought back beside its edit.
    // A uid cleared here is already counted in that person's opening balance, so it is not added on
    // top of it. Matching on contents instead did both.
    //
    // Contents still decide for an entry whose uid was made up on reading (a backup from before
    // v2.5, or a CSV): an entry is the same if it is on the same person for the same amount at the
    // same moment with the same note. Two genuinely identical entries of that kind collapse to one,
    // the accepted cost of restoring the same old backup twice safely. And for an entry here that
    // the file does not know by uid (one that came in from a CSV, say), whose contents a file entry
    // matches exactly, so that restoring a CSV and then the backup it came from adds nothing twice.
    val hereUids = existing.transactions.map { it.personId to it.uid }.toHashSet()
    val clearedHere = existing.retiredUids.filter { it.reason == RETIRED_CLEARED }.map { it.personId to it.uid }.toHashSet()
    val fileUids = incoming.transactions.filter { it.uid !in minted }.map { it.uid }.toHashSet()
    val hereByContentOnly = HashMap<String, Int>()
    existing.transactions.filter { it.uid !in fileUids }.forEach {
        hereByContentOnly.merge(transactionKey(it.personId, it.timestamp, it.amountMinor, it.note), 1, Int::plus)
    }
    val existingTransactionKeys = existing.transactions
        .map { transactionKey(it.personId, it.timestamp, it.amountMinor, it.note) }
        .toHashSet()

    // The mirror of Clear history: a backup made after a clear, merged onto a phone that still has
    // the entries the clear folded into its opening balance. Adding that opening balance beside them
    // counted the debt twice. It is left out when every entry it replaced is still here, exactly by
    // uid. An opening entry an earlier clear wrote, which this phone never had, is part of that
    // chain and needs nothing here; one it does have counts like any entry. If only some of the
    // entries are here, the opening balance is added as before (BACKUP.md says so).
    val skippedOpening = HashSet<Pair<Long, String>>()
    val clearNotCopied = HashSet<Pair<Long, String>>()
    matchOf.forEach { (fileId, here) ->
        val retiredHere = existing.retiredUids.filter { it.personId == here.id }.map { it.uid }.toHashSet()
        val uidsHere = existing.transactions.filter { it.personId == here.id }.map { it.uid }.toHashSet()
        val replaced = incoming.retiredUids
            .filter { it.personId == fileId && it.reason == RETIRED_CLEARED && it.uid !in retiredHere }
            .map { it.uid }
        val opening = incoming.transactions.filter {
            it.personId == fileId && it.uid !in minted && it.uid.startsWith(OPENING_UID_PREFIX) &&
                it.uid !in uidsHere && it.uid !in retiredHere
        }
        val allHere = replaced.all { it in uidsHere || it.startsWith(OPENING_UID_PREFIX) }
        if (opening.isNotEmpty() && replaced.any { it in uidsHere } && allHere) {
            opening.forEach { skippedOpening += fileId to it.uid }
            replaced.forEach { clearNotCopied += fileId to it }
        }
    }

    val newTransactions = mutableListOf<Transaction>()
    var transactionsAlreadyPresent = 0
    incoming.transactions.forEach { t ->
        val personId = personIdMap[t.personId] ?: return@forEach
        val key = transactionKey(personId, t.timestamp, t.amountMinor, t.note)
        val present = when {
            t.uid in minted -> !existingTransactionKeys.add(key)
            (t.personId to t.uid) in skippedOpening -> true
            (personId to t.uid) in hereUids || (personId to t.uid) in clearedHere -> true
            (hereByContentOnly[key] ?: 0) > 0 -> {
                hereByContentOnly.merge(key, -1, Int::plus)
                true
            }
            else -> false
        }
        if (present) {
            transactionsAlreadyPresent++
            return@forEach
        }
        hereUids += personId to t.uid
        existingTransactionKeys += key
        newTransactions.add(t.copy(id = nextTransactionId++, personId = personId))
    }

    // Groups match on currency and creation time, the moment the group was made, so a match means
    // "this is that same group", not "these share a label": two trips called "Goa" stay apart. The
    // name only tells apart groups made at the same moment. Matching on the name as well copied a
    // whole group, every expense and share, once it had been renamed since the backup.
    val existingGroupsByMoment = existing.groups.groupBy { groupMoment(it.currency, it.createdAt) }
    val groupIdMap = HashMap<Long, Long>()
    val newGroups = mutableListOf<Group>()
    var groupsAlreadyPresent = 0

    incoming.groups.forEach { group ->
        val candidates = existingGroupsByMoment[groupMoment(group.currency, group.createdAt)].orEmpty()
        val match = candidates.firstOrNull { sameName(it.name, group.name) } ?: candidates.singleOrNull()
        if (match != null) {
            groupIdMap[group.id] = match.id
            groupsAlreadyPresent++
        } else {
            val id = nextGroupId++
            groupIdMap[group.id] = id
            newGroups.add(group.copy(id = id))
        }
    }

    val existingMemberKeys = existing.groupMembers.map { it.groupId to it.personId }.toHashSet()
    val newMembers = mutableListOf<GroupMember>()
    incoming.groupMembers.forEach { m ->
        val groupId = groupIdMap[m.groupId] ?: return@forEach
        val personId = personIdMap[m.personId] ?: return@forEach
        if (existingMemberKeys.add(groupId to personId)) {
            newMembers.add(GroupMember(groupId, personId))
        }
    }

    // A payment also names who it went to, which lives only in its share. Moving your part of a
    // group to your ledger writes one payment per person at the same moment, so two people you owe
    // the same amount give two payments that differ only there; without it the second was taken
    // for the first and skipped, and that debt came back in the group beside its ledger entry.
    val existingPayees = existing.expenseShares.groupBy({ it.expenseId }, { it.personId })
    val incomingPayees = incoming.expenseShares.groupBy({ it.expenseId }, { personIdMap[it.personId] })
    fun payeesOf(e: Expense, payees: Map<Long, List<Long?>>): String =
        if (!e.isSettlement) "" else "\u0000to " + payees[e.id].orEmpty().map { it ?: -1L }.sorted().joinToString(",")

    val existingExpenseByKey = existing.expenses.associateBy {
        expenseKey(it.groupId, it.description, it.amountMinor, it.paidByPersonId, it.timestamp) + payeesOf(it, existingPayees)
    }
    val existingExpenseKeys = existingExpenseByKey.keys.toHashSet()

    val expenseIdMap = HashMap<Long, Long>()
    val newExpenses = mutableListOf<Expense>()
    var expensesAlreadyPresent = 0

    // Deleting someone hands their share of an expense somebody else paid to the payer (see
    // `PersonDao.reassignGroupSharesToPayer`), and that expense stays, so Merge finds it already
    // here. Bringing them back then restored only the expenses they paid, and the group's
    // positions came out the other way. So where an expense here holds exactly what that deletion
    // would have left, the share goes back from the payer to the person coming back.
    //
    // Coming back means having nothing in that group here: no share and no payment. That is
    // someone this Merge adds, and also someone deleted and then added again by name before the
    // Merge, whom it matches rather than adds. Only counting those it adds left the second with
    // the expenses she paid and none of her shares, and the positions ran the other way again.
    val involvedHere = HashSet<Pair<Long, Long>>()
    val groupOfExpense = existing.expenses.associate { it.id to it.groupId }
    existing.expenses.forEach { involvedHere += it.groupId to it.paidByPersonId }
    existing.expenseShares.forEach { s -> groupOfExpense[s.expenseId]?.let { involvedHere += it to s.personId } }
    val incomingSharesByExpense = incoming.expenseShares.groupBy { it.expenseId }
    val existingSharesByExpense = existing.expenseShares.groupBy { it.expenseId }
    val returnedShares = mutableListOf<ExpenseShare>()
    val sharesHandedBack = mutableListOf<ExpenseShare>()
    val sharesTakenBack = mutableListOf<ExpenseShare>()
    fun giveBackShares(here: Expense, from: Expense) {
        val backup = incomingSharesByExpense[from.id].orEmpty()
        if (backup.any { it.personId !in personIdMap }) return
        val shares = backup.associate { personIdMap.getValue(it.personId) to it.shareMinor }
        val payer = here.paidByPersonId
        val returning = shares.filterKeys { it != payer && it != localSelfId && (here.groupId to it) !in involvedHere }
        if (returning.isEmpty()) return
        val afterDeletion = (shares - returning.keys).toMutableMap()
        if (returning.values.any { it != 0L }) afterDeletion[payer] = (afterDeletion[payer] ?: 0L) + returning.values.sum()
        val onPhone = existingSharesByExpense[here.id].orEmpty().associate { it.personId to it.shareMinor }
        if (onPhone != afterDeletion) return
        returning.forEach { (personId, share) -> returnedShares += ExpenseShare(here.id, personId, share) }
        val payerShare = shares[payer]
        when {
            payerShare != null && payerShare != onPhone[payer] -> sharesHandedBack += ExpenseShare(here.id, payer, payerShare)
            payerShare == null && payer in onPhone -> sharesTakenBack += ExpenseShare(here.id, payer, onPhone.getValue(payer))
        }
    }

    incoming.expenses.forEach { e ->
        val groupId = groupIdMap[e.groupId] ?: return@forEach
        val paidBy = personIdMap[e.paidByPersonId] ?: return@forEach
        val key = expenseKey(groupId, e.description, e.amountMinor, paidBy, e.timestamp) + payeesOf(e, incomingPayees)
        if (!existingExpenseKeys.add(key)) {
            expensesAlreadyPresent++
            existingExpenseByKey[key]?.let { here -> giveBackShares(here, e) }
            return@forEach
        }
        val id = nextExpenseId++
        expenseIdMap[e.id] = id
        newExpenses.add(e.copy(id = id, groupId = groupId, paidByPersonId = paidBy))
    }

    // Shares follow their expense: only those belonging to an expense actually being inserted, or
    // the shares of an expense that is already here would be added a second time and its balances
    // would double.
    val newShares = incoming.expenseShares.mapNotNull { s ->
        val expenseId = expenseIdMap[s.expenseId] ?: return@mapNotNull null
        val personId = personIdMap[s.personId] ?: return@mapNotNull null
        ExpenseShare(expenseId, personId, s.shareMinor)
    } + returnedShares

    // Keyed by payload id, which is the point of that table: a link applied before the backup must
    // stay applied after the restore, or tapping it again would land its entries twice.
    val existingPayloadIds = existing.appliedPayloads.map { it.payloadId }.toHashSet()
    val newPayloads = incoming.appliedPayloads.mapNotNull { a ->
        if (!existingPayloadIds.add(a.payloadId)) return@mapNotNull null
        a.copy(personId = personIdMap[a.personId] ?: a.personId)
    }

    // Keyed by person and uid, the table's own key, onto whoever each person became here.
    val existingRetired = existing.retiredUids.map { it.personId to it.uid }.toHashSet()
    val newRetired = incoming.retiredUids.mapNotNull { r ->
        // A clear this phone never made, whose entries it still holds: nothing here was cleared.
        if ((r.personId to r.uid) in clearNotCopied) return@mapNotNull null
        val personId = personIdMap[r.personId] ?: return@mapNotNull null
        if (!existingRetired.add(personId to r.uid)) return@mapNotNull null
        r.copy(personId = personId)
    }

    return RestorePlan(
        mode = RestoreMode.Merge,
        persons = newPersons,
        transactions = newTransactions,
        groups = newGroups,
        groupMembers = newMembers,
        expenses = newExpenses,
        expenseShares = newShares,
        sharesHandedBack = sharesHandedBack,
        sharesTakenBack = sharesTakenBack,
        appliedPayloads = newPayloads,
        retiredUids = newRetired,
        photos = photos,
        photosForPeopleAlreadyHere = photosForPeopleAlreadyHere,
        secondPeople = incoming.persons.filter { it.id in inOtherCurrency && it.id !in matchOf }.map { it.name.trim() },
        deletes = RestoreCounts(),
        alreadyPresent = RestoreCounts(
            people = peopleAlreadyPresent,
            transactions = transactionsAlreadyPresent,
            groups = groupsAlreadyPresent,
            expenses = expensesAlreadyPresent
        )
    )
}

/**
 * Pairs the people of one name and currency in the file with those here. First by the entries
 * they share, most first, so someone deleted since the backup is not paired with the one who
 * stayed; then by the same id, as in a backup of this phone; then the rest in id order.
 */
private fun pairSameNamed(
    incoming: List<Person>,
    here: List<Person>,
    incomingEntries: Map<Long, List<Set<String>>>,
    hereEntries: Map<Long, Set<String>>
): Map<Long, Person> {
    val pairs = HashMap<Long, Person>()
    val taken = HashSet<Long>()
    fun pair(fromFile: Person, onPhone: Person) {
        if (fromFile.id in pairs || onPhone.id in taken) return
        pairs[fromFile.id] = onPhone
        taken += onPhone.id
    }
    incoming.flatMap { i ->
        here.map { h -> Triple(i, h, incomingEntries[i.id].orEmpty().count { keys -> keys.any { it in hereEntries[h.id].orEmpty() } }) }
    }
        .filter { it.third > 0 }
        .sortedWith(
            compareByDescending<Triple<Person, Person, Int>> { it.third }
                .thenBy { if (it.first.id == it.second.id) 0 else 1 }
                .thenBy { it.first.id }
                .thenBy { it.second.id }
        )
        .forEach { (i, h, _) -> pair(i, h) }
    incoming.forEach { i -> here.firstOrNull { it.id == i.id }?.let { h -> pair(i, h) } }
    incoming.filter { it.id !in pairs }.sortedBy { it.id }
        .zip(here.filter { it.id !in taken }.sortedBy { it.id })
        .forEach { (i, h) -> pair(i, h) }
    return pairs
}

private fun uidKey(uid: String): String = "uid\u0000$uid"

private fun sameCurrency(a: String, b: String): Boolean = normaliseCurrency(a.trim()) == normaliseCurrency(b.trim())

private fun entryKey(timestamp: Long, amountMinor: Long, note: String): String =
    "$timestamp\u0000$amountMinor\u0000${note.trim()}"

private fun personKey(name: String, currency: String): String =
    name.trim().lowercase() + "\u0000" + normaliseCurrency(currency.trim())

private fun groupMoment(currency: String, createdAt: Long): String =
    normaliseCurrency(currency.trim()) + "\u0000" + createdAt

private fun sameName(a: String, b: String): Boolean = a.trim().lowercase() == b.trim().lowercase()

private fun transactionKey(personId: Long, timestamp: Long, amountMinor: Long, note: String): String =
    "$personId\u0000$timestamp\u0000$amountMinor\u0000${note.trim()}"

private fun expenseKey(
    groupId: Long,
    description: String,
    amountMinor: Long,
    paidByPersonId: Long,
    timestamp: Long
): String = "$groupId\u0000${description.trim().lowercase()}\u0000$amountMinor\u0000$paidByPersonId\u0000$timestamp"
