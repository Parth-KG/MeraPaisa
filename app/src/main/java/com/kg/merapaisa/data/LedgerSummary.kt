package com.kg.merapaisa.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.kg.merapaisa.ui.format.SignStyle
import com.kg.merapaisa.ui.format.amountString
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
    val amount = amountString(person.balanceMinor.absoluteValue, person.currency, SignStyle.None)
    val headline = when {
        person.balanceMinor > 0 -> "${person.name}: you owe me $amount"
        person.balanceMinor < 0 -> "${person.name}: I owe you $amount"
        else -> "${person.name}: we're even"
    }

    if (transactions.isEmpty()) return headline

    // The headline counts group expenses too; the log below is direct entries only. Without this
    // line the last running total disagreed with the headline and neither said why.
    return buildString {
        append(headline)
        append("\n\nRecent activity:\n")
        append(buildActivityLog(transactions, person.currency, limit, locale, timeZone))
        groupPartLine(person, transactions)?.let { append("\n").append(it) }
    }
}

/**
 * What group expenses add to [person]'s balance, as a closing line for a log of their direct
 * entries, or null when there is none. The log's last running total is direct entries only, so
 * without this it disagreed with the balance and nothing said why.
 */
fun groupPartLine(person: PersonWithBalance, transactions: List<Transaction>): String? {
    val fromGroups = person.balanceMinor - transactions.sumOf { it.amountMinor }
    // Direction in words, as the headline does: "Plus − ₹200" read as arithmetic.
    val figure = amountString(fromGroups.absoluteValue, person.currency, SignStyle.None)
    return when {
        fromGroups > 0 -> "Plus $figure you owe me in groups we share"
        fromGroups < 0 -> "Less $figure I owe you in groups we share"
        else -> null
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
        val amount = amountString(transaction.amountMinor, currency)
        val note = if (transaction.note.isNotBlank()) " (${transaction.note})" else ""
        "$date: $amount$note  →  ${amountString(running, currency)}"
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
