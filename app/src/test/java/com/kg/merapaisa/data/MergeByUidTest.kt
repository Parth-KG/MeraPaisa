package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Merge matches entries by uid (v3.7.1), and finds people by the entries they share before their
 * names. Each fixture names its uids, as a real backup file does: a file read twice gives the same.
 */
class MergeByUidTest {

    private val selfId = 1L
    private val you = Person(id = selfId, name = "You", isSelf = true, sortOrder = -1)

    private fun t(id: Int, person: Long, amount: Long, time: Long, uid: String, note: String = "") =
        Transaction(id = id, personId = person, amountMinor = amount, timestamp = time, note = note, uid = uid)

    private fun cleared(person: Long, uid: String) = RetiredUid(person, uid, RETIRED_CLEARED, 9_000)

    private fun snap(
        persons: List<Person>,
        transactions: List<Transaction>,
        retired: List<RetiredUid> = emptyList(),
        minted: Set<String> = emptySet()
    ) = BackupSnapshot(
        persons = listOf(you) + persons,
        transactions = transactions,
        groups = emptyList(),
        groupMembers = emptyList(),
        expenses = emptyList(),
        expenseShares = emptyList(),
        appliedPayloads = emptyList(),
        retiredUids = retired,
        mintedUids = minted
    )

    private fun merge(here: BackupSnapshot, file: BackupSnapshot) = planRestore(here, file, RestoreMode.Merge, selfId)

    private val ashaFile = Person(id = 11, name = "Asha", currency = "INR")
    private val ashaHere = Person(id = 2, name = "Asha", currency = "INR")

    /** Asha owes you 200 and 300 in the backup. */
    private fun backup() = snap(
        listOf(ashaFile),
        listOf(t(1, 11, 200_00, 1_000, "a1", "Dinner"), t(2, 11, 300_00, 2_000, "a2", "Cab"))
    )

    @Test
    fun `after Clear history, an older backup adds nothing on top of the opening balance`() {
        val here = snap(
            listOf(ashaHere),
            listOf(t(1, 2, 500_00, 9_000, OPENING_UID_PREFIX + "x", "Opening balance")),
            retired = listOf(cleared(2, "a1"), cleared(2, "a2"))
        )
        val plan = merge(here, backup())
        assertTrue(plan.transactions.isEmpty())
        assertTrue(plan.persons.isEmpty())
        assertEquals(2, plan.alreadyPresent.transactions)
    }

    @Test
    fun `an entry edited since the backup does not come back beside its edit`() {
        val here = snap(listOf(ashaHere), listOf(t(1, 2, 50_00, 1_000, "a1", "Dinner"), t(2, 2, 300_00, 2_000, "a2", "Cab")))
        assertTrue(merge(here, backup()).transactions.isEmpty())
    }

    @Test
    fun `an entry deleted by hand since the backup comes back, as Merge always said`() {
        val here = snap(
            listOf(ashaHere),
            listOf(t(1, 2, 200_00, 1_000, "a1", "Dinner")),
            retired = listOf(RetiredUid(2, "a2", RETIRED_DELETED, 9_000))
        )
        assertEquals(listOf("a2"), merge(here, backup()).transactions.map { it.uid })
    }

    @Test
    fun `a backup without uids still matches on contents`() {
        val file = snap(
            listOf(ashaFile),
            listOf(t(1, 11, 200_00, 1_000, "m1", "Dinner"), t(2, 11, 40_00, 3_000, "m2", "Chai")),
            minted = setOf("m1", "m2")
        )
        val here = snap(listOf(ashaHere), listOf(t(1, 2, 200_00, 1_000, "zz", "Dinner")))
        val plan = merge(here, file)
        assertEquals(listOf(40_00L), plan.transactions.map { it.amountMinor })
        assertTrue(plan.persons.isEmpty())
    }

    @Test
    fun `restoring a CSV and then the backup it came from adds nothing twice`() {
        // A CSV restore gave the entries new uids; the backup still has the old ones.
        val here = snap(listOf(ashaHere), listOf(t(1, 2, 200_00, 1_000, "c1", "Dinner"), t(2, 2, 300_00, 2_000, "c2", "Cab")))
        assertTrue(merge(here, backup()).transactions.isEmpty())
    }

    @Test
    fun `merging the same backup twice changes nothing the second time`() {
        val here = snap(listOf(ashaHere), listOf(t(1, 2, 200_00, 1_000, "a1", "Dinner")))
        val first = merge(here, backup())
        val after = here.copy(transactions = here.transactions + first.transactions)
        assertTrue(merge(after, backup()).changesNothing)
    }

    @Test
    fun `a person renamed since the backup comes back as herself`() {
        val here = snap(listOf(Person(id = 2, name = "Asha Kulkarni", currency = "INR")), listOf(t(1, 2, 200_00, 1_000, "a1", "Dinner")))
        val plan = merge(here, backup())
        assertTrue("no second Asha", plan.persons.isEmpty())
        assertEquals(listOf(2L), plan.transactions.map { it.personId })
        assertEquals(listOf("a2"), plan.transactions.map { it.uid })
        assertTrue(plan.secondPeople.isEmpty())
    }

    @Test
    fun `renamed onto another person's name, the entries win over the name`() {
        val here = snap(
            listOf(Person(id = 2, name = "Asha", currency = "INR"), Person(id = 3, name = "Ashu", currency = "INR")),
            listOf(t(1, 2, 70_00, 500, "b1", "Lunch"), t(2, 3, 200_00, 1_000, "a1", "Dinner"), t(3, 3, 300_00, 2_000, "a2", "Cab"))
        )
        val plan = merge(here, backup())
        assertTrue(plan.persons.isEmpty())
        assertTrue("nothing lands on the other Asha", plan.transactions.isEmpty())
    }

    @Test
    fun `someone moved to another currency since comes back as a second person, by name`() {
        val here = snap(listOf(Person(id = 2, name = "Asha", currency = "USD")), listOf(t(1, 2, 2_40, 1_000, "a1", "Dinner")))
        val plan = merge(here, backup())
        assertEquals(listOf("Asha"), plan.persons.map { it.name })
        assertEquals(listOf("Asha"), plan.secondPeople)
    }

    @Test
    fun `moved to another currency, with a new person of her old name and currency here, she still comes back as a second person`() {
        val here = snap(
            listOf(Person(id = 2, name = "Asha", currency = "USD"), Person(id = 3, name = "Asha", currency = "INR")),
            listOf(t(1, 2, 2_40, 1_000, "a1", "Dinner"), t(2, 2, 3_60, 2_000, "a2", "Cab"), t(3, 3, 50_00, 30_000, "z1", "Lunch"))
        )
        val plan = merge(here, backup())
        assertTrue("nothing lands on the rupee Asha", plan.transactions.none { it.personId == 3L })
        assertEquals(listOf("Asha"), plan.persons.map { it.name })
        assertEquals(listOf("Asha"), plan.secondPeople)
    }

    @Test
    fun `a group member moved to another currency keeps her place in the group, merged once or twice`() {
        // Goa, in rupees: a taxi Asha paid, split with you. She owes nothing, you owe her 50.
        val goa = Group(id = 5, name = "Goa", currency = "INR", createdAt = 100)
        fun groupRows(asha: Long) = Triple(
            listOf(GroupMember(5, selfId), GroupMember(5, asha)),
            listOf(Expense(1, 5, "Taxi", 100_00, asha, 3_000)),
            listOf(ExpenseShare(1, selfId, 50_00), ExpenseShare(1, asha, 50_00))
        )
        val (fileMembers, fileExpenses, fileShares) = groupRows(11)
        val file = backup().copy(groups = listOf(goa), groupMembers = fileMembers, expenses = fileExpenses, expenseShares = fileShares)
        // Since the backup, Asha moved to dollars; the group is as it was.
        val (members, expenses, shares) = groupRows(2)
        val here = snap(
            listOf(Person(id = 2, name = "Asha", currency = "USD")),
            listOf(t(1, 2, 2_40, 1_000, "a1", "Dinner"), t(2, 2, 3_60, 2_000, "a2", "Cab"))
        ).copy(groups = listOf(goa), groupMembers = members, expenses = expenses, expenseShares = shares)

        val plan = merge(here, file)
        assertEquals("her own entries come back beside her, as decided", listOf("Asha"), plan.secondPeople)
        assertTrue("no second Asha in the group", plan.groupMembers.isEmpty())
        assertTrue("the taxi is not counted twice", plan.expenses.isEmpty() && plan.expenseShares.isEmpty())

        val merged = here.copy(
            persons = here.persons + plan.persons,
            transactions = here.transactions + plan.transactions,
            groupMembers = here.groupMembers + plan.groupMembers,
            expenses = here.expenses + plan.expenses,
            expenseShares = here.expenseShares + plan.expenseShares
        )
        val again = merge(merged, file)
        assertTrue("a second Merge changes nothing", again.changesNothing)
        assertTrue(again.groupMembers.isEmpty())
    }

    @Test
    fun `an entry the other phone holds with the sign turned is not taken as hers`() {
        // An update link puts the same uid on both phones, opposite ways round. A backup from the
        // other phone does not make its "You" into the person here.
        val here = snap(listOf(ashaHere), listOf(t(1, 2, 200_00, 1_000, "a1", "Dinner")))
        val other = snap(listOf(Person(id = 11, name = "Parth", currency = "INR")), listOf(t(1, 11, -200_00, 1_000, "a1", "Dinner")))
        val plan = merge(here, other)
        assertEquals(listOf("Parth"), plan.persons.map { it.name })
    }

    /** The backup was made after a Clear history: one opening balance, the old uids retired. */
    private fun clearedBackup() = snap(
        listOf(ashaFile),
        listOf(t(1, 11, 500_00, 9_000, OPENING_UID_PREFIX + "o1", "Opening balance"), t(2, 11, 10_00, 9_500, "n1", "Tea")),
        retired = listOf(cleared(11, "a1"), cleared(11, "a2"))
    )

    @Test
    fun `a cleared backup onto a phone that still has every old entry leaves the opening balance out`() {
        val here = snap(listOf(ashaHere), listOf(t(1, 2, 200_00, 1_000, "a1", "Dinner"), t(2, 2, 300_00, 2_000, "a2", "Cab")))
        val plan = merge(here, clearedBackup())
        assertEquals("only what came after the clear", listOf("n1"), plan.transactions.map { it.uid })
        assertTrue("this phone never cleared", plan.retiredUids.isEmpty())
        val after = here.copy(transactions = here.transactions + plan.transactions)
        assertEquals(510_00L, after.transactions.sumOf { it.amountMinor })
    }

    @Test
    fun `with only some of the old entries here, the opening balance is added as before`() {
        val here = snap(listOf(ashaHere), listOf(t(1, 2, 200_00, 1_000, "a1", "Dinner")))
        val plan = merge(here, clearedBackup())
        assertEquals(setOf(OPENING_UID_PREFIX + "o1", "n1"), plan.transactions.map { it.uid }.toSet())
        assertEquals(2, plan.retiredUids.size)
    }

    @Test
    fun `a Clear history here after that Merge, then the same Merge again, adds nothing`() {
        val here = snap(listOf(ashaHere), listOf(t(1, 2, 200_00, 1_000, "a1", "Dinner"), t(2, 2, 300_00, 2_000, "a2", "Cab")))
        val first = merge(here, clearedBackup())
        val merged = here.copy(transactions = here.transactions + first.transactions, retiredUids = here.retiredUids + first.retiredUids)
        assertEquals(510_00L, merged.transactions.sumOf { it.amountMinor })
        // Clear history on this phone: every entry into one opening balance, the uids retired.
        val clearedHere = merged.copy(
            transactions = listOf(t(10, 2, 510_00, 20_000, OPENING_UID_PREFIX + "p1", "Opening balance")),
            retiredUids = merged.transactions.map { cleared(2, it.uid) }
        )
        val second = merge(clearedHere, clearedBackup())
        assertTrue("the backup's opening balance is already in this phone's", second.transactions.isEmpty())
        assertTrue(second.changesNothing)
    }

    @Test
    fun `both phones cleared the same entries, so the backup's opening balance is not added beside this one`() {
        val here = snap(
            listOf(ashaHere),
            listOf(t(1, 2, 500_00, 8_000, OPENING_UID_PREFIX + "p1", "Opening balance"), t(2, 2, 10_00, 9_500, "n1", "Tea")),
            retired = listOf(cleared(2, "a1"), cleared(2, "a2"))
        )
        val plan = merge(here, clearedBackup())
        assertTrue(plan.transactions.isEmpty())
        assertTrue(plan.retiredUids.isEmpty())
    }

    @Test
    fun `this phone cleared only some of them and lacks the rest, so the opening balance is added as before`() {
        val here = snap(
            listOf(ashaHere),
            listOf(t(1, 2, 200_00, 8_000, OPENING_UID_PREFIX + "p1", "Opening balance")),
            retired = listOf(cleared(2, "a1"))
        )
        val plan = merge(here, clearedBackup())
        assertTrue(plan.transactions.any { it.uid == OPENING_UID_PREFIX + "o1" })
    }

    @Test
    fun `a chain of two clears, onto a phone that made neither, leaves the last opening balance out`() {
        val file = snap(
            listOf(ashaFile),
            listOf(t(1, 11, 600_00, 9_900, OPENING_UID_PREFIX + "o2", "Opening balance")),
            retired = listOf(cleared(11, "a1"), cleared(11, "a2"), cleared(11, OPENING_UID_PREFIX + "o1"), cleared(11, "b1"))
        )
        val here = snap(
            listOf(ashaHere),
            listOf(t(1, 2, 200_00, 1_000, "a1"), t(2, 2, 300_00, 2_000, "a2"), t(3, 2, 100_00, 9_200, "b1"))
        )
        assertTrue(merge(here, file).transactions.isEmpty())
    }

    @Test
    fun `a chain of two clears, onto a phone that made the first, leaves the last opening balance out`() {
        val file = snap(
            listOf(ashaFile),
            listOf(t(1, 11, 600_00, 9_900, OPENING_UID_PREFIX + "o2", "Opening balance")),
            retired = listOf(cleared(11, "a1"), cleared(11, "a2"), cleared(11, OPENING_UID_PREFIX + "o1"), cleared(11, "b1"))
        )
        val here = snap(
            listOf(ashaHere),
            listOf(t(1, 2, 500_00, 9_000, OPENING_UID_PREFIX + "o1", "Opening balance"), t(2, 2, 100_00, 9_200, "b1")),
            retired = listOf(cleared(2, "a1"), cleared(2, "a2"))
        )
        val plan = merge(here, file)
        assertTrue(plan.transactions.isEmpty())
    }
}
