package com.kg.merapaisa.ui.gallery

import com.kg.merapaisa.data.OPENING_UID_PREFIX
import com.kg.merapaisa.data.BackupSnapshot
import com.kg.merapaisa.data.CurrencyTotal
import com.kg.merapaisa.data.Expense
import com.kg.merapaisa.data.Group
import com.kg.merapaisa.data.GroupSummary
import com.kg.merapaisa.data.ImportOutcome
import com.kg.merapaisa.data.MemberBalance
import com.kg.merapaisa.data.Person
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.data.ReconcileItem
import com.kg.merapaisa.data.ReconcilePlan
import com.kg.merapaisa.data.RestoreCounts
import com.kg.merapaisa.data.RestoreMode
import com.kg.merapaisa.data.RestorePlan
import com.kg.merapaisa.data.SharePayload
import com.kg.merapaisa.data.ShareScope
import com.kg.merapaisa.data.SharedEntry
import com.kg.merapaisa.data.Transaction
import com.kg.merapaisa.data.Transfer
import com.kg.merapaisa.ui.BackupFlowState
import com.kg.merapaisa.ui.ImportFlowState
import com.kg.merapaisa.ui.MoveDebtFlowState
import com.kg.merapaisa.ui.RestoreSource
import com.kg.merapaisa.ui.ShareFlowState
import com.kg.merapaisa.ui.UpdateFlowState

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

    // -- history ---------------------------------------------------------------------------

    private const val HOUR = 60 * 60 * 1000L
    private const val DAY = 24 * HOUR

    /**
     * Asha's entries, newest first, reaching back far enough for every kind of day heading: today,
     * yesterday, a date this year and one from last year. A long note, an entry with no note (which
     * says its direction in words instead), paise, and a lakh figure for the column to hold.
     *
     * Relative to now, because the headings are. A fixed date would read "12 March 2024" forever
     * and never show what today looks like.
     */
    val ashaHistory: List<Transaction>
        get() {
            val now = System.currentTimeMillis()
            return listOf(
                Transaction(id = 1, personId = 1, amountMinor = 1_200_50, timestamp = now - HOUR, note = LONG_NOTE),
                Transaction(id = 2, personId = 1, amountMinor = -340_00, timestamp = now - 2 * HOUR),
                Transaction(id = 3, personId = 1, amountMinor = 500_00, timestamp = now - DAY, note = "Cab to the airport"),
                Transaction(id = 4, personId = 1, amountMinor = -2_000_00, timestamp = now - 12 * DAY, note = "Paid back"),
                Transaction(id = 5, personId = 1, amountMinor = 12_34_567_50, timestamp = now - 400 * DAY, note = "Deposit for the flat")
            )
        }

    // -- backup ----------------------------------------------------------------------------

    val backupMenu = BackupFlowState.Menu(
        folderName = "Documents",
        lastRun = 1_727_500_000_000,
        lastResult = "Saved a backup. 1 older backup removed."
    )

    /**
     * Replacing from a CSV export: the case with the sharpest edge, where every group is deleted
     * and none comes back, so the warning line has to show.
     */
    val backupReview: BackupFlowState.Reviewing
        get() {
            val incoming = BackupSnapshot(
                persons = mixedPeople.map { it.person },
                transactions = List(38) { i ->
                    Transaction(
                        id = i + 1,
                        personId = (i % 7 + 1).toLong(),
                        amountMinor = i * 137_00L - 900_00,
                        timestamp = 1_700_000_000_000 + i * DAY
                    )
                },
                groups = emptyList(),
                groupMembers = emptyList(),
                expenses = emptyList(),
                expenseShares = emptyList(),
                appliedPayloads = emptyList()
            )
            return BackupFlowState.Reviewing(
                source = RestoreSource.Csv,
                incoming = incoming,
                exportedAt = null,
                appVersion = null,
                mode = RestoreMode.Replace,
                plan = RestorePlan(
                    mode = RestoreMode.Replace,
                    persons = incoming.persons,
                    transactions = incoming.transactions,
                    groups = emptyList(),
                    groupMembers = emptyList(),
                    expenses = emptyList(),
                    expenseShares = emptyList(),
                    appliedPayloads = emptyList(),
                    deletes = RestoreCounts(people = 9, transactions = 61, groups = 3, expenses = 14),
                    alreadyPresent = RestoreCounts()
                )
            )
        }

    val backupDone = BackupFlowState.Done(
        "Ledger restored",
        "Your ledger now matches the file exactly. Added 7 people, 38 entries."
    )

    // -- update links ----------------------------------------------------------------------

    /**
     * A full send from Asha compared against what this phone holds: one of every kind of
     * difference, so the whole comparison is on screen at once. The new entry starts ticked; the
     * edit and the deletion do not, because nothing about a link proves who sent it.
     */
    val importConfirming: ImportFlowState.Confirming
        get() {
            val t = 1_727_000_000_000
            val payload = SharePayload(
                payloadId = "gallery",
                senderName = "Asha",
                currency = "INR",
                entries = listOf(
                    SharedEntry(t + 5 * DAY, -500_00, "Cab to the airport", "n1"),
                    SharedEntry(t + 4 * DAY, -1_250_00, LONG_NOTE, "e1"),
                    SharedEntry(t + 2 * DAY, 300_00, "Chai and samosas", "u1"),
                    SharedEntry(t + DAY, -2_000_00, "Tickets", "u2")
                ),
                scope = ShareScope.Full,
                formatVersion = 2
            )
            val plan = ReconcilePlan(
                items = listOf(
                    ReconcileItem.New("n1", t + 5 * DAY, 500_00, "Cab to the airport"),
                    ReconcileItem.Edited(
                        uid = "e1", timestamp = t + 4 * DAY, localId = 12,
                        localAmountMinor = 1_200_00, localNote = LONG_NOTE,
                        theirAmountMinor = 1_250_00, theirNote = LONG_NOTE,
                        theirTimestamp = t + 4 * DAY
                    ),
                    ReconcileItem.DeletedBySender("d1", t + 3 * DAY, localId = 11, amountMinor = -340_00, note = "Coffee"),
                    ReconcileItem.Unchanged("u1", t + 2 * DAY, -300_00, "Chai and samosas"),
                    ReconcileItem.Unchanged("u2", t + DAY, 2_000_00, "Tickets"),
                    ReconcileItem.OnlyYours("y1", t, localId = 9, amountMinor = 75_50, note = "Auto")
                ),
                scope = ShareScope.Full,
                comparable = true
            )
            return ImportFlowState.Confirming(
                payload = payload,
                targetPersonId = asha.id,
                plan = plan,
                selected = setOf("n1")
            )
        }

    /**
     * The entries an import holds back: their opening balance after they cleared, one deleted
     * here that they still have, and one dated before a clear with no record of its uid. All
     * three start unticked; only the ordinary new entry is ticked.
     */
    val importHeldBack: ImportFlowState.Confirming
        get() {
            val t = 1_727_000_000_000
            val payload = SharePayload(
                payloadId = "gallery-held",
                senderName = "Asha",
                currency = "INR",
                entries = listOf(
                    SharedEntry(t + 6 * DAY, -900_00, "Opening balance", OPENING_UID_PREFIX + "a"),
                    SharedEntry(t + 5 * DAY, -500_00, "Cab to the airport", "n1"),
                    SharedEntry(t + 2 * DAY, 120_00, "Typo, 1200 meant", "del1"),
                    SharedEntry(t, -80_00, "Parking", "old1")
                ),
                scope = ShareScope.Full,
                formatVersion = 2
            )
            val plan = ReconcilePlan(
                items = listOf(
                    ReconcileItem.New(OPENING_UID_PREFIX + "a", t + 6 * DAY, 900_00, "Opening balance", theirOpening = true),
                    ReconcileItem.New("n1", t + 5 * DAY, 500_00, "Cab to the airport"),
                    ReconcileItem.New("del1", t + 2 * DAY, -120_00, "Typo, 1200 meant", deletedHere = true),
                    ReconcileItem.New("old1", t, 80_00, "Parking", predatesClear = true)
                ),
                scope = ShareScope.Full,
                comparable = true
            )
            return ImportFlowState.Confirming(
                payload = payload,
                targetPersonId = asha.id,
                plan = plan,
                selected = plan.defaultSelection
            )
        }

    val importDone = ImportFlowState.Done(
        ImportOutcome.Reconciled(added = 1, updated = 1, removed = 0, netMinor = 1_250_00),
        personName = "Asha"
    )

    val shareState = ShareFlowState(
        personId = asha.id,
        personName = "Asha",
        currency = "INR",
        senderName = "",
        entryCount = 14,
        netMinor = 1_200_00
    )

    // -- updates ---------------------------------------------------------------------------

    /** Long enough notes that the screen has to scroll them, which the old dialog could not. */
    val updateAvailable = UpdateFlowState.Available(
        version = "2.6.0",
        downloadUrl = "https://github.com/Parth-KG/MeraPaisa/releases/download/v2.6.0/app-release.apk",
        sizeBytes = 7_812_000,
        notes = "Every screen has been redrawn. Amounts are set in Anek Latin, lakh figures are " +
            "grouped the Indian way, and each of the six themes has colours of its own.\n\n" +
            "Deleting a group now asks first, and says what goes with it.\n\n" +
            "Settle up offers the payments the group is set to, instead of always the fewest.\n\n" +
            "Settings, backup, update links and this screen are full screens now, with their " +
            "actions at the foot.\n\nThe APK's SHA-256 is in the release notes on GitHub."
    )

    // -- moving a debt ---------------------------------------------------------------------

    val moveDebt = MoveDebtFlowState(
        fromPersonId = chaitanya.id,
        fromName = chaitanya.name,
        currency = "INR",
        availableMinor = chaitanya.balanceMinor,
        amount = "2500",
        toPersonId = asha.id
    )

    /** The mirror: you owe Bilal ₹40 and move part of it onto Asha. */
    val moveWhatYouOwe = MoveDebtFlowState(
        fromPersonId = bilal.id,
        fromName = bilal.name,
        currency = "INR",
        availableMinor = -bilal.balanceMinor,
        youOwe = true,
        amount = "25",
        toPersonId = asha.id
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
            pfpValue = com.kg.merapaisa.data.initialsOf(name),
            // A different hue each, walking the picker's palette. Leaving these at the stored
            // default made every avatar in every screenshot the same green, which is faithful to
            // an untouched ledger and useless for judging a palette of eight.
            pfpColor = com.kg.merapaisa.ui.AVATAR_HUES[(id.toInt() - 1).coerceAtLeast(0) % com.kg.merapaisa.ui.AVATAR_HUES.size],
            sortOrder = id.toInt(),
            isSettled = settled,
            currency = currency
        ),
        balanceMinor
    )
}
