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
 * without anything failing. So the headline property here is idempotence: restoring the same
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
        expenseShares = expenseShares
            .filterNot { s -> (plan.sharesHandedBack + plan.sharesTakenBack).any { it.expenseId == s.expenseId && it.personId == s.personId } } +
            plan.sharesHandedBack + plan.expenseShares,
        appliedPayloads = appliedPayloads + plan.appliedPayloads,
        retiredUids = retiredUids + plan.retiredUids
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
    fun `retired uids follow their person to the id they get here, once`() {
        val withRetired = backup().copy(retiredUids = listOf(RetiredUid(11, "gone", RETIRED_CLEARED, 5)))
        val plan = merge(emptyLedger(), withRetired)
        val asha = plan.persons.single { it.name == "Asha" }
        assertEquals(listOf(RetiredUid(asha.id, "gone", RETIRED_CLEARED, 5)), plan.retiredUids)

        val again = merge(emptyLedger().plus(plan), withRetired)
        assertTrue("merging the same backup twice adds nothing", again.retiredUids.isEmpty())
    }

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
    // Idempotence: the property that matters
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
        // since v2.5 two separately built entries differ by their uid, so comparing the plan
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

    // -----------------------------------------------------------------------------------------
    // Photos (v3.4.0)
    // -----------------------------------------------------------------------------------------

    private val ashaPhoto = base64UrlEncode(byteArrayOf(1, 2, 3))
    private val raviPhoto = base64UrlEncode(byteArrayOf(4, 5, 6))
    private val selfPhoto = base64UrlEncode(byteArrayOf(7, 8, 9))

    private fun backupWithPhotos() = backup().copy(photos = mapOf(10L to selfPhoto, 11L to ashaPhoto, 12L to raviPhoto))

    /** Folds a plan back in the way apply does, photos included, for a second plan to run against. */
    private fun BackupSnapshot.restoredWith(plan: RestorePlan): BackupSnapshot {
        val marked = (persons + plan.persons).map { p ->
            if (p.id in plan.photos) p.copy(pfpType = "photo", pfpValue = "/files/pfp_${p.id}.jpg") else p
        }
        // A snapshot holds a photo for everyone whose file it could read; the new files can be.
        return plus(plan).copy(persons = marked, photos = photos + plan.photos)
    }

    @Test
    fun `Replace brings every photo back under the backup's own ids`() {
        val plan = planRestore(emptyLedger(), backupWithPhotos(), RestoreMode.Replace, SELF_ID)
        assertEquals(mapOf(10L to selfPhoto, 11L to ashaPhoto, 12L to raviPhoto), plan.photos)
        assertEquals(0, plan.photosForPeopleAlreadyHere)
    }

    @Test
    fun `Replace ignores a photo for someone not in the backup`() {
        val plan = planRestore(emptyLedger(), backup().copy(photos = mapOf(99L to ashaPhoto)), RestoreMode.Replace, SELF_ID)
        assertEquals(emptyMap<Long, String>(), plan.photos)
    }

    @Test
    fun `Merge gives each new person their photo under their new id`() {
        val plan = planRestore(emptyLedger(), backupWithPhotos(), RestoreMode.Merge, SELF_ID)
        val asha = plan.persons.single { it.name == "Asha" }
        val ravi = plan.persons.single { it.name == "Ravi" }
        assertEquals(ashaPhoto, plan.photos[asha.id])
        assertEquals(raviPhoto, plan.photos[ravi.id])
        assertEquals("people it adds are not counted as people already here", 0, plan.photosForPeopleAlreadyHere)
    }

    @Test
    fun `Merge never gives this phone's own row the backup's photo`() {
        val plan = planRestore(emptyLedger(), backupWithPhotos(), RestoreMode.Merge, SELF_ID)
        assertFalse(SELF_ID in plan.photos)
        assertFalse(selfPhoto in plan.photos.values)
    }

    @Test
    fun `Merge gives a photo to someone already here who has none`() {
        val here = snapshot(persons = listOf(self, Person(id = 2, name = "Asha", currency = "INR")))
        val plan = planRestore(here, backupWithPhotos(), RestoreMode.Merge, SELF_ID)
        assertEquals(ashaPhoto, plan.photos[2L])
        assertEquals(1, plan.photosForPeopleAlreadyHere)
        assertFalse("a photo to fill in is a change", plan.changesNothing)
    }

    @Test
    fun `Merge leaves someone's own photo alone`() {
        val here = snapshot(persons = listOf(self, Person(id = 2, name = "Asha", currency = "INR", pfpType = "photo", pfpValue = "/files/pfp_mine.jpg")))
            .copy(photos = mapOf(2L to base64UrlEncode(byteArrayOf(42))))
        val plan = planRestore(here, backupWithPhotos(), RestoreMode.Merge, SELF_ID)
        assertFalse(2L in plan.photos)
        assertEquals(0, plan.photosForPeopleAlreadyHere)
    }

    @Test
    fun `Merge fills in a photo whose file is gone, as after Android's own backup`() {
        // The row says photo, but the file never came across, so the snapshot has none for her.
        val here = snapshot(persons = listOf(self, Person(id = 2, name = "Asha", currency = "INR", pfpType = "photo", pfpValue = "/files/pfp_lost.jpg")))
        val plan = planRestore(here, backupWithPhotos(), RestoreMode.Merge, SELF_ID)
        assertEquals(ashaPhoto, plan.photos[2L])
        assertEquals(1, plan.photosForPeopleAlreadyHere)
    }

    @Test
    fun `the same Merge run twice gives no photo the second time`() {
        val here = snapshot(persons = listOf(self, Person(id = 2, name = "Asha", currency = "INR")))
        val first = planRestore(here, backupWithPhotos(), RestoreMode.Merge, SELF_ID)
        val second = planRestore(here.restoredWith(first), backupWithPhotos(), RestoreMode.Merge, SELF_ID)
        assertEquals(emptyMap<Long, String>(), second.photos)
        assertTrue(second.changesNothing)
    }

    @Test
    fun `a backup without photos plans none`() {
        assertEquals(emptyMap<Long, String>(), planRestore(emptyLedger(), backup(), RestoreMode.Merge, SELF_ID).photos)
        assertEquals(emptyMap<Long, String>(), planRestore(emptyLedger(), backup(), RestoreMode.Replace, SELF_ID).photos)
    }


    // -----------------------------------------------------------------------------------------
    // Payments written by moving your part of a group to your ledger (v3.7.0)
    // -----------------------------------------------------------------------------------------

    /**
     * You owed Asha and Ravi ₹100 each and moved both to your ledger: two payments by you, at the
     * same moment, for the same amount, told apart only by who each went to.
     */
    private fun backupWithTwoMovedPayments() = snapshot(
        persons = listOf(
            Person(id = 10, name = "You", isSelf = true, sortOrder = -1),
            Person(id = 11, name = "Asha", currency = "INR"),
            Person(id = 12, name = "Ravi", currency = "INR")
        ),
        transactions = listOf(
            Transaction(id = 1, personId = 11, amountMinor = -10_000, timestamp = 9_000, note = "From Goa"),
            Transaction(id = 2, personId = 12, amountMinor = -10_000, timestamp = 9_000, note = "From Goa")
        ),
        groups = listOf(Group(id = 20, name = "Goa", currency = "INR", createdAt = 500)),
        groupMembers = listOf(GroupMember(20, 10), GroupMember(20, 11), GroupMember(20, 12)),
        expenses = listOf(
            Expense(id = 30, groupId = 20, description = "Cab", amountMinor = 20_000, paidByPersonId = 11, timestamp = 600),
            Expense(id = 33, groupId = 20, description = "Boat", amountMinor = 20_000, paidByPersonId = 12, timestamp = 700),
            Expense(id = 31, groupId = 20, description = MOVED_TO_LEDGER, amountMinor = 10_000, paidByPersonId = 10, timestamp = 9_000, isSettlement = true),
            Expense(id = 32, groupId = 20, description = MOVED_TO_LEDGER, amountMinor = 10_000, paidByPersonId = 10, timestamp = 9_000, isSettlement = true)
        ),
        expenseShares = listOf(
            ExpenseShare(30, 10, 10_000), ExpenseShare(30, 11, 10_000),
            ExpenseShare(33, 10, 10_000), ExpenseShare(33, 12, 10_000),
            ExpenseShare(31, 11, 10_000),
            ExpenseShare(32, 12, 10_000)
        )
    )

    @Test
    fun `two moved payments that differ only in who they went to both come back`() {
        val plan = merge(emptyLedger(), backupWithTwoMovedPayments())

        assertEquals(4, plan.expenses.size)
        assertEquals(6, plan.expenseShares.size)
        assertEquals(0, plan.alreadyPresent.expenses)
    }

    @Test
    fun `after a merge your part of the group is still even, as it was when moved`() {
        val after = emptyLedger().plus(merge(emptyLedger(), backupWithTwoMovedPayments()))
        val goa = after.groups.single()
        val ravi = after.persons.single { it.name == "Ravi" }
        val pairs = pairsWithYou(after.expenses.filter { it.groupId == goa.id }, after.expenseShares, SELF_ID)

        assertTrue("nothing left between you and anyone in the group, got $pairs", pairs.isEmpty())
        assertEquals("Ravi's debt lives in the ledger only", -10_000L,
            after.transactions.filter { it.personId == ravi.id }.sumOf { it.amountMinor })
    }

    @Test
    fun `merging moved payments twice changes nothing the second time`() {
        val after = emptyLedger().plus(merge(emptyLedger(), backupWithTwoMovedPayments()))
        val second = merge(after, backupWithTwoMovedPayments())

        assertTrue("a second identical restore must be a no-op, got ${second.inserts}", second.changesNothing)
        assertEquals(4, second.alreadyPresent.expenses)
    }


    // -----------------------------------------------------------------------------------------
    // Two people with the same name and currency
    // -----------------------------------------------------------------------------------------

    /** Two Ashas, both in rupees, each with her own entries and both in one group expense. */
    private fun backupWithTwoAshas() = snapshot(
        persons = listOf(
            Person(id = 10, name = "You", isSelf = true, sortOrder = -1),
            Person(id = 11, name = "Asha", currency = "INR"),
            Person(id = 12, name = "Asha", currency = "INR")
        ),
        transactions = listOf(
            Transaction(id = 1, personId = 11, amountMinor = 20_000, timestamp = 1_000, note = "Dinner"),
            Transaction(id = 2, personId = 12, amountMinor = -7_000, timestamp = 2_000, note = "Cab")
        ),
        groups = listOf(Group(id = 20, name = "Goa", currency = "INR", createdAt = 500)),
        groupMembers = listOf(GroupMember(20, 10), GroupMember(20, 11), GroupMember(20, 12)),
        expenses = listOf(
            Expense(id = 30, groupId = 20, description = "Hotel", amountMinor = 30_000, paidByPersonId = 10, timestamp = 600)
        ),
        expenseShares = listOf(ExpenseShare(30, 10, 10_000), ExpenseShare(30, 11, 10_000), ExpenseShare(30, 12, 10_000))
    )

    private fun BackupSnapshot.balances() = persons.filter { !it.isSelf }.sortedBy { it.id }
        .map { p -> transactions.filter { it.personId == p.id }.sumOf { it.amountMinor } }

    @Test
    fun `merging your own backup back keeps two people of one name apart`() {
        val ledger = emptyLedger().plus(merge(emptyLedger(), backupWithTwoAshas()))
        assertEquals(listOf(20_000L, -7_000L), ledger.balances())

        val again = merge(ledger, backupWithTwoAshas())

        assertTrue("a second identical restore must be a no-op, got ${again.inserts}", again.changesNothing)
        assertEquals(2, again.alreadyPresent.people)
        assertEquals(listOf(20_000L, -7_000L), ledger.plus(again).balances())
    }

    @Test
    fun `two people of one name in the file are both added to a ledger with neither`() {
        val plan = merge(emptyLedger(), backupWithTwoAshas())

        assertEquals(2, plan.persons.count { it.name == "Asha" })
        assertEquals(3, plan.expenseShares.size)
    }

    // -----------------------------------------------------------------------------------------
    // Bringing back someone deleted since the backup
    // -----------------------------------------------------------------------------------------

    /**
     * The ledger after deleting [personId] the way the app does (`deletePersonWithHistory`): their
     * share of expenses others paid goes to the payer, and what they paid goes with them, along
     * with their entries, their place in groups and the shares of their expenses.
     */
    private fun BackupSnapshot.deleting(personId: Long): BackupSnapshot {
        val theirExpenses = expenses.filter { it.paidByPersonId == personId }.map { it.id }.toSet()
        val payerOf = expenses.associate { it.id to it.paidByPersonId }
        val kept = expenseShares.filter { it.expenseId !in theirExpenses }
        val handed = kept.filter { it.personId == personId && it.shareMinor != 0L }
            .groupBy({ payerOf.getValue(it.expenseId) to it.expenseId }, { it.shareMinor })
        val shares = kept.filter { it.personId != personId }.map { s ->
            val extra = handed[s.personId to s.expenseId]?.sum() ?: 0L
            s.copy(shareMinor = s.shareMinor + extra)
        } + handed.filterKeys { (payer, expenseId) -> kept.none { it.expenseId == expenseId && it.personId == payer } }
            .map { (k, v) -> ExpenseShare(k.second, k.first, v.sum()) }
        return copy(
            persons = persons.filter { it.id != personId },
            transactions = transactions.filter { it.personId != personId },
            groupMembers = groupMembers.filter { it.personId != personId },
            expenses = expenses.filter { it.id !in theirExpenses },
            expenseShares = shares
        )
    }

    /** Each member's position in [groupId]: what they paid, less their shares. */
    private fun BackupSnapshot.positions(groupId: Long): Map<String, Long> {
        val inGroup = expenses.filter { it.groupId == groupId }
        val ids = inGroup.map { it.id }.toSet()
        val name = persons.associate { it.id to it.name }
        return groupMembers.filter { it.groupId == groupId }.associate { m ->
            name.getValue(m.personId) to
                inGroup.filter { it.paidByPersonId == m.personId }.sumOf { it.amountMinor } -
                expenseShares.filter { it.expenseId in ids && it.personId == m.personId }.sumOf { it.shareMinor }
        }
    }

    @Test
    fun `merging back one of two people of one name, deleted since, restores her and leaves the other alone`() {
        val ledger = emptyLedger().plus(merge(emptyLedger(), backupWithTwoAshas()))
        val first = ledger.persons.filter { it.name == "Asha" }.minOf { it.id }
        val afterDelete = ledger.deleting(first)
        assertEquals(listOf(-7_000L), afterDelete.balances())

        val plan = merge(afterDelete, backupWithTwoAshas())

        assertEquals("only the deleted Asha comes back", 1, plan.persons.count { it.name == "Asha" })
        assertEquals("her one entry, and nothing of the other's again", 1, plan.transactions.size)
        assertEquals(listOf(-7_000L, 20_000L), afterDelete.plus(plan).balances())
    }

    /**
     * You paid a ₹200 dinner shared with Asha, she paid a ₹100 taxi shared with you: she owes you
     * ₹50. Deleting her hands her dinner share to you and takes the taxi with her. Merging the
     * backup back must bring both halves of her group history, not only the taxi.
     */
    private fun backupWithAshaInAGroup() = snapshot(
        persons = listOf(
            Person(id = 10, name = "You", isSelf = true, sortOrder = -1),
            Person(id = 11, name = "Asha", currency = "INR")
        ),
        groups = listOf(Group(id = 20, name = "Goa", currency = "INR", createdAt = 500)),
        groupMembers = listOf(GroupMember(20, 10), GroupMember(20, 11)),
        expenses = listOf(
            Expense(id = 30, groupId = 20, description = "Dinner", amountMinor = 20_000, paidByPersonId = 10, timestamp = 600),
            Expense(id = 31, groupId = 20, description = "Taxi", amountMinor = 10_000, paidByPersonId = 11, timestamp = 700)
        ),
        expenseShares = listOf(
            ExpenseShare(30, 10, 10_000), ExpenseShare(30, 11, 10_000),
            ExpenseShare(31, 10, 5_000), ExpenseShare(31, 11, 5_000)
        )
    )

    @Test
    fun `merging back a deleted member restores her share of what others paid`() {
        val ledger = emptyLedger().plus(merge(emptyLedger(), backupWithAshaInAGroup()))
        val goa = ledger.groups.single().id
        assertEquals(mapOf("You" to 5_000L, "Asha" to -5_000L), ledger.positions(goa))
        val asha = ledger.persons.single { it.name == "Asha" }.id
        val afterDelete = ledger.deleting(asha)

        val restored = afterDelete.plus(merge(afterDelete, backupWithAshaInAGroup()))

        assertEquals(mapOf("You" to 5_000L, "Asha" to -5_000L), restored.positions(goa))
        assertEquals("both expenses there once each", 2, restored.expenses.size)
        val again = merge(restored, backupWithAshaInAGroup())
        assertTrue("a second identical restore must be a no-op, got ${again.inserts}", again.changesNothing)
    }

    /**
     * As above, but Asha was added again by name before the backup was merged, so she is matched
     * rather than added. Her share of the dinner you paid still has to come back, or the taxi she
     * paid returns alone and the positions run the other way.
     */
    @Test
    fun `merging back a deleted member added again by name restores her share of what others paid`() {
        val ledger = emptyLedger().plus(merge(emptyLedger(), backupWithAshaInAGroup()))
        val goa = ledger.groups.single().id
        val asha = ledger.persons.single { it.name == "Asha" }.id
        val afterDelete = ledger.deleting(asha)
        val readded = afterDelete.copy(persons = afterDelete.persons + Person(id = 99, name = "Asha", currency = "INR"))

        val plan = merge(readded, backupWithAshaInAGroup())
        val restored = readded.plus(plan)

        assertEquals("the Asha added again is the one matched", 0, plan.persons.count { !it.isSelf })
        assertEquals(mapOf("You" to 5_000L, "Asha" to -5_000L), restored.positions(goa))
        assertEquals("both expenses there once each", 2, restored.expenses.size)
        val again = merge(restored, backupWithAshaInAGroup())
        assertTrue("a second identical restore must be a no-op, got ${again.inserts}", again.changesNothing)
    }

    /** A group renamed since the backup is still that group, so Merge adds nothing to it twice. */
    @Test
    fun `merging after renaming a group finds the group, and copies none of it`() {
        val ledger = emptyLedger().plus(merge(emptyLedger(), backupWithAshaInAGroup()))
        val goa = ledger.groups.single()
        val renamed = ledger.copy(groups = listOf(goa.copy(name = "Goa 2026")))

        val plan = merge(renamed, backupWithAshaInAGroup())

        assertTrue("nothing should be added, got ${plan.inserts}", plan.changesNothing)
        assertEquals(mapOf("You" to 5_000L, "Asha" to -5_000L), renamed.plus(plan).positions(goa.id))
    }

    /** Two groups made at the same moment in one currency are told apart by name. */
    @Test
    fun `groups made at the same moment are still told apart by name`() {
        val here = emptyLedger().copy(
            groups = listOf(
                Group(id = 5, name = "Goa", currency = "INR", createdAt = 500),
                Group(id = 6, name = "Manali", currency = "INR", createdAt = 500)
            )
        )
        val file = snapshot(
            persons = listOf(Person(id = 10, name = "You", isSelf = true, sortOrder = -1)),
            groups = listOf(Group(id = 21, name = "Manali", currency = "INR", createdAt = 500)),
            expenses = listOf(Expense(id = 40, groupId = 21, description = "Tea", amountMinor = 1_000, paidByPersonId = 10, timestamp = 600)),
            expenseShares = listOf(ExpenseShare(40, 10, 1_000))
        )

        val plan = merge(here, file)

        assertEquals(0, plan.groups.size)
        assertEquals(6L, plan.expenses.single().groupId)
    }
}
