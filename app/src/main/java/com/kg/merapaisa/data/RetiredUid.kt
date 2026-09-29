package com.kg.merapaisa.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * An entry uid this phone removed on purpose, and why.
 *
 * Links match entries by uid, so a uid that is simply gone reads as "never seen" the next time the
 * other phone sends its full history, and the entry comes back as new. Two ways that goes wrong:
 *
 *  - **Cleared.** Clearing a history folds every entry into an opening balance and deletes the
 *    rows. Their money is still counted, in the opening balance, so an incoming entry with one of
 *    these uids already matches and must not be added again.
 *  - **Deleted.** An entry removed by hand. The other phone may still have it, which is worth
 *    showing, but it is not added back unless someone ticks it.
 *
 * Cascades with the person: once they are deleted, a link filed against someone new starts clean.
 */
@Entity(
    tableName = "retired_uids",
    primaryKeys = ["personId", "uid"],
    indices = [Index("personId")],
    foreignKeys = [ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE)]
)
data class RetiredUid(
    val personId: Long,
    val uid: String,
    /** [RETIRED_CLEARED] or [RETIRED_DELETED]. */
    val reason: String,
    val retiredAt: Long
)

const val RETIRED_CLEARED = "cleared"
const val RETIRED_DELETED = "deleted"
