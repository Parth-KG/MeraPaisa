package com.kg.merapaisa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val personId: Long,
    /** Minor units — hundredths of the person's currency. See Money.kt. */
    val amountMinor: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)
