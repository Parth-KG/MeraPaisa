package com.kg.merapaisa.data

/** A net position in one currency. Never zero: a currency that nets out is not reported. */
data class CurrencyTotal(val currency: String, val amountMinor: Long)

/**
 * What you are up or down overall, per currency.
 *
 * Balances are deliberately not converted into a single figure: a rate is a guess about a day,
 * and quietly folding ₹ and $ together would report a number nobody actually owes anyone.
 * Currencies that net to zero are left out; the rest come back in a stable order.
 */
fun netTotalsByCurrency(persons: List<PersonWithBalance>): List<CurrencyTotal> =
    persons
        // Normalised, not raw. A row written by an early version stores "₹" where a newer one
        // stores "INR", and grouped by the literal string those became two totals stacked on the
        // main screen and the widget, both drawn with a ₹, as though there were two rupees. Every
        // other comparison in the app already goes through normaliseCurrency.
        .groupingBy { normaliseCurrency(it.currency) }
        .fold(0L) { running, person -> running + person.balanceMinor }
        .filterValues { it != 0L }
        .map { (currency, amountMinor) -> CurrencyTotal(currency, amountMinor) }
        .sortedBy { it.currency }
