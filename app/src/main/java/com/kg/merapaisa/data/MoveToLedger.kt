package com.kg.merapaisa.data

/**
 * One person's part of a move from a group to your ledger (v3.7.0).
 *
 * [groupAmountMinor] is the pair as it stands in the group, in the group's currency, positive when
 * they owe you. [ledgerAmountMinor] is the entry their ledger gets, in their own currency (the same
 * figure unless the two currencies differ), signed the same way.
 */
data class MoveLine(val personId: Long, val groupAmountMinor: Long, val ledgerAmountMinor: Long)

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
