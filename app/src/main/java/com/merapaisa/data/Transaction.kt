package com.merapaisa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val personId: Long,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)