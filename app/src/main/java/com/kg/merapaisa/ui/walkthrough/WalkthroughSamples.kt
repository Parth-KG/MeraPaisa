package com.kg.merapaisa.ui.walkthrough

import com.kg.merapaisa.data.Group
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.Transfer
import com.kg.merapaisa.ui.gallery.Fixtures

/**
 * The sample ledger the walkthrough's pictures are drawn with: the gallery's Asha and Bilal, and a
 * small "Goa, October" with you in it. Nothing here is saved, and none of it is the user's own.
 */
internal object WalkthroughSamples {
    /** Owes you ₹1,200. */
    val asha: PersonWithBalance = Fixtures.asha

    /** You owe them ₹40. */
    val bilal: PersonWithBalance = Fixtures.bilal

    val people = listOf(asha, bilal)

    /** A share being typed on the keypad. */
    const val TYPED = "250"
    const val NOTE = "Dinner"

    // -- the split: ₹1,800 three ways, Asha's share locked ---------------------------------

    const val SPLIT_TOTAL = 1_800_00L

    // -- the group --------------------------------------------------------------------------

    const val YOU_ID = 10L
    val group = Group(id = 1, name = Fixtures.group.name, currency = "INR", createdAt = Fixtures.group.createdAt)
    val members = listOf(
        Person(id = YOU_ID, name = "You", currency = "INR", isSelf = true),
        Person(id = 1, name = "Asha", currency = "INR"),
        Person(id = 2, name = "Bilal", currency = "INR")
    )

    /** Hotel ₹6,000 paid by you and dinner ₹1,500 paid by Asha, shared three ways. */
    const val EXPENSES = 2
    const val YOUR_POSITION = 3_500_00L
    val transfers = listOf(
        Transfer(fromPersonId = 2, toPersonId = YOU_ID, amountMinor = 2_500_00),
        Transfer(fromPersonId = 1, toPersonId = YOU_ID, amountMinor = 1_000_00)
    )
}
