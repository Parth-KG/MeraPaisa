package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comparing two ledgers that are supposed to describe the same debts.
 *
 * The two properties worth protecting, in order:
 *
 *  1. **Nothing is destroyed without being asked for.** A deletion is only ever inferred from a
 *     full payload, only ever for a row that came from that sender, and is never ticked by default.
 *     Every test that could produce a deletion checks all three.
 *  2. **A difference is never invented.** Two ledgers that agree must produce an empty plan, or
 *     the screen cries wolf and people stop reading it.
 */
class ReconcileTest {

    private val now = 2_000_000_000_000L
    private val personId = 7L

    private fun local(
        id: Int,
        uid: String,
        amount: Long,
        // Matches the default in `incoming` below. They have to agree, or every pairing in this
        // file is an edit rather than the match the test meant to set up.
        note: String = "n",
        fromShare: Boolean = false,
        timestamp: Long = 1_000L
    ) = Transaction(
        id = id, personId = personId, amountMinor = amount,
        timestamp = timestamp, note = note, uid = uid, fromShare = fromShare
    )

    /** Already mirrored, as [reconcile] requires: these are amounts as they read *here*. */
    private fun incoming(uid: String, amount: Long, note: String = "n", timestamp: Long = 1_000L) =
        SharedEntry(timestamp, amount, note, uid)

    private fun plan(
        incoming: List<SharedEntry>,
        local: List<Transaction>,
        scope: ShareScope = ShareScope.Full
    ) = reconcile(personId, incoming, local, scope, comparable = true, now = now)

    // -----------------------------------------------------------------------------------------
    // The ordinary cases
    // -----------------------------------------------------------------------------------------

    @Test
    fun `after a clear, an unknown entry dated before it is held back`() {
        val opening = local(1, OPENING_UID_PREFIX + "x", 100_00, note = "Opening balance", timestamp = 5_000L)
        val p = plan(
            listOf(incoming("old", 100_00, timestamp = 1_000L), incoming("fresh", 50_00, timestamp = 9_000L)),
            listOf(opening)
        )

        assertTrue(p.new.single { it.uid == "old" }.predatesClear)
        assertFalse(p.new.single { it.uid == "fresh" }.predatesClear)
        assertEquals("only the entry after the clear is ticked", setOf("fresh"), p.defaultSelection)
        assertTrue("held-back entries are shown for a decision", p.needsDecision)
        assertEquals(50_00L, p.netChangeFor(p.defaultSelection))
    }

    @Test
    fun `without a clear nothing is held back`() {
        val p = plan(listOf(incoming("old", 100_00, timestamp = 1_000L)), listOf(local(1, "mine", 20_00, timestamp = 5_000L)))
        assertEquals(setOf("old"), p.defaultSelection)
    }

    @Test
    fun `an entry this phone has never seen is new`() {
        val p = plan(listOf(incoming("a", -34_000)), emptyList())

        assertEquals(1, p.new.size)
        assertEquals(-34_000L, p.new.single().amountMinor)
        assertTrue("new entries are ticked by default", "a" in p.defaultSelection)
    }

    @Test
    fun `matching entries are reported as unchanged and change nothing`() {
        val p = plan(
            incoming = listOf(incoming("a", -34_000, "Dinner")),
            local = listOf(local(1, "a", -34_000, "Dinner", fromShare = true))
        )

        assertEquals(1, p.unchanged.size)
        assertTrue(p.edited.isEmpty())
        assertFalse("nothing to do", p.hasChanges)
        assertTrue(p.writesFor(personId, p.defaultSelection).isEmpty)
    }

    @Test
    fun `a changed amount is an edit, carrying both versions`() {
        val p = plan(
            incoming = listOf(incoming("a", -40_000, "Dinner")),
            local = listOf(local(1, "a", -34_000, "Dinner", fromShare = true))
        )

        val edit = p.edited.single()
        assertEquals(-34_000L, edit.localAmountMinor)
        assertEquals(-40_000L, edit.theirAmountMinor)
        assertTrue(edit.amountDiffers)
        assertFalse(edit.noteDiffers)
    }

    @Test
    fun `a changed note alone is still an edit`() {
        val p = plan(
            incoming = listOf(incoming("a", -34_000, "Dinner and cab")),
            local = listOf(local(1, "a", -34_000, "Dinner", fromShare = true))
        )

        assertTrue(p.edited.single().noteDiffers)
        assertFalse(p.edited.single().amountDiffers)
    }

    /** The case the whole release exists for: an edit must not import as a second entry. */
    @Test
    fun `an edited entry is not duplicated`() {
        val p = plan(
            incoming = listOf(incoming("a", -40_000)),
            local = listOf(local(1, "a", -34_000, fromShare = true))
        )
        val write = p.writesFor(personId, setOf("a"))

        assertTrue("an edit must never insert", write.inserts.isEmpty())
        assertEquals(1, write.updates.size)
        assertEquals("it must update the row that is already there", 1, write.updates.single().id)
        assertEquals(-40_000L, write.updates.single().amountMinor)
    }

    // -----------------------------------------------------------------------------------------
    // Deletion: the part that can destroy something
    // -----------------------------------------------------------------------------------------

    @Test
    fun `an entry of theirs missing from a full payload was deleted by them`() {
        val p = plan(
            incoming = listOf(incoming("a", -10_000)),
            local = listOf(local(1, "a", -10_000, fromShare = true), local(2, "b", -5_000, fromShare = true))
        )

        assertEquals(1, p.deletedBySender.size)
        assertEquals("b", p.deletedBySender.single().uid)
    }

    /**
     * The single most dangerous confusion in this file. An incremental link says nothing about
     * what it omits. Everything older than the sender's watermark is missing from it by design.
     * Reading that as deletion would wipe most of a ledger the first time anyone sent an update.
     */
    @Test
    fun `an incremental payload can never delete anything`() {
        val p = plan(
            incoming = listOf(incoming("a", -10_000)),
            local = listOf(local(1, "a", -10_000, fromShare = true), local(2, "b", -5_000, fromShare = true)),
            scope = ShareScope.Incremental
        )

        assertTrue("absence proves nothing here", p.deletedBySender.isEmpty())
        assertTrue("and it is not somebody's news either", p.onlyYours.isEmpty())
        assertFalse(p.needsDecision)
    }

    /** They never had it, so they cannot have deleted it. It is yours, and it stays. */
    @Test
    fun `an entry you typed yourself is never a deletion`() {
        val p = plan(
            incoming = listOf(incoming("a", -10_000)),
            local = listOf(local(1, "a", -10_000, fromShare = true), local(2, "mine", -5_000, fromShare = false))
        )

        assertTrue(p.deletedBySender.isEmpty())
        assertEquals(1, p.onlyYours.size)
        assertEquals("mine", p.onlyYours.single().uid)
    }

    @Test
    fun `nothing that removes or rewrites an entry is ticked by default`() {
        val p = plan(
            incoming = listOf(incoming("a", -40_000), incoming("new", -1_000)),
            local = listOf(local(1, "a", -34_000, fromShare = true), local(2, "gone", -5_000, fromShare = true))
        )

        assertEquals("only the genuinely new entry", setOf("new"), p.defaultSelection)
        val write = p.writesFor(personId, p.defaultSelection)
        assertTrue(write.updates.isEmpty())
        assertTrue(write.deleteIds.isEmpty())
        assertEquals(1, write.inserts.size)
    }

    @Test
    fun `an accepted deletion removes exactly that row`() {
        val p = plan(
            incoming = emptyList(),
            local = listOf(local(1, "a", -10_000, fromShare = true), local(2, "b", -5_000, fromShare = true))
        )
        val write = p.writesFor(personId, setOf("b"))

        assertEquals(listOf(2), write.deleteIds)
        assertTrue(write.inserts.isEmpty())
    }

    @Test
    fun `an entry the user leaves unticked is left completely alone`() {
        val p = plan(
            incoming = listOf(incoming("a", -40_000)),
            local = listOf(local(1, "a", -34_000, fromShare = true), local(2, "gone", -5_000, fromShare = true))
        )

        assertTrue("nothing ticked means nothing written", p.writesFor(personId, emptySet()).isEmpty)
    }

    /** Informational only: there is deliberately no action that touches your own unshared entry. */
    @Test
    fun `an entry they have not seen is never written to`() {
        val p = plan(
            incoming = emptyList(),
            local = listOf(local(1, "mine", -5_000, fromShare = false))
        )

        assertEquals(1, p.onlyYours.size)
        // Even if the uid is somehow ticked, it produces nothing.
        assertTrue(p.writesFor(personId, setOf("mine")).isEmpty)
    }

    // -----------------------------------------------------------------------------------------
    // What an import writes
    // -----------------------------------------------------------------------------------------

    /** The line that makes the *next* reconcile possible. A fresh uid here breaks everything. */
    @Test
    fun `an imported entry keeps the sender's uid and is marked as theirs`() {
        val p = plan(listOf(incoming("their-uid", -10_000)), emptyList())
        val row = p.writesFor(personId, setOf("their-uid")).inserts.single()

        assertEquals("their-uid", row.uid)
        assertTrue("it came from them, so a later deletion is recognisable", row.fromShare)
        assertEquals(personId, row.personId)
    }

    /**
     * Accepting their version hands the row over to them. If it is theirs to edit, it is theirs to
     * delete, and a row still marked as yours would reconcile as "they have not seen this" forever.
     */
    @Test
    fun `accepting an edit marks the row as theirs`() {
        val p = plan(
            incoming = listOf(incoming("a", -40_000)),
            local = listOf(local(1, "a", -34_000, fromShare = false))
        )

        assertTrue(p.writesFor(personId, setOf("a")).updates.single().fromShare)
    }

    @Test
    fun `a forward dated entry is clamped to now`() {
        val p = plan(listOf(incoming("a", -1_000, timestamp = now + 999_999_999L)), emptyList())
        assertEquals(now, p.new.single().timestamp)
    }

    // -----------------------------------------------------------------------------------------
    // Version 1 links, which have no uids at all
    // -----------------------------------------------------------------------------------------

    /**
     * An old link still works. It cannot be compared, so everything in it is new, which is
     * precisely what every release before v2.5 did with every link.
     */
    @Test
    fun `an uncomparable payload appends everything`() {
        val p = reconcile(
            personId = personId,
            incoming = listOf(SharedEntry(1_000L, -10_000L, "Dinner"), SharedEntry(2_000L, -5_000L, "Cab")),
            local = listOf(local(1, "x", -10_000, fromShare = true)),
            scope = ShareScope.Incremental,
            comparable = false,
            now = now
        )

        assertFalse(p.comparable)
        assertEquals(2, p.new.size)
        assertTrue("it must not guess at matches it cannot make", p.edited.isEmpty())
        assertTrue(p.deletedBySender.isEmpty())
    }

    /** With nothing to tick, an uncomparable plan writes all of it rather than none of it. */
    @Test
    fun `an uncomparable payload writes every entry despite an empty selection`() {
        val p = reconcile(
            personId, listOf(SharedEntry(1_000L, -10_000L, "Dinner")), emptyList(),
            ShareScope.Incremental, comparable = false, now = now
        )
        val write = p.writesFor(personId, emptySet())

        assertEquals(1, write.inserts.size)
        assertTrue("and it still gets a uid, so the next one can be compared", write.inserts.single().uid.isNotEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // Awkward shapes
    // -----------------------------------------------------------------------------------------

    @Test
    fun `two ledgers that agree completely produce nothing to do`() {
        val rows = listOf(local(1, "a", -10_000, "Dinner", fromShare = true), local(2, "b", -5_000, "Cab", fromShare = true))
        val p = plan(listOf(incoming("a", -10_000, "Dinner"), incoming("b", -5_000, "Cab")), rows)

        assertFalse(p.hasChanges)
        assertFalse(p.needsDecision)
        assertEquals(2, p.unchanged.size)
    }

    @Test
    fun `an empty full payload against an empty ledger is empty`() {
        val p = plan(emptyList(), emptyList())
        assertTrue(p.items.isEmpty())
        assertFalse(p.hasChanges)
    }

    /**
     * A full payload from someone who has deleted everything. Every row of theirs is offered for
     * deletion, and every one of them is unticked, so tapping straight through changes nothing.
     */
    @Test
    fun `a payload that deletes everything still requires every tick`() {
        val rows = (1..5).map { local(it, "u$it", -1_000L * it, fromShare = true) }
        val p = plan(emptyList(), rows)

        assertEquals(5, p.deletedBySender.size)
        assertTrue(p.defaultSelection.isEmpty())
        assertTrue(p.writesFor(personId, p.defaultSelection).isEmpty)
    }

    /** A restore that ran twice can leave two rows sharing a uid. Neither may be offered for deletion. */
    @Test
    fun `a duplicated local uid is matched once and never reported missing`() {
        val p = plan(
            incoming = listOf(incoming("a", -10_000)),
            local = listOf(local(1, "a", -10_000, fromShare = true), local(2, "a", -10_000, fromShare = true))
        )

        assertEquals(1, p.unchanged.size)
        assertTrue("the duplicate is present, not missing", p.deletedBySender.isEmpty())
    }

    /** Rows predating v2.5 have a uid the sender has never heard of. They are the user's to keep. */
    @Test
    fun `a pre-v2_5 row is treated as yours rather than as deleted`() {
        val p = plan(
            incoming = listOf(incoming("a", -10_000)),
            local = listOf(local(1, "backfilled-uid", -20_000, fromShare = false))
        )

        assertTrue(p.deletedBySender.isEmpty())
        assertEquals(1, p.onlyYours.size)
    }

    @Test
    fun `decisions are listed before everything else`() {
        val p = plan(
            incoming = listOf(incoming("new", -1_000), incoming("edit", -40_000), incoming("same", -2_000)),
            local = listOf(
                local(1, "edit", -34_000, fromShare = true),
                local(2, "same", -2_000, "n", fromShare = true),
                local(3, "gone", -3_000, fromShare = true),
                local(4, "mine", -4_000, fromShare = false)
            )
        )

        assertTrue(p.items.first() is ReconcileItem.Edited)
        assertTrue(p.items.last() is ReconcileItem.Unchanged)
    }

    // -----------------------------------------------------------------------------------------
    // What the screen promises
    //
    // Found on a device: the import screen said "you will owe 412" while the default ticks
    // actually produced 402, because it summed the whole payload and ignored the tick boxes. On
    // the one screen whose job is to say what is about to happen, being out by exactly the amount
    // the user declined is the worst possible error.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `the promised change counts an edit as the difference, not the whole amount`() {
        val p = plan(
            incoming = listOf(incoming("a", -40_000)),
            local = listOf(local(1, "a", -34_000, fromShare = true))
        )

        assertEquals("the extra 60, not the whole 400", -6_000L, p.netChangeFor(setOf("a")))
        assertEquals("unticked, it changes nothing", 0L, p.netChangeFor(emptySet()))
    }

    @Test
    fun `the promised change counts a deletion as giving the amount back`() {
        val p = plan(
            incoming = emptyList(),
            local = listOf(local(1, "a", -5_000, fromShare = true))
        )

        assertEquals(5_000L, p.netChangeFor(setOf("a")))
        assertEquals(0L, p.netChangeFor(emptySet()))
    }

    /** The exact shape that was wrong on the device: an edit, a deletion and an addition together. */
    @Test
    fun `the promised change adds up across every kind of item`() {
        val p = plan(
            incoming = listOf(incoming("a", -40_000), incoming("new", -1_200)),
            local = listOf(
                local(1, "a", -34_000, fromShare = true),
                local(2, "gone", -5_000, fromShare = true),
                local(3, "mine", -900, fromShare = false)
            )
        )

        // Defaults tick the addition only.
        assertEquals(-1_200L, p.netChangeFor(p.defaultSelection))
        // Everything ticked: -6000 for the edit, +5000 for the deletion, -1200 for the addition.
        assertEquals(-2_200L, p.netChangeFor(setOf("a", "gone", "new")))
        // Your own entry is never part of it, however it is ticked.
        assertEquals(-2_200L, p.netChangeFor(setOf("a", "gone", "new", "mine")))
    }

    @Test
    fun `an uncomparable payload promises the whole of itself`() {
        val p = reconcile(
            personId, listOf(SharedEntry(1L, -10_000L, "a"), SharedEntry(2L, -5_000L, "b")),
            emptyList(), ShareScope.Incremental, comparable = false, now = now
        )

        assertEquals("nothing to tick, so all of it counts", -15_000L, p.netChangeFor(emptySet()))
    }

    /** The balance change is shown before anything is written, so it has to be right. */
    @Test
    fun `the net change counts only what was ticked`() {
        val p = plan(
            incoming = listOf(incoming("a", -10_000), incoming("b", -5_000)),
            local = emptyList()
        )

        assertEquals(-15_000L, p.writesFor(personId, setOf("a", "b")).netChangeMinor)
        assertEquals(-10_000L, p.writesFor(personId, setOf("a")).netChangeMinor)
    }
}
