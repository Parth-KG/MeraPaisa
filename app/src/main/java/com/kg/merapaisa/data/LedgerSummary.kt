package com.kg.merapaisa.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.absoluteValue

/** How many entries a shared summary shows before it starts saying "earlier entries". */
const val SUMMARY_ENTRY_LIMIT = 10

/**
 * Plain text for one person's position, written in the second person because it is meant to be
 * pasted straight into a chat with them.
 */
fun buildPersonSummary(
    person: PersonWithBalance,
    transactions: List<Transaction>,
    limit: Int = SUMMARY_ENTRY_LIMIT,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    val amount = formatMinor(person.balanceMinor.absoluteValue, person.currency)
    val headline = when {
        person.balanceMinor > 0 -> "${person.name} — you owe me $amount"
        person.balanceMinor < 0 -> "${person.name} — I owe you $amount"
        else -> "${person.name} — we're all settled up"
    }

    if (transactions.isEmpty()) return headline

    return buildString {
        append(headline)
        append("\n\nRecent activity:\n")
        append(buildActivityLog(transactions, person.currency, limit, locale, timeZone))
    }
}

/**
 * The transaction lines, oldest first, each with the balance as it stood after that entry.
 *
 * The running total is computed over every transaction and only the last [limit] lines are
 * shown, so a truncated log still reports true balances rather than starting from zero
 * part-way through the history.
 */
fun buildActivityLog(
    transactions: List<Transaction>,
    currency: String,
    limit: Int = Int.MAX_VALUE,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    if (transactions.isEmpty()) return ""

    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", locale).apply { this.timeZone = timeZone }
    var running = 0L
    val lines = transactions.sortedBy { it.timestamp }.map { transaction ->
        running += transaction.amountMinor
        val date = dateFormat.format(Date(transaction.timestamp))
        val amount = formatSignedAmount(transaction.amountMinor, currency)
        val note = if (transaction.note.isNotBlank()) " (${transaction.note})" else ""
        "$date: $amount$note  →  ${formatMinor(running, currency)}"
    }

    val hidden = (lines.size - limit).coerceAtLeast(0)
    return buildString {
        append(lines.takeLast(limit.coerceAtLeast(0)).joinToString("\n"))
        if (hidden > 0) {
            val entries = if (hidden == 1) "1 earlier entry" else "$hidden earlier entries"
            append("\n($entries not shown)")
        }
    }
}
