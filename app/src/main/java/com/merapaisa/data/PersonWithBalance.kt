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
    val isSettled: Boolean get() = person.isSettled
    val sortOrder: Int get() = person.sortOrder
}
