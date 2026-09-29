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
    val appliedPayloads: List<AppliedPayload>,
    val retiredUids: List<RetiredUid> = emptyList(),
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
        get() = inserts.isZero && deletes.isZero
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

    // Name and currency together, because the app itself treats the same name in two currencies as
    // two people. Case and surrounding space are ignored: "asha " and "Asha" are one person.
    val existingByKey = existing.persons
        .filter { !it.isSelf }
        .associateBy { personKey(it.name, it.currency) }

    incoming.persons.forEach { person ->
        if (person.isSelf) {
            // Never duplicated: the backup's "you" and this phone's "you" are the same person.
            personIdMap[person.id] = localSelfId
            return@forEach
        }
        val match = existingByKey[personKey(person.name, person.currency)]
        if (match != null) {
            personIdMap[person.id] = match.id
            peopleAlreadyPresent++
        } else {
            val id = nextPersonId++
            personIdMap[person.id] = id
            newPersons.add(person.copy(id = id))
        }
    }

    // An entry is the same entry if it is on the same person for the same amount at the same moment
    // with the same note. Two genuinely identical entries a user really did record twice would
    // collapse to one, which is the accepted cost of restoring the same backup twice safely.
    val existingTransactionKeys = existing.transactions
        .map { transactionKey(it.personId, it.timestamp, it.amountMinor, it.note) }
        .toHashSet()

    val newTransactions = mutableListOf<Transaction>()
    var transactionsAlreadyPresent = 0
    incoming.transactions.forEach { t ->
        val personId = personIdMap[t.personId] ?: return@forEach
        val key = transactionKey(personId, t.timestamp, t.amountMinor, t.note)
        if (!existingTransactionKeys.add(key)) {
            transactionsAlreadyPresent++
            return@forEach
        }
        newTransactions.add(t.copy(id = nextTransactionId++, personId = personId))
    }

    // Groups match on name, currency and creation time together. Name alone would merge two
    // different trips that happen to be called "Goa"; adding createdAt makes a match mean
    // "this is that same group", not "these share a label".
    val existingGroupByKey = existing.groups.associateBy { groupKey(it.name, it.currency, it.createdAt) }
    val groupIdMap = HashMap<Long, Long>()
    val newGroups = mutableListOf<Group>()
    var groupsAlreadyPresent = 0

    incoming.groups.forEach { group ->
        val match = existingGroupByKey[groupKey(group.name, group.currency, group.createdAt)]
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

    val existingExpenseKeys = existing.expenses
        .map { expenseKey(it.groupId, it.description, it.amountMinor, it.paidByPersonId, it.timestamp) }
        .toHashSet()

    val expenseIdMap = HashMap<Long, Long>()
    val newExpenses = mutableListOf<Expense>()
    var expensesAlreadyPresent = 0

    incoming.expenses.forEach { e ->
        val groupId = groupIdMap[e.groupId] ?: return@forEach
        val paidBy = personIdMap[e.paidByPersonId] ?: return@forEach
        val key = expenseKey(groupId, e.description, e.amountMinor, paidBy, e.timestamp)
        if (!existingExpenseKeys.add(key)) {
            expensesAlreadyPresent++
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
    }

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
        appliedPayloads = newPayloads,
        retiredUids = newRetired,
        deletes = RestoreCounts(),
        alreadyPresent = RestoreCounts(
            people = peopleAlreadyPresent,
            transactions = transactionsAlreadyPresent,
            groups = groupsAlreadyPresent,
            expenses = expensesAlreadyPresent
        )
    )
}

private fun personKey(name: String, currency: String): String =
    name.trim().lowercase() + "\u0000" + normaliseCurrency(currency.trim())

private fun groupKey(name: String, currency: String, createdAt: Long): String =
    name.trim().lowercase() + "\u0000" + normaliseCurrency(currency.trim()) + "\u0000" + createdAt

private fun transactionKey(personId: Long, timestamp: Long, amountMinor: Long, note: String): String =
    "$personId\u0000$timestamp\u0000$amountMinor\u0000${note.trim()}"

private fun expenseKey(
    groupId: Long,
    description: String,
    amountMinor: Long,
    paidByPersonId: Long,
    timestamp: Long
): String = "$groupId\u0000${description.trim().lowercase()}\u0000$amountMinor\u0000$paidByPersonId\u0000$timestamp"
