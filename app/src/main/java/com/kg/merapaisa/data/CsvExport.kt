package com.kg.merapaisa.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One person together with their full transaction history, for export. */
data class PersonLedger(
    val person: PersonWithBalance,
    val transactions: List<Transaction>
)

private val CSV_HEADER = listOf(
    "person", "currency", "balance", "settled", "timestamp", "date", "amount", "note"
)

/**
 * The whole ledger as RFC 4180 CSV: one row per transaction, with the person's details
 * repeated so the file opens as a single flat table.
 *
 * People with no transactions still get a row — an export that quietly omits someone is
 * worse than no export — and the raw epoch timestamp travels alongside the readable date so
 * the file can be read back without guessing a locale.
 */
fun buildLedgerCsv(
    ledgers: List<PersonLedger>,
    locale: Locale = Locale.US,
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale).apply { this.timeZone = timeZone }
    val rows = StringBuilder()
    rows.append(CSV_HEADER.joinToString(",")).append("\r\n")

    ledgers.sortedBy { it.person.name.lowercase(locale) }.forEach { ledger ->
        val person = ledger.person
        val personFields = listOf(
            person.name,
            person.currency,
            formatMinorPlain(person.balanceMinor, person.currency),
            if (person.isSettled) "yes" else "no"
        )

        if (ledger.transactions.isEmpty()) {
            rows.append(csvRow(personFields + listOf("", "", "", ""))).append("\r\n")
        } else {
            ledger.transactions.sortedBy { it.timestamp }.forEach { transaction ->
                rows.append(
                    csvRow(
                        personFields + listOf(
                            transaction.timestamp.toString(),
                            dateFormat.format(Date(transaction.timestamp)),
                            formatMinorPlain(transaction.amountMinor, person.currency),
                            transaction.note
                        )
                    )
                ).append("\r\n")
            }
        }
    }
    return rows.toString()
}

private fun csvRow(fields: List<String>): String = fields.joinToString(",") { escapeCsv(it) }

/** Names and notes are free text, so anything that would break the row gets quoted. */
private fun escapeCsv(field: String): String =
    if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + field.replace("\"", "\"\"") + "\""
    } else {
        field
    }
