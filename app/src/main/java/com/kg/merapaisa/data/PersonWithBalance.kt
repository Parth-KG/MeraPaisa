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
     * back stayed there too. The flag is still written, because backups and older versions of the
     * app expect it, but nothing reads it to place a person any more. The list and the widget both
     * ask this, so they cannot disagree about who is open.
     */
    val isEven: Boolean get() = balanceMinor == 0L
}
