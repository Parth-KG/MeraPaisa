package com.kg.merapaisa.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Every balance in the app is a SUM over this table filtered by personId, so that column is
 * indexed. The foreign key makes an orphaned entry — a transaction whose person no longer
 * exists, silently counted by nothing and visible in no history — impossible rather than
 * merely unlikely.
 */
@Entity(
    tableName = "transactions",
    indices = [Index("personId"), Index("uid")],
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val personId: Long,
    /** Minor units — hundredths of the person's currency. See Money.kt. */
    val amountMinor: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    /**
     * A stable name for the *debt*, not for this row — and the whole reason reconcile is possible.
     *
     * The row id is a local autoincrement, so the same dinner is id 7 on your phone and id 41 on
     * theirs. Comparing two ledgers across a link needs something both sides call by the same
     * name, and this is it: minted once by whichever phone first recorded the entry, then carried
     * in the share payload and stored *unchanged* on the other side alongside the mirrored amount.
     *
     * So both phones hold the same uid with opposite signs, and that is the invariant everything
     * downstream leans on. Same uid, exactly opposite amounts, same note means the two ledgers
     * agree about that debt. Anything else is a difference worth showing someone.
     *
     * Rows predating v2.5 are given one by the migration. They are random rather than derived from
     * the row, because two phones must never independently mint the same uid for different debts —
     * a hash of (amount, timestamp, note) would collide on exactly the common case of two people
     * both typing "Chai 20" on the same afternoon.
     */
    val uid: String = newEntryUid(),
    /**
     * True when this row was written by importing someone else's link rather than typed here.
     *
     * The difference matters only once, and it matters a lot: when a full payload arrives and one
     * of their earlier entries is missing from it, that means one of two completely different
     * things. If the row came from them, they deleted it. If you typed it, they have simply never
     * seen it. Offering to delete in the second case would throw away your own record because
     * somebody else had not heard about it yet.
     */
    val fromShare: Boolean = false
)

/**
 * A fresh entry uid: 16 hex characters, 64 bits of randomness.
 *
 * Not a UUID string, because this travels in a share link where every character is paid for twice
 * — once in the payload and again in Base64 — and a thousand entries of dashes and hex padding is
 * real length on a link a chat app may truncate. 64 bits is far more than enough for the handful
 * of entries two phones will ever exchange: at a million entries the chance of any collision is
 * still about one in forty billion.
 *
 * [java.util.UUID.randomUUID] rather than [kotlin.random.Random], since this is an identity the
 * sender cannot be allowed to guess or replay for anyone else's entries.
 */
fun newEntryUid(): String =
    java.util.UUID.randomUUID().toString().replace("-", "").take(16)
