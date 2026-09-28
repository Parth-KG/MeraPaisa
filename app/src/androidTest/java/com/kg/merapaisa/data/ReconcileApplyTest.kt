package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.repository.PersonRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two ledgers, two databases, one link between them — the whole round trip against real Room.
 *
 * The unit tests cover the comparison itself. What this covers is everything the comparison
 * assumes and cannot check: that a uid actually survives being written to SQLite and read back,
 * that an edit updates the row rather than inserting a second one, and that the balance after a
 * reconcile is the balance the preview promised.
 *
 * "Their phone" and "your phone" are two separate in-memory databases, which is the only honest
 * way to test this: a single database sharing row ids would pass even if uids did nothing at all.
 */
@RunWith(AndroidJUnit4::class)
class ReconcileApplyTest {

    private lateinit var theirDb: AppDatabase
    private lateinit var yourDb: AppDatabase
    private lateinit var theirs: PersonRepository
    private lateinit var yours: PersonRepository

    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        theirDb = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        yourDb = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        theirs = PersonRepository(theirDb.personDao())
        yours = PersonRepository(yourDb.personDao())
    }

    @After
    fun tearDown() {
        theirDb.close()
        yourDb.close()
    }

    /** On their phone, you are a person who owes them. */
    private suspend fun theirPersonForYou(): Long =
        theirDb.personDao().insertPerson(Person(name = "You", currency = "INR"))

    /** On your phone, they are a person you owe. */
    private suspend fun yourPersonForThem(): Long =
        yourDb.personDao().insertPerson(Person(name = "Parth", currency = "INR"))

    private suspend fun theyRecord(personId: Long, amount: Long, note: String, timestamp: Long) =
        theirDb.personDao().recordEntry(
            Transaction(personId = personId, amountMinor = amount, timestamp = timestamp, note = note)
        )

    /** Builds the link they would send, always as a full snapshot. */
    private suspend fun theirLink(personId: Long): SharePayload {
        val (payload, _) = theirs.buildPayload(personId, "Parth", fullHistory = true)!!
        // Through the wire, not around it: a uid that survives in memory but not through the codec
        // would make every one of these tests pass and the feature fail on a real link.
        return (decodePayload(encodePayload(payload)) as PayloadResult.Ok).payload
    }

    // -----------------------------------------------------------------------------------------
    // The first share
    // -----------------------------------------------------------------------------------------

    @Test
    fun aFirstImportMirrorsTheAmountsAndKeepsTheirUids() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        theyRecord(theirYou, 5_000, "Cab", 2_000)

        val yourThem = yourPersonForThem()
        val outcome = yours.importPayload(yourThem, theirLink(theirYou), now)

        assertTrue(outcome is ImportOutcome.Applied)
        assertEquals("you owe what they are owed", -39_000L, yourDb.personDao().getBalanceNow(yourThem))

        val theirUids = theirDb.personDao().getTransactionsForPersonNow(theirYou).map { it.uid }.toSet()
        val yourRows = yourDb.personDao().getTransactionsForPersonNow(yourThem)
        assertEquals("both phones must name the same debts the same way", theirUids, yourRows.map { it.uid }.toSet())
        assertTrue("and know they came from them", yourRows.all { it.fromShare })
    }

    /** Sharing again with nothing changed must find nothing to do. */
    @Test
    fun asecondIdenticalShareChangesNothing() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        val yourThem = yourPersonForThem()
        yours.importPayload(yourThem, theirLink(theirYou), now)

        val plan = yours.previewReconcile(yourThem, theirLink(theirYou), now)

        assertFalse("nothing changed, so nothing to report", plan.hasChanges)
        assertEquals(1, plan.unchanged.size)
        assertEquals(-34_000L, yourDb.personDao().getBalanceNow(yourThem))
    }

    // -----------------------------------------------------------------------------------------
    // The case the release exists for
    // -----------------------------------------------------------------------------------------

    @Test
    fun anEditOnTheirPhoneUpdatesYourRowRatherThanAddingASecondOne() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        val yourThem = yourPersonForThem()
        yours.importPayload(yourThem, theirLink(theirYou), now)

        // They correct the amount: it was 400, not 340.
        val theirRow = theirDb.personDao().getTransactionsForPersonNow(theirYou).single()
        theirDb.personDao().updateTransaction(theirRow.copy(amountMinor = 40_000))

        val link = theirLink(theirYou)
        val plan = yours.previewReconcile(yourThem, link, now)
        assertEquals("an edit, not a new entry", 1, plan.edited.size)
        assertTrue("and never accepted without being asked", plan.defaultSelection.isEmpty())

        val outcome = yours.applyReconcile(yourThem, link, setOf(plan.edited.single().uid), now)

        assertEquals(ImportOutcome.Reconciled(added = 0, updated = 1, removed = 0, netMinor = -6_000L), outcome)
        assertEquals("one entry, corrected", 1, yourDb.personDao().getTransactionsForPersonNow(yourThem).size)
        assertEquals(-40_000L, yourDb.personDao().getBalanceNow(yourThem))
    }

    @Test
    fun aDeletionOnTheirPhoneIsOfferedAndAppliedOnlyWhenChosen() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        theyRecord(theirYou, 5_000, "Cab", 2_000)
        val yourThem = yourPersonForThem()
        yours.importPayload(yourThem, theirLink(theirYou), now)

        val cab = theirDb.personDao().getTransactionsForPersonNow(theirYou).first { it.note == "Cab" }
        theirDb.personDao().deleteTransaction(cab.id)

        val link = theirLink(theirYou)
        val plan = yours.previewReconcile(yourThem, link, now)
        assertEquals(1, plan.deletedBySender.size)
        assertEquals("Cab", plan.deletedBySender.single().note)

        // Tapping through without ticking it leaves the ledger alone.
        val untouched = yours.applyReconcile(yourThem, link, emptySet(), now)
        assertEquals(ImportOutcome.Reconciled(0, 0, 0, 0L), untouched)
        assertEquals(-39_000L, yourDb.personDao().getBalanceNow(yourThem))

        // Ticking it removes exactly that entry, and the balance follows.
        val applied = yours.applyReconcile(yourThem, link, setOf(cab.uid), now)
        assertEquals(ImportOutcome.Reconciled(added = 0, updated = 0, removed = 1, netMinor = 5_000L), applied)
        assertEquals(-34_000L, yourDb.personDao().getBalanceNow(yourThem))
    }

    /**
     * The dinner you both wrote down. Their link cannot know about your copy, and this must not
     * offer to delete it — it is yours, and they have simply never seen it.
     */
    @Test
    fun anEntryYouTypedYourselfIsReportedAsUnseenRatherThanDeleted() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        val yourThem = yourPersonForThem()
        yours.importPayload(yourThem, theirLink(theirYou), now)

        yourDb.personDao().recordEntry(
            Transaction(personId = yourThem, amountMinor = 2_000, timestamp = 3_000, note = "Chai I paid")
        )

        val plan = yours.previewReconcile(yourThem, theirLink(theirYou), now)

        assertTrue("never a deletion", plan.deletedBySender.isEmpty())
        assertEquals(1, plan.onlyYours.size)
        assertEquals("Chai I paid", plan.onlyYours.single().note)
    }

    /** A new entry on their side after the first share is ordinary, and still just an addition. */
    @Test
    fun aLaterEntryArrivesAsANewOne() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        val yourThem = yourPersonForThem()
        yours.importPayload(yourThem, theirLink(theirYou), now)

        theyRecord(theirYou, 5_000, "Cab", 2_000)
        val link = theirLink(theirYou)
        val plan = yours.previewReconcile(yourThem, link, now)

        assertEquals(1, plan.new.size)
        assertEquals("additions are ticked by default", 1, plan.defaultSelection.size)

        yours.applyReconcile(yourThem, link, plan.defaultSelection, now)
        assertEquals(-39_000L, yourDb.personDao().getBalanceNow(yourThem))
        assertEquals(2, yourDb.personDao().getTransactionsForPersonNow(yourThem).size)
    }

    // -----------------------------------------------------------------------------------------
    // Round trip: your reply carries their uids back
    // -----------------------------------------------------------------------------------------

    /**
     * The proof that this is a two-way sync rather than two one-way pipes. Your reply contains
     * their own entry under its original uid, so their phone recognises it instead of importing a
     * duplicate of the debt they already have.
     */
    @Test
    fun sharingBackIsRecognisedRatherThanDuplicated() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        val yourThem = yourPersonForThem()
        yours.importPayload(yourThem, theirLink(theirYou), now)

        // You add your own entry, then send everything back. Positive here means they owe *you*:
        // you covered the chai, so it comes off the 340 you owe them rather than adding to it.
        yourDb.personDao().recordEntry(
            Transaction(personId = yourThem, amountMinor = 6_000, timestamp = 4_000, note = "Chai")
        )
        val (reply, _) = yours.buildPayload(yourThem, "You", fullHistory = true)!!
        val onTheirPhone = (decodePayload(encodePayload(reply)) as PayloadResult.Ok).payload

        val plan = theirs.previewReconcile(theirYou, onTheirPhone, now)

        assertEquals("their own dinner comes home unchanged", 1, plan.unchanged.size)
        assertEquals("only your chai is news", 1, plan.new.size)
        assertTrue(plan.deletedBySender.isEmpty())

        theirs.applyReconcile(theirYou, onTheirPhone, plan.defaultSelection, now)
        assertEquals("they are owed 340 less the 60 you covered", 28_000L, theirDb.personDao().getBalanceNow(theirYou))
        assertEquals(2, theirDb.personDao().getTransactionsForPersonNow(theirYou).size)
    }

    /**
     * Confirming before the comparison has loaded must not append the whole link again.
     *
     * The plan is computed asynchronously, and the import screen used to fall back to plain
     * appending whenever it had not arrived — so a quick tap on a cold start wrote a second copy
     * of every entry already in the ledger and silently doubled the debt. A null selection is the
     * "they never saw a plan" case, and it has to mean the defaults, not nothing.
     */
    @Test
    fun confirmingBeforeThePlanLoadsStillReconcilesRatherThanAppending() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        theyRecord(theirYou, 5_000, "Cab", 2_000)
        val yourThem = yourPersonForThem()
        yours.importPayload(yourThem, theirLink(theirYou), now)

        // They add one entry, then you tap through before the comparison finishes.
        theyRecord(theirYou, 1_200, "Chai", 3_000)
        val outcome = yours.applyReconcile(yourThem, theirLink(theirYou), selected = null, now = now)

        assertEquals(
            "only the genuinely new entry may land",
            ImportOutcome.Reconciled(added = 1, updated = 0, removed = 0, netMinor = -1_200L),
            outcome
        )
        assertEquals("three entries, not five", 3, yourDb.personDao().getTransactionsForPersonNow(yourThem).size)
        assertEquals(-40_200L, yourDb.personDao().getBalanceNow(yourThem))
    }

    /** And the same tap must never take an edit or a deletion nobody agreed to. */
    @Test
    fun confirmingBeforeThePlanLoadsAcceptsNoEditOrDeletion() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        theyRecord(theirYou, 5_000, "Cab", 2_000)
        val yourThem = yourPersonForThem()
        yours.importPayload(yourThem, theirLink(theirYou), now)

        val rows = theirDb.personDao().getTransactionsForPersonNow(theirYou)
        theirDb.personDao().updateTransaction(rows.first { it.note == "Dinner" }.copy(amountMinor = 99_000))
        theirDb.personDao().deleteTransaction(rows.first { it.note == "Cab" }.id)

        val outcome = yours.applyReconcile(yourThem, theirLink(theirYou), selected = null, now = now)

        assertEquals(ImportOutcome.Reconciled(0, 0, 0, 0L), outcome)
        assertEquals("nothing may be overwritten or removed", -39_000L, yourDb.personDao().getBalanceNow(yourThem))
    }

    // -----------------------------------------------------------------------------------------
    // Refusals
    // -----------------------------------------------------------------------------------------

    @Test
    fun theSameLinkStillOnlyCountsOnce() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        val yourThem = yourPersonForThem()
        val link = theirLink(theirYou)

        val first = yours.applyReconcile(yourThem, link, setOf(link.entries.single().uid), now)
        assertTrue(first is ImportOutcome.Reconciled)

        val second = yours.applyReconcile(yourThem, link, setOf(link.entries.single().uid), now)
        assertTrue("re-tapping a used link must not write it again", second is ImportOutcome.AlreadyApplied)
        assertEquals(1, yourDb.personDao().getTransactionsForPersonNow(yourThem).size)
    }

    /**
     * A link the user ticked nothing on was considered, not applied — so it can be opened again.
     * Filing it as used would leave no way to change their mind about a deletion they declined.
     */
    @Test
    fun aLinkThatWroteNothingCanBeOpenedAgain() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        val yourThem = yourPersonForThem()
        val link = theirLink(theirYou)

        yours.applyReconcile(yourThem, link, emptySet(), now)
        val again = yours.applyReconcile(yourThem, link, setOf(link.entries.single().uid), now)

        assertTrue("it was never applied, so it is not 'already applied'", again is ImportOutcome.Reconciled)
        assertEquals(-34_000L, yourDb.personDao().getBalanceNow(yourThem))
    }

    @Test
    fun aLinkInTheWrongCurrencyIsStillRefused() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        val dollars = yourDb.personDao().insertPerson(Person(name = "Parth", currency = "USD"))
        val link = theirLink(theirYou)

        val outcome = yours.applyReconcile(dollars, link, setOf(link.entries.single().uid), now)

        assertTrue(outcome is ImportOutcome.CurrencyMismatch)
        assertEquals("nothing may be written", 0, yourDb.personDao().getTransactionsForPersonNow(dollars).size)
    }

    /** Receiving anything means money moved, so a settled person comes back to the active list. */
    @Test
    fun reconcilingReopensASettledPerson() = runBlocking {
        val theirYou = theirPersonForYou()
        theyRecord(theirYou, 34_000, "Dinner", 1_000)
        val yourThem = yourPersonForThem()
        yourDb.personDao().settle(yourThem)
        assertTrue(yourDb.personDao().getPersonNow(yourThem)!!.isSettled)

        val link = theirLink(theirYou)
        yours.applyReconcile(yourThem, link, setOf(link.entries.single().uid), now)

        assertFalse(yourDb.personDao().getPersonNow(yourThem)!!.isSettled)
    }
}
