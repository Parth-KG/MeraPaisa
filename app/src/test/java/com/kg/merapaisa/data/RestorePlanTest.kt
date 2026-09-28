package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a restore would do, worked out before it does any of it.
 *
 * This is the part of Phase E that can quietly corrupt a ledger: matching too loosely merges two
 * different people, matching too tightly duplicates one, and either way the balances end up wrong
 * without anything failing. So the headline property here is idempotence — restoring the same
 * backup twice must be indistinguishable from restoring it once.
 */
class RestorePlanTest {

    private val SELF_ID = 1L

    private fun snapshot(
        persons: List<Person> = emptyList(),
        transactions: List<Transaction> = emptyList(),
        groups: List<Group> = emptyList(),
        groupMembers: List<GroupMember> = emptyList(),
        expenses: List<Expense> = emptyList(),
        expenseShares: List<ExpenseShare> = emptyList(),
        appliedPayloads: List<AppliedPayload> = emptyList()
    ) = BackupSnapshot(persons, transactions, groups, groupMembers, expenses, expenseShares, appliedPayloads)

    private val self = Person(id = SELF_ID, name = "You", isSelf = true, sortOrder = -1)

    /** Everything already here, so a merge into this is the interesting case. */
    private fun emptyLedger() = snapshot(persons = listOf(self))

    /** Folds a plan back into a snapshot, so a second plan can be computed against the result. */
    private fun BackupSnapshot.plus(plan: RestorePlan) = BackupSnapshot(
        persons = persons + plan.persons,
        transactions = transactions + plan.transactions,
        groups = groups + plan.groups,
        groupMembers = groupMembers + plan.groupMembers,
        expenses = expenses + plan.expenses,
        expenseShares = expenseShares + plan.expenseShares,
        appliedPayloads = appliedPayloads + plan.appliedPayloads
    )

    private fun backup() = snapshot(
        persons = listOf(
            Person(id = 10, name = "You", isSelf = true, sortOrder = -1),
            Person(id = 11, name = "Asha", currency = "INR"),
            Person(id = 12, name = "Ravi", currency = "USD")
        ),
        transactions = listOf(
            Transaction(id = 1, personId = 11, amountMinor = 20_000, timestamp = 1_000, note = "Dinner"),
            Transaction(id = 2, personId = 11, amountMinor = 5_000, timestamp = 2_000, note = "Chai"),
            Transaction(id = 3, personId = 12, amountMinor = -4_000, timestamp = 3_000, note = "Cab")
        ),
        groups = listOf(Group(id = 20, name = "Goa", currency = "INR", createdAt = 500)),
        groupMembers = listOf(GroupMember(20, 10), GroupMember(20, 11)),
        expenses = listOf(
            Expense(id = 30, groupId = 20, description = "Hotel", amountMinor = 20_000, paidByPersonId = 10, timestamp = 600)
        ),
        expenseShares = listOf(ExpenseShare(30, 10, 10_000), ExpenseShare(30, 11, 10_000)),
        appliedPayloads = listOf(AppliedPayload("pay1", 900, 11, "Parth", 1, -5_000))
    )

    private fun merge(existing: BackupSnapshot, incoming: BackupSnapshot) =
        planRestore(existing, incoming, RestoreMode.Merge, SELF_ID)

    // -----------------------------------------------------------------------------------------
    // Merge into an empty ledger
    // -----------------------------------------------------------------------------------------

    @Test
    fun `merging into an empty ledger brings everything across`() {
        val plan = merge(emptyLedger(), backup())

        assertEquals(2, plan.inserts.people)
        assertEquals(3, plan.inserts.transactions)
        assertEquals(1, plan.inserts.groups)
        assertEquals(1, plan.inserts.expenses)
        assertEquals(2, plan.expenseShares.size)
        assertEquals(1, plan.appliedPayloads.size)
        assertTrue("merge never deletes", plan.deletes.isZero)
    }

    /** The backup's "you" and this phone's "you" are the same person, not two. */
    @Test
    fun `the self row is mapped rather than duplicated`() {
        val plan = merge(emptyLedger(), backup())

        assertFalse("no second self row", plan.persons.any { it.isSelf })
        assertTrue(
            "group membership should point at this phone's own self row",
            plan.groupMembers.any { it.personId == SELF_ID }
        )
        assertEquals(
            "the expense paid by 'you' should be attributed to the local self row",
            SELF_ID,
            plan.expenses.single().paidByPersonId
        )
    }

    // -----------------------------------------------------------------------------------------
    // Idempotence — the property that matters
    // -----------------------------------------------------------------------------------------

    @Test
    fun `restoring the same backup twice changes nothing the second time`() {
        val after = emptyLedger().plus(merge(emptyLedger(), backup()))
        val second = merge(after, backup())

        assertTrue("a second identical restore must be a no-op, got ${second.inserts}", second.changesNothing)
        assertEquals(2, second.alreadyPresent.people)
        assertEquals(3, second.alreadyPresent.transactions)
        assertEquals(1, second.alreadyPresent.groups)
        assertEquals(1, second.alreadyPresent.expenses)
    }

    /** Three times, because a bug that survives the second pass often shows on the third. */
    @Test
    fun `restoring three times is the same as restoring once`() {
        var ledger = emptyLedger()
        repeat(3) { ledger = ledger.plus(merge(ledger, backup())) }

        assertEquals(3, ledger.persons.size) // self + Asha + Ravi
        assertEquals(3, ledger.transactions.size)
        assertEquals(1, ledger.groups.size)
        assertEquals(1, ledger.expenses.size)
        assertEquals(2, ledger.expenseShares.size)
        assertEquals("Asha's balance must not have tripled", 25_000L,
            ledger.transactions.filter { it.personId == ledger.persons.first { p -> p.name == "Asha" }.id }
                .sumOf { it.amountMinor })
    }

    /**
     * Shares must follow their expense. If an expense is recognised as already present but its
     * shares are inserted again, every member of that group silently owes twice.
     */
    @Test
    fun `shares of an expense already present are not inserted again`() {
        val after = emptyLedger().plus(merge(emptyLedger(), backup()))
        val second = merge(after, backup())

        assertTrue("no orphaned shares", second.expenseShares.isEmpty())
        assertEquals(20_000L, after.expenseShares.sumOf { it.shareMinor })
    }

    /** A link applied before the backup must stay applied after it, or it can land twice. */
    @Test
    fun `an applied payload already recorded is not recorded again`() {
        val after = emptyLedger().plus(merge(emptyLedger(), backup()))
        assertTrue(merge(after, backup()).appliedPayloads.isEmpty())
        assertEquals(1, after.appliedPayloads.size)
    }

    // -----------------------------------------------------------------------------------------
    // Matching
    // -----------------------------------------------------------------------------------------

    @Test
    fun `people match on name and currency ignoring case and surrounding space`() {
        val existing = snapshot(persons = listOf(self, Person(id = 2, name = "  asha ", currency = "INR")))
        val plan = merge(existing, snapshot(persons = listOf(Person(id = 11, name = "Asha", currency = "INR"))))

        assertEquals(0, plan.inserts.people)
        assertEquals(1, plan.alreadyPresent.people)
    }

    /** The app treats these as two people, so a restore must too. */
    @Test
    fun `the same name in a different currency is a different person`() {
        val existing = snapshot(persons = listOf(self, Person(id = 2, name = "Asha", currency = "INR")))
        val plan = merge(existing, snapshot(persons = listOf(Person(id = 11, name = "Asha", currency = "USD"))))

        assertEquals(1, plan.inserts.people)
    }

    @Test
    fun `a legacy currency symbol matches its code`() {
        val existing = snapshot(persons = listOf(self, Person(id = 2, name = "Asha", currency = "₹")))
        val plan = merge(existing, snapshot(persons = listOf(Person(id = 11, name = "Asha", currency = "INR"))))

        assertEquals("a ₹ row and an INR row are the same person", 0, plan.inserts.people)
    }

    @Test
    fun `an entry differing in amount timestamp or note is a new entry`() {
        val existing = snapshot(
            persons = listOf(self, Person(id = 2, name = "Asha", currency = "INR")),
            transactions = listOf(Transaction(id = 1, personId = 2, amountMinor = 100, timestamp = 1, note = "a"))
        )
        val incoming = snapshot(
            persons = listOf(Person(id = 11, name = "Asha", currency = "INR")),
            transactions = listOf(
                Transaction(id = 1, personId = 11, amountMinor = 100, timestamp = 1, note = "a"),   // same
                Transaction(id = 2, personId = 11, amountMinor = 101, timestamp = 1, note = "a"),   // amount
                Transaction(id = 3, personId = 11, amountMinor = 100, timestamp = 2, note = "a"),   // time
                Transaction(id = 4, personId = 11, amountMinor = 100, timestamp = 1, note = "b")    // note
            )
        )
        val plan = merge(existing, incoming)
        assertEquals(3, plan.inserts.transactions)
        assertEquals(1, plan.alreadyPresent.transactions)
    }

    /** Name alone would merge two different trips that happen to share a label. */
    @Test
    fun `groups match on name currency and creation time together`() {
        val existing = snapshot(
            persons = listOf(self),
            groups = listOf(Group(id = 5, name = "Goa", currency = "INR", createdAt = 500))
        )
        val sameTrip = snapshot(groups = listOf(Group(id = 20, name = "Goa", currency = "INR", createdAt = 500)))
        val differentTrip = snapshot(groups = listOf(Group(id = 20, name = "Goa", currency = "INR", createdAt = 900)))

        assertEquals(0, merge(existing, sameTrip).inserts.groups)
        assertEquals("a second trip also called Goa is a different group", 1, merge(existing, differentTrip).inserts.groups)
    }

    // -----------------------------------------------------------------------------------------
    // Id allocation
    // -----------------------------------------------------------------------------------------

    /** A backup id must never land on an unrelated local row that happens to share it. */
    @Test
    fun `new ids are allocated above everything already in use`() {
        val existing = snapshot(
            persons = listOf(self, Person(id = 50, name = "Existing", currency = "INR")),
            transactions = listOf(Transaction(id = 77, personId = 50, amountMinor = 1, timestamp = 1, note = "x")),
            groups = listOf(Group(id = 60, name = "Old", currency = "INR", createdAt = 1)),
            expenses = listOf(Expense(id = 90, groupId = 60, description = "x", amountMinor = 1, paidByPersonId = 50, timestamp = 1))
        )
        val plan = merge(existing, backup())

        assertTrue("person ids must clear 50", plan.persons.all { it.id > 50 })
        assertTrue("transaction ids must clear 77", plan.transactions.all { it.id > 77 })
        assertTrue("group ids must clear 60", plan.groups.all { it.id > 60 })
        assertTrue("expense ids must clear 90", plan.expenses.all { it.id > 90 })
    }

    @Test
    fun `allocated ids are unique within the plan`() {
        val plan = merge(emptyLedger(), backup())
        assertEquals(plan.persons.size, plan.persons.map { it.id }.toSet().size)
        assertEquals(plan.transactions.size, plan.transactions.map { it.id }.toSet().size)
    }

    /**
     * Every row the plan inserts must point at something that will exist once it is applied, or
     * Room's foreign keys reject it partway through and leave a half-restored ledger.
     */
    @Test
    fun `the plan is referentially closed`() {
        val existing = snapshot(persons = listOf(self, Person(id = 50, name = "Existing", currency = "INR")))
        val plan = merge(existing, backup())
        val after = existing.plus(plan)

        val personIds = after.persons.map { it.id }.toSet()
        val groupIds = after.groups.map { it.id }.toSet()
        val expenseIds = after.expenses.map { it.id }.toSet()

        assertTrue(plan.transactions.all { it.personId in personIds })
        assertTrue(plan.groupMembers.all { it.groupId in groupIds && it.personId in personIds })
        assertTrue(plan.expenses.all { it.groupId in groupIds && it.paidByPersonId in personIds })
        assertTrue(plan.expenseShares.all { it.expenseId in expenseIds && it.personId in personIds })
    }

    // -----------------------------------------------------------------------------------------
    // Replace
    // -----------------------------------------------------------------------------------------

    @Test
    fun `replace counts what it would delete`() {
        val existing = snapshot(
            persons = listOf(self, Person(id = 2, name = "Gone", currency = "INR")),
            transactions = listOf(Transaction(id = 1, personId = 2, amountMinor = 1, timestamp = 1, note = "x")),
            groups = listOf(Group(id = 3, name = "Old", currency = "INR", createdAt = 1)),
            expenses = listOf(Expense(id = 4, groupId = 3, description = "x", amountMinor = 1, paidByPersonId = 2, timestamp = 1))
        )
        val plan = planRestore(existing, backup(), RestoreMode.Replace, SELF_ID)

        assertEquals("the self row is not counted as a deletion", 1, plan.deletes.people)
        assertEquals(1, plan.deletes.transactions)
        assertEquals(1, plan.deletes.groups)
        assertEquals(1, plan.deletes.expenses)
    }

    @Test
    fun `replace lays the backup down exactly as it was`() {
        // One snapshot, compared against itself. `backup()` builds a fresh one on every call, and
        // since v2.5 two separately built entries differ by their uid — so comparing the plan
        // against a second call was comparing two different backups and only ever passed by
        // accident of Transaction having nothing unique in it.
        val source = backup()
        val plan = planRestore(emptyLedger(), source, RestoreMode.Replace, SELF_ID)

        assertEquals(source.persons, plan.persons)
        assertEquals(source.transactions, plan.transactions)
        assertEquals(source.groups, plan.groups)
        assertEquals(source.expenses, plan.expenses)
        assertEquals(source.expenseShares, plan.expenseShares)
        assertTrue("replace starts from nothing, so it skips nothing", plan.alreadyPresent.isZero)
    }

    // -----------------------------------------------------------------------------------------
    // Nothing to do
    // -----------------------------------------------------------------------------------------

    @Test
    fun `an empty backup changes nothing`() {
        val plan = merge(emptyLedger(), snapshot())
        assertTrue(plan.changesNothing)
    }

    @Test
    fun `changesNothing is false when there is anything to insert`() {
        assertFalse(merge(emptyLedger(), backup()).changesNothing)
    }

    /** Replace onto a populated ledger is never a no-op, even with an empty backup. */
    @Test
    fun `replace with an empty backup still reports the deletions`() {
        val existing = snapshot(persons = listOf(self, Person(id = 2, name = "Gone", currency = "INR")))
        val plan = planRestore(existing, snapshot(), RestoreMode.Replace, SELF_ID)

        assertFalse(plan.changesNothing)
        assertEquals(1, plan.deletes.people)
    }
}
