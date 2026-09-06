package com.kg.merapaisa.data

/** One member's net position inside a group: positive is owed, negative owes. */
data class MemberBalance(val personId: Long, val amountMinor: Long)

/** "A pays B ₹430." */
data class Transfer(val fromPersonId: Long, val toPersonId: Long, val amountMinor: Long)

/**
 * Reduces a group's net positions to the fewest payments that square everyone up.
 *
 * Within a group, who paid whom for any individual expense stops mattering once you only care
 * about ending square — all that survives is each member's net position. If A owes B and B owes
 * C the same amount, B is a pass-through and A can simply pay C.
 *
 * Repeatedly matching the largest debtor against the largest creditor settles at least one
 * member with every payment, so at most n-1 transfers are ever produced. That is not always the
 * theoretical minimum — finding that is NP-hard — but it is never worse than n-1 and is
 * instant for the handful of people a group actually holds.
 *
 * Balances are expected to sum to zero, since every expense is fully shared out. Any residue
 * from rounding is absorbed by the last transfer rather than left dangling.
 */
fun settleUp(balances: List<MemberBalance>): List<Transfer> {
    val creditors = balances.filter { it.amountMinor > 0 }
        .sortedWith(compareByDescending<MemberBalance> { it.amountMinor }.thenBy { it.personId })
        .map { it.personId to it.amountMinor }
        .toMutableList()
    val debtors = balances.filter { it.amountMinor < 0 }
        .sortedWith(compareBy<MemberBalance> { it.amountMinor }.thenBy { it.personId })
        .map { it.personId to -it.amountMinor }
        .toMutableList()

    val transfers = mutableListOf<Transfer>()
    var c = 0
    var d = 0
    while (c < creditors.size && d < debtors.size) {
        val (creditorId, owed) = creditors[c]
        val (debtorId, owes) = debtors[d]
        val amount = minOf(owed, owes)

        if (amount > 0) {
            transfers += Transfer(fromPersonId = debtorId, toPersonId = creditorId, amountMinor = amount)
        }

        creditors[c] = creditorId to (owed - amount)
        debtors[d] = debtorId to (owes - amount)
        if (creditors[c].second == 0L) c++
        if (debtors[d].second == 0L) d++
    }
    return transfers
}

/**
 * Each member's net position in a group: what they paid out, less what they were assigned.
 * Members who neither paid nor owe anything are still reported, at zero, so a group screen can
 * list everyone rather than only the people currently in the red.
 */
fun groupBalances(
    memberIds: List<Long>,
    paidByPerson: Map<Long, Long>,
    sharesByPerson: Map<Long, Long>
): List<MemberBalance> = memberIds.map { id ->
    MemberBalance(id, (paidByPerson[id] ?: 0L) - (sharesByPerson[id] ?: 0L))
}

/**
 * Splits an amount evenly, giving the remaining minor units to the earliest members so the
 * parts always add back up to the whole. Paise cannot be divided three ways.
 */
fun evenShares(amountMinor: Long, memberIds: List<Long>): Map<Long, Long> {
    if (memberIds.isEmpty()) return emptyMap()
    val base = amountMinor / memberIds.size
    var remainder = amountMinor - base * memberIds.size
    return memberIds.associateWith { _ ->
        val extra = if (remainder != 0L) remainder.coerceIn(-1L, 1L) else 0L
        remainder -= extra
        base + extra
    }
}
