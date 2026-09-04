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
    indices = [Index("personId")],
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
    val note: String = ""
)
