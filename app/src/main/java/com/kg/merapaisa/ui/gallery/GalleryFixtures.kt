package com.kg.merapaisa.ui.gallery

import com.kg.merapaisa.data.CurrencyTotal
import com.kg.merapaisa.data.Expense
import com.kg.merapaisa.data.Group
import com.kg.merapaisa.data.GroupSummary
import com.kg.merapaisa.data.MemberBalance
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.Transfer

/**
 * Fake data for previews and the screenshot gallery.
 *
 * Chosen to stress the layout rather than flatter it. A gallery built from "Asha owes 100" proves
 * only that the happy path fits: it is the twelve lakh figure, the sixty character note and the
 * three currencies sitting in one column that show whether the design actually holds.
 *
 * Nothing here is used by the app itself. R8 drops it from the release build because no release
 * code path reaches it.
 */
object Fixtures {

    // -- people ----------------------------------------------------------------------------

    /** Owed to you, four figures: the ordinary case. */
    val asha = personWith(1, "Asha", "INR", 1_200_00)

    /** You owe them, small: the other direction, and a short amount beside long ones. */
    val bilal = personWith(2, "Bilal", "INR", -40_00)

    /** The width test: a long name and a lakh-grouped amount with paise, in one row. */
    val chaitanya = personWith(3, "Chaitanya Venkataraman", "INR", 12_34_567_50)

    /** A second currency, so a column has to align a wider symbol. */
    val dollarPerson = personWith(4, "Diego Hernández", "USD", 1_050_25)

    /** No decimals at all, so the decimal slot in a column has to stay empty rather than collapse. */
    val yenPerson = personWith(5, "Emi Nakamura", "JPY", -12_000_00)

    /** Exactly zero: no sign, and the word "even". */
    val evenPerson = personWith(6, "Farid Ahmed", "INR", 0)

    /** Closed with Settle up, so it renders in the Settled tab. */
    val settledPerson = personWith(7, "Gita Rao", "INR", 0, settled = true)

    /** The shortest possible name, which tends to break rows built around a wide avatar. */
    val shortName = personWith(8, "Jo", "INR", 75_50)

    /** The set every row and column layout has to survive. */
    val mixedPeople = listOf(asha, bilal, chaitanya, dollarPerson, yenPerson, evenPerson, shortName)

    /** Long enough to scroll, with the awkward cases kept at the top where they are visible. */
    val manyPeople: List<PersonWithBalance> = mixedPeople + (9..27).map { i ->
        personWith(i.toLong(), "Person $i", if (i % 5 == 0) "USD" else "INR", (i * 337_00L) - 3_000_00)
    }

    /** One line per currency, never summed together. */
    val totals = listOf(
        CurrencyTotal("INR", 13_36_727_50),
        CurrencyTotal("USD", 1_050_25),
        CurrencyTotal("JPY", -12_000_00)
    )

    val totalsSingle = listOf(CurrencyTotal("INR", 1_200_00))
    val totalsEven = listOf<CurrencyTotal>()

    /** Sixty characters: the note length a row has to truncate gracefully. */
    const val LONG_NOTE = "Dinner at the place near the station, split four ways ok?"

    // -- groups ----------------------------------------------------------------------------

    val groupMembers = listOf(
        Person(id = 1, name = "Asha", currency = "INR"),
        Person(id = 2, name = "Bilal", currency = "INR"),
        Person(id = 3, name = "Chaitanya Venkataraman", currency = "INR"),
        Person(id = 4, name = "Farid", currency = "INR"),
        Person(id = 5, name = "Gita", currency = "INR"),
        Person(id = 6, name = "Jo", currency = "INR")
    )

    /** Deliberately unequal, so the settle-up plan has something real to reduce. */
    val groupExpenses = listOf(
        Expense(id = 1, groupId = 1, description = "Hotel, two nights", amountMinor = 24_000_00, paidByPersonId = 1),
        Expense(id = 2, groupId = 1, description = "Cab from the airport", amountMinor = 2_450_00, paidByPersonId = 2),
        Expense(id = 3, groupId = 1, description = "Dinner", amountMinor = 6_780_50, paidByPersonId = 3),
        Expense(id = 4, groupId = 1, description = "Boat", amountMinor = 3_000_00, paidByPersonId = 1),
        Expense(id = 5, groupId = 1, description = "Breakfast", amountMinor = 940_00, paidByPersonId = 6)
    )

    val groupBalances = listOf(
        MemberBalance(1, 21_838_42),
        MemberBalance(2, -3_711_58),
        MemberBalance(3, 618_92),
        MemberBalance(4, -6_161_58),
        MemberBalance(5, -6_161_58),
        MemberBalance(6, -5_221_58)
    )

    val groupTransfers = listOf(
        Transfer(fromPersonId = 4, toPersonId = 1, amountMinor = 6_161_58),
        Transfer(fromPersonId = 5, toPersonId = 1, amountMinor = 6_161_58),
        Transfer(fromPersonId = 6, toPersonId = 1, amountMinor = 5_221_58),
        Transfer(fromPersonId = 2, toPersonId = 1, amountMinor = 3_711_58)
    )

    val group = Group(id = 1, name = "Goa, October", currency = "INR", createdAt = 1_700_000_000_000)

    val groupSummaries = listOf(
        GroupSummary(group, memberCount = 6, yourBalanceMinor = 21_838_42),
        GroupSummary(
            Group(id = 2, name = "Flat 3B", currency = "INR", createdAt = 1_700_000_000_000),
            memberCount = 3,
            yourBalanceMinor = -1_240_00
        ),
        GroupSummary(
            Group(id = 3, name = "Tokyo", currency = "JPY", createdAt = 1_700_000_000_000),
            memberCount = 4,
            yourBalanceMinor = 0
        )
    )

    private fun personWith(
        id: Long,
        name: String,
        currency: String,
        balanceMinor: Long,
        settled: Boolean = false
    ) = PersonWithBalance(
        Person(
            id = id,
            name = name,
            pfpValue = name.take(2).uppercase(),
            sortOrder = id.toInt(),
            isSettled = settled,
            currency = currency
        ),
        balanceMinor
    )
}
