package com.kg.merapaisa.data

/*
 * The arithmetic of paying someone from the app (v4.0.0). Pure, so it runs under JVM tests.
 * Nothing here pays or records: it says what a yes to "Did it go through?" would record.
 */

/** How much more than you owe [sentMinor] is, or 0 when it is not more. Both are positive. */
fun overpaidBy(owedMinor: Long, sentMinor: Long): Long = maxOf(0L, sentMinor - owedMinor)

/**
 * What a payment records if it went through. Paying more than you owe is allowed (Parth, 12 Nov
 * 2026): either all of it is recorded, and they then owe you the extra, or only what you owed, and
 * the extra stays out of the ledger as a gift or a tip.
 */
fun amountToRecord(owedMinor: Long, sentMinor: Long, recordOnlyWhatYouOwed: Boolean): Long =
    if (recordOnlyWhatYouOwed && sentMinor > owedMinor) owedMinor else sentMinor

/**
 * Whether Pay is offered for this person: kept in rupees (UPI is rupees only), with a UPI ID, and
 * owed money by you (a negative balance).
 */
fun canPayByUpi(currency: String, upiId: String?, balanceMinor: Long): Boolean =
    normaliseCurrency(currency) == "INR" && upiId != null && isUpiId(upiId) && balanceMinor < 0
