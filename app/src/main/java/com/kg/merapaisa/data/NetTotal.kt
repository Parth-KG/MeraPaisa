package com.kg.merapaisa.data

/** A net position in one currency. Never zero — a currency that nets out is not reported. */
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
        .groupingBy { it.currency }
        .fold(0L) { running, person -> running + person.balanceMinor }
        .filterValues { it != 0L }
        .map { (currency, amountMinor) -> CurrencyTotal(currency, amountMinor) }
        .sortedBy { it.currency }
