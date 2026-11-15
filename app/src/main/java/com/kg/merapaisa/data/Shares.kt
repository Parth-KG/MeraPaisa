package com.kg.merapaisa.data

/*
 * Shares of one amount, as "Adjust the shares" moves them: on Balances' Split an expense, and in a
 * group's Add and Edit expense (v3.7.0). Pure, so every rule runs under JVM tests. Moved here from
 * the split screen, unchanged, so the two places share one arithmetic.
 */

/**
 * [amountMinor] in equal parts for [ids], in that order, the minor units integer division leaves
 * over all going to the first, so the parts always add back up to the whole.
 */
fun equalSplit(amountMinor: Long, ids: List<Long>): Map<Long, Long> {
    if (ids.isEmpty()) return emptyMap()
    val base = amountMinor / ids.size
    val remainder = amountMinor - (base * ids.size)
    return ids.mapIndexed { i, id -> id to if (i == 0) base + remainder else base }.toMap()
}

/**
 * The shares after [changedId] is set to [newValue]: locked rows (which should include
 * [changedId]) keep theirs, and the unlocked rows take up the rest of [total] equally, the leftover
 * minor units to the first of them, none below zero. With nothing unlocked, nothing else moves,
 * and the shares may then not add up; the screen says so.
 */
fun redistribute(
    current: Map<Long, Long>,
    locked: Set<Long>,
    changedId: Long,
    newValue: Long,
    total: Long
): Map<Long, Long> {
    val updated = current.toMutableMap()
    updated[changedId] = newValue

    val lockedSum = updated.filterKeys { it in locked }.values.sum()
    val unlockedIds = updated.keys.filter { it !in locked }
    if (unlockedIds.isEmpty()) return updated

    val remaining = total - lockedSum
    val perUnlocked = remaining / unlockedIds.size
    // Integer division leaves a few minor units over; give them to the first row so the
    // parts still add up to the whole rather than tripping the totals-mismatch warning.
    val leftover = remaining - perUnlocked * unlockedIds.size

    unlockedIds.forEachIndexed { i, id ->
        val share = if (i == 0) perUnlocked + leftover else perUnlocked
        updated[id] = kotlin.math.max(0L, share)
    }
    return updated
}
