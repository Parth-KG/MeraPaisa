package com.kg.merapaisa.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A payment the app is waiting to hear about, asked about the next time it opens.
 *
 * Two things leave one: a reminder sent with a pay link (did they pay you?), and a Pay whose "Did it
 * go through?" was never answered because the app closed while the UPI app was open (did yours go
 * through?). Nothing is recorded from a question without a yes. Recording the entry and removing the
 * question happen in one transaction, so a crash cannot leave a recorded payment still asking.
 *
 * Kept on this phone only: never in a backup, a link or the CSV. Android's own backup copies the
 * whole database, and a question restored that way lapses by itself (see [questionsStillAsking]).
 * Cascades with the person. Schema 11.
 */
@Entity(
    tableName = "payment_questions",
    indices = [Index("personId")],
    foreignKeys = [ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE)]
)
data class PaymentQuestion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personId: Long,
    /**
     * The entry a yes would record, signed as entries are: negative when they paid you (they owe
     * you less), positive when you paid them (you owe them less).
     */
    val amountMinor: Long,
    /** [QUESTION_REMINDER] or [QUESTION_PAY]. */
    val kind: String,
    val createdAt: Long,
    /** The person's balance when the question was left. Any other change to it drops the question. */
    val balanceAtCreation: Long
)

/** A reminder with a pay link went to them: did they pay you? */
const val QUESTION_REMINDER = "reminder"

/** You opened a UPI app to pay them and never said whether it went through. */
const val QUESTION_PAY = "pay"

/** How long a question is asked before it lapses unanswered. */
const val QUESTION_LIFETIME_MS = 7L * 24 * 60 * 60 * 1000
