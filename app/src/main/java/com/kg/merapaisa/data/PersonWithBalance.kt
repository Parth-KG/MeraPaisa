package com.kg.merapaisa.data

import androidx.room.Embedded

/**
 * A person alongside their derived balance, so the list screen can read both in one query
 * instead of summing per row. The pass-through properties keep call sites readable.
 */
data class PersonWithBalance(
    @Embedded val person: Person,
    val balanceMinor: Long
) {
    val id: Long get() = person.id
    val name: String get() = person.name
    val currency: String get() = person.currency
    val sortOrder: Int get() = person.sortOrder

    /**
     * Where this person sits on the main screen: on Even at exactly zero, on Active otherwise.
     *
     * The balance decides, and nothing else. It used to be the stored `isSettled` flag, which only
     * Settle up set, so someone just added sat on Active at zero, and someone who had paid you
     * back stayed there too. The column went in schema 12 (v3.7.1); backups and the CSV still say it,
     * worked out from the balance, for older versions. The list and the widget both ask this, so
     * they cannot disagree about who is open.
     */
    val isEven: Boolean get() = balanceMinor == 0L

    /**
     * Whether recording [amountMinor] against this person leaves them at exactly zero, so the
     * screen can move to the tab they are about to be on. A sum too large for a Long cannot be
     * zero.
     */
    fun isEvenAfter(amountMinor: Long): Boolean =
        runCatching { Math.addExact(balanceMinor, amountMinor) == 0L }.getOrDefault(false)
}
