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
    val currency: String = "INR"      // ISO 4217 code
)
