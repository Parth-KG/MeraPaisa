package com.kg.merapaisa.data

/**
 * One person's part of a move from a group to your ledger (v3.7.0).
 *
 * [groupAmountMinor] is the pair as it stands in the group, in the group's currency, positive when
 * they owe you. [ledgerAmountMinor] is the entry their ledger gets, in their own currency (the same
 * figure unless the two currencies differ), signed the same way.
 */
data class MoveLine(val personId: Long, val groupAmountMinor: Long, val ledgerAmountMinor: Long)

/**
 * What moving your part of a group to your ledger takes (v3.7.1): Settle up's payments that involve
 * you, by person, positive when they owe you. So the move writes exactly what the group screen
 * shows: the fewest payments while that switch is on, the debts as they arose while it is off.
 *
 * v3.7.0 always moved pair by pair. Once a fewest-payments payment had gone through you, that
 * wrote entries Settle up never showed and reopened a debt between two others (audit runs 4 and 6).
 */
fun moveLinesFor(transfers: List<Transfer>, selfId: Long): Map<Long, Long> {
    val lines = LinkedHashMap<Long, Long>()
    transfers.forEach { t ->
        when (selfId) {
            t.toPersonId -> lines[t.fromPersonId] = (lines[t.fromPersonId] ?: 0L) + t.amountMinor
            t.fromPersonId -> lines[t.toPersonId] = (lines[t.toPersonId] ?: 0L) - t.amountMinor
        }
    }
    return lines.filterValues { it != 0L }
}

/** What a move wrote, so Undo can take exactly those rows out again. */
data class MoveReceipt(
    val groupId: Long,
    val expenseIds: List<Long>,
    val entryUids: List<String>
) {
    val people: Int get() = entryUids.size
}

/**
 * The payment row that cancels one pair in the group: the person who owes pays, shared wholly with
 * the other, marked [MOVED_TO_LEDGER]. Returns the row and the share it carries.
 */
fun cancellingPayment(groupId: Long, selfId: Long, line: MoveLine, now: Long): Pair<Expense, Pair<Long, Long>> {
    val theyOweYou = line.groupAmountMinor > 0
    val amount = kotlin.math.abs(line.groupAmountMinor)
    val payer = if (theyOweYou) line.personId else selfId
    val payee = if (theyOweYou) selfId else line.personId
    val row = Expense(
        groupId = groupId,
        description = MOVED_TO_LEDGER,
        amountMinor = amount,
        paidByPersonId = payer,
        timestamp = now,
        isSettlement = true
    )
    return row to (payee to amount)
}
