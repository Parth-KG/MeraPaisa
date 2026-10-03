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

/**
 * Both sides of where you stand in one currency, kept apart: what people owe you, and what you
 * owe them, as a negative. Either may be zero, but not both.
 */
data class CurrencySides(val currency: String, val owedToYouMinor: Long, val youOweMinor: Long)

/**
 * What you are owed and what you owe, per currency, without netting the two.
 *
 * The net hides how much is out either way: being owed ₹1,000 and owing ₹900 reads as ₹100. This
 * adds the positive balances and the negative ones separately. The same rules as the net: one
 * entry per currency, never summed across currencies, "₹" and "INR" counted as one, and a currency
 * where nobody owes anything either way left out.
 */
fun sidesByCurrency(persons: List<PersonWithBalance>): List<CurrencySides> =
    persons
        .groupBy { normaliseCurrency(it.currency) }
        .map { (currency, people) ->
            CurrencySides(
                currency = currency,
                owedToYouMinor = people.sumOf { maxOf(it.balanceMinor, 0L) },
                youOweMinor = people.sumOf { minOf(it.balanceMinor, 0L) }
            )
        }
        .filter { it.owedToYouMinor != 0L || it.youOweMinor != 0L }
        .sortedBy { it.currency }
