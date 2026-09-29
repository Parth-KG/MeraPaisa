package com.kg.merapaisa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A person is deliberately balance-free: the balance is the sum of their transactions,
 * derived on read, so there is no second copy of the number that can drift out of step.
 */
@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val pfpType: String = "initials", // "initials", "emoji", "photo"
    val pfpValue: String = "",        // initials text, emoji, or file path
    val pfpColor: String = "#4CAF50", // color for initials background
    val sortOrder: Int = 0,
    val isSettled: Boolean = false,
    val currency: String = "INR",     // ISO 4217 code
    /**
     * Exactly one row is you. Groups need you to be a real member with a real id, rather than
     * the -1L sentinel the split flow used to special-case. You are hidden from the people
     * list, since you do not owe yourself anything.
     */
    val isSelf: Boolean = false,
    /**
     * Timestamp of the newest transaction included in the last share link sent for this person,
     * so the next link carries only what is new. 0 means nothing has been shared yet, which is
     * why it defaults to 0 rather than to now: a fresh person's first link should offer their
     * whole history, not an empty payload.
     */
    val lastSharedAt: Long = 0
)

/**
 * The first two characters of a name, for an avatar. Counted in code points, so a name that
 * starts "A🙂" keeps the emoji whole rather than ending on half of it, which drew as a box.
 */
fun initialsOf(name: String): String {
    val trimmed = name.trim()
    val end = if (trimmed.codePointCount(0, trimmed.length) <= 2) trimmed.length
    else trimmed.offsetByCodePoints(0, 2)
    return trimmed.substring(0, end).uppercase()
}
