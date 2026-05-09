package com.merapaisa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val balance: Double = 0.0,
    val pfpType: String = "initials", // "initials", "emoji", "photo"
    val pfpValue: String = "",        // initials text, emoji, or file path
    val pfpColor: String = "#4CAF50", // color for initials background
    val sortOrder: Int = 0,
    val isSettled: Boolean = false,
    val currency: String = "₹"
)