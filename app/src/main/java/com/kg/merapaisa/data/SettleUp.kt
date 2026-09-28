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
 * The debts as they actually arose, netted only between each pair.
 *
 * The alternative to [settleUp]. Where that nets the whole group down to the fewest payments —
 * collapsing B out of "A owes B, B owes C" so A pays C — this keeps every debt attached to the
 * expense that created it. If you shared a dinner Ravi paid for, you owe Ravi, and no amount of
 * other people's spending moves that.
 *
 * Netting still happens *within* a pair, because "you owe me ₹300 and I owe you ₹100" is one
 * payment of ₹200 by any reading, and presenting it as two would be pedantry rather than honesty.
 *
 * **Exact by construction, with no rounding anywhere.** Shares are already whole minor units that
 * add up to their expense, so summing and subtracting them cannot introduce a fraction. That is
 * the reason this is built from shares rather than from a pro-rata split of each member's net
 * position, which would need two-dimensional rounding to keep both the payer totals and the
 * receiver totals honest.
 *
 * A member's own share of an expense they paid for is skipped: nobody owes themselves.
 */
fun directTransfers(
    expenses: List<Expense>,
    shares: List<ExpenseShare>
): List<Transfer> {
    val paidBy = expenses.associate { it.id to it.paidByPersonId }

    // debtor -> creditor -> amount
    val owed = LinkedHashMap<Pair<Long, Long>, Long>()
    shares.forEach { share ->
        val creditor = paidBy[share.expenseId] ?: return@forEach
        if (creditor == share.personId || share.shareMinor == 0L) return@forEach
        val key = share.personId to creditor
        owed[key] = (owed[key] ?: 0L) + share.shareMinor
    }

    // Net each pair against its opposite, then emit whichever direction survives.
    val settled = HashSet<Pair<Long, Long>>()
    val transfers = mutableListOf<Transfer>()
    owed.forEach { (pair, amount) ->
        val (debtor, creditor) = pair
        if (pair in settled) return@forEach
        settled.add(pair)
        settled.add(creditor to debtor)

        val net = amount - (owed[creditor to debtor] ?: 0L)
        when {
            net > 0 -> transfers += Transfer(debtor, creditor, net)
            net < 0 -> transfers += Transfer(creditor, debtor, -net)
        }
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
    // Distinct first, and before the arithmetic. The result is a map keyed by person, so a list
    // naming somebody twice used to divide by a count the map could not hold: `associateWith`
    // collapsed the duplicate while the division had already given away its share, and the parts
    // stopped adding up to the whole. Nothing downstream checks that sum, so the group's balances
    // would simply have been wrong by the difference, with every individual figure still plausible.
    val members = memberIds.distinct()
    if (members.isEmpty()) return emptyMap()
    val base = amountMinor / members.size
    var remainder = amountMinor - base * members.size
    return members.associateWith { _ ->
        val extra = if (remainder != 0L) remainder.coerceIn(-1L, 1L) else 0L
        remainder -= extra
        base + extra
    }
}
