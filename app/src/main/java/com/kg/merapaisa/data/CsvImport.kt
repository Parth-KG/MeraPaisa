package com.kg.merapaisa.data

/**
 * Reads back what [buildLedgerCsv] writes.
 *
 * **The `balance` column is deliberately ignored.** It is the balance the app displays, which since
 * v2.0.2 includes each person's slice of group activity — while the `amount` rows in the same file
 * cover direct transactions only. For anyone in a group the two therefore disagree, by design.
 * Trusting the column would import a balance with no entries to explain it; deriving from the rows
 * gives a ledger that adds up, and the group activity comes back from a JSON backup instead.
 *
 * The other half of the same limitation: this file has no groups in it at all. A CSV restore is a
 * partial restore, and the UI has to say so rather than letting someone believe otherwise.
 */

/** One person as the CSV describes them, with the entries found under their name. */
data class ImportedPerson(
    val name: String,
    val currency: String,
    val isSettled: Boolean,
    val transactions: List<ImportedTransaction>
)

data class ImportedTransaction(
    val timestamp: Long,
    val amountMinor: Long,
    val note: String
)

sealed interface CsvImportResult {
    data class Ok(val people: List<ImportedPerson>) : CsvImportResult

    /** Parsed as CSV, but the header is not this app's. */
    data object NotALedgerCsv : CsvImportResult

    /**
     * Something in the file could not be read. Carries the line so the user can go and look — a
     * CSV is the one format here people genuinely do hand-edit, and "row 47 is broken" is worth
     * far more than "invalid file".
     */
    data class Damaged(val line: Int, val reason: String) : CsvImportResult
}

private val EXPECTED_HEADER = listOf(
    "person", "currency", "balance", "settled", "timestamp", "date", "amount", "note"
)

/** Index into a row, named so the column order is stated once. */
private const val COL_PERSON = 0
private const val COL_CURRENCY = 1
private const val COL_SETTLED = 3
private const val COL_TIMESTAMP = 4
private const val COL_AMOUNT = 6
private const val COL_NOTE = 7

/** Spellings of "settled" that a spreadsheet might produce from the exporter's "yes". */
private val SETTLED_WORDS = setOf("yes", "true", "1")

fun readLedgerCsv(text: String): CsvImportResult {
    val rows = parseCsv(text) ?: return CsvImportResult.Damaged(0, "the file is not valid CSV")
    if (rows.isEmpty()) return CsvImportResult.NotALedgerCsv

    val header = rows.first().map { it.trim().lowercase() }
    if (header != EXPECTED_HEADER) return CsvImportResult.NotALedgerCsv

    // Grouped by name and currency together: the same name in two currencies is two people here,
    // exactly as it is in the app.
    val byPerson = LinkedHashMap<Pair<String, String>, MutableList<ImportedTransaction>>()
    val settledFlags = LinkedHashMap<Pair<String, String>, Boolean>()

    rows.drop(1).forEachIndexed { index, row ->
        val line = index + 2 // 1-based, and the header is line 1
        if (row.size == 1 && row[0].isBlank()) return@forEachIndexed // trailing newline

        if (row.size != EXPECTED_HEADER.size) {
            return CsvImportResult.Damaged(line, "expected ${EXPECTED_HEADER.size} columns, found ${row.size}")
        }

        val name = row[COL_PERSON].trim()
        if (name.isEmpty()) return CsvImportResult.Damaged(line, "the person column is empty")

        val currency = normaliseCurrency(row[COL_CURRENCY].trim())
        if (currency.isEmpty()) return CsvImportResult.Damaged(line, "the currency column is empty")

        val key = name to currency
        val entries = byPerson.getOrPut(key) { mutableListOf() }

        // Read once, from the person's first row. Written as an explicit absence check rather than
        // an elvis: `in` binds looser than `?:` in Kotlin, so `flags[key] ?: text in SETTLED_WORDS`
        // parses as `(flags[key] ?: text) in SETTLED_WORDS` and, on the second row, compares the
        // stored Boolean against a set of Strings — quietly un-settling anyone with more than one
        // transaction.
        //
        // "yes"/"no" is what the exporter writes; the rest are tolerated because a spreadsheet
        // round trip may well have turned it into TRUE or 1.
        if (key !in settledFlags) {
            settledFlags[key] = row[COL_SETTLED].trim().lowercase() in SETTLED_WORDS
        }

        val rawTimestamp = row[COL_TIMESTAMP].trim()
        val rawAmount = row[COL_AMOUNT].trim()

        // A person with no transactions still gets a row, with those columns empty. That is a
        // person to create, not a row to reject.
        if (rawTimestamp.isEmpty() && rawAmount.isEmpty()) return@forEachIndexed

        val timestamp = rawTimestamp.toLongOrNull()
            ?: return CsvImportResult.Damaged(line, "\"$rawTimestamp\" is not a timestamp")
        if (timestamp < 0) return CsvImportResult.Damaged(line, "the timestamp is negative")

        // The exporter writes amounts with formatMinorPlain, which parseAmountToMinor reads back —
        // the same pair the numpad uses, so the rounding rules are identical.
        val amountMinor = parseAmountToMinor(rawAmount)
            ?: return CsvImportResult.Damaged(line, "\"$rawAmount\" is not an amount")

        entries.add(ImportedTransaction(timestamp, amountMinor, row[COL_NOTE]))
    }

    return CsvImportResult.Ok(
        byPerson.map { (key, entries) ->
            val (name, currency) = key
            ImportedPerson(
                name = name,
                currency = currency,
                isSettled = settledFlags[key] ?: false,
                transactions = entries.sortedBy { it.timestamp }
            )
        }
    )
}

/**
 * An RFC 4180 reader: quoted fields may contain commas, line breaks and doubled quotes.
 *
 * Returns null only for a structurally impossible file — an unterminated quoted field — because
 * everything else is better reported per line by the caller.
 */
fun parseCsv(text: String): List<List<String>>? {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0

    // A UTF-8 BOM is what a spreadsheet leaves behind, and it would otherwise become part of the
    // first header cell and make the file look like somebody else's CSV.
    if (text.startsWith("\uFEFF")) i = 1

    while (i < text.length) {
        val c = text[i]
        if (inQuotes) {
            when {
                c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i += 2 }
                c == '"' -> { inQuotes = false; i++ }
                else -> { field.append(c); i++ }
            }
        } else {
            when (c) {
                '"' -> { inQuotes = true; i++ }
                ',' -> { row.add(field.toString()); field.setLength(0); i++ }
                '\r' -> {
                    // CRLF or a lone CR both end the row.
                    row.add(field.toString()); field.setLength(0)
                    rows.add(row); row = mutableListOf()
                    i += if (i + 1 < text.length && text[i + 1] == '\n') 2 else 1
                }
                '\n' -> {
                    row.add(field.toString()); field.setLength(0)
                    rows.add(row); row = mutableListOf()
                    i++
                }
                else -> { field.append(c); i++ }
            }
        }
    }
    if (inQuotes) return null

    // Whatever is left is a final row without a trailing newline.
    if (field.isNotEmpty() || row.isNotEmpty()) {
        row.add(field.toString())
        rows.add(row)
    }

    // Drop a trailing blank row, which is what a file ending in a newline produces.
    return rows.filterNot { it.size == 1 && it[0].isEmpty() }
}
