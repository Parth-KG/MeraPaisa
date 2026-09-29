package com.kg.merapaisa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.kg.merapaisa.repository.PersonRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The share/import round trip against a real database.
 *
 * The codec is covered by JVM tests; what needs a database is everything around it: that applying
 * a link mirrors the signs, that applying the same link twice is a no-op, that a currency mismatch
 * is refused rather than converted, and that the watermark makes the next link carry only what is
 * new. Those are the ways this feature can silently produce two ledgers that disagree.
 */
@RunWith(AndroidJUnit4::class)
class ShareImportTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PersonDao
    private lateinit var repo: PersonRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.personDao()
        repo = PersonRepository(dao)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun newPerson(name: String = "Asha", currency: String = "INR"): Long =
        dao.insertPerson(Person(name = name, currency = currency))

    private fun payload(
        id: String = "pay1",
        name: String = "Parth",
        currency: String = "INR",
        entries: List<SharedEntry> = listOf(SharedEntry(1_000L, 34_000L, "Dinner"))
    ) = SharePayload(id, name, currency, entries)

    // ---------------------------------------------------------------------------------------
    // Mirroring: the two ledgers must agree
    // ---------------------------------------------------------------------------------------

    /**
     * The defining behaviour. Parth records "Asha owes me ₹340"; on Asha's phone that same link has
     * to become "I owe Parth ₹340", the opposite sign. Importing it as sent would give two ledgers
     * that agree on the number and disagree on who pays, which is worse than not syncing at all.
     */
    @Test
    fun importingWritesTheOppositeSign() = runBlocking {
        val id = newPerson()
        val outcome = repo.importPayload(id, payload(), now = 9_000L)

        assertTrue("expected Applied, got $outcome", outcome is ImportOutcome.Applied)
        assertEquals(
            "the sender is owed 34000, so the importer must owe 34000",
            -34_000L,
            dao.getBalanceNow(id)
        )
    }

    @Test
    fun importingPreservesTimestampsAndNotes() = runBlocking {
        val id = newPerson()
        repo.importPayload(id, payload(entries = listOf(SharedEntry(1_234L, 5_000L, "Chai"))), now = 9_000L)

        val entry = dao.getTransactionsForPersonNow(id).single()
        assertEquals(1_234L, entry.timestamp)
        assertEquals("Chai", entry.note)
        assertEquals(-5_000L, entry.amountMinor)
    }

    @Test
    fun importingSeveralEntriesNetsToTheNegationOfTheirSum() = runBlocking {
        val id = newPerson()
        val entries = listOf(
            SharedEntry(1_000L, 34_000L, "Dinner"),
            SharedEntry(2_000L, -10_000L, "Part payment"),
            SharedEntry(3_000L, 500L, "Chai")
        )
        repo.importPayload(id, payload(entries = entries), now = 9_000L)

        assertEquals(3, dao.getTransactionsForPersonNow(id).size)
        assertEquals(-(34_000L - 10_000L + 500L), dao.getBalanceNow(id))
    }

    /** A settled person receiving a link is live again: same rule as recording an entry by hand. */
    @Test
    fun importingReopensASettledPerson() = runBlocking {
        val id = newPerson()
        dao.settle(id)
        assertTrue(dao.getPersonNow(id)!!.isSettled)

        repo.importPayload(id, payload(), now = 9_000L)

        assertFalse("money moved, so they are not settled any more", dao.getPersonNow(id)!!.isSettled)
    }

    /** An all-zero payload is legitimate (notes only) and must not unsettle anyone. */
    @Test
    fun importingOnlyZeroAmountsLeavesTheSettledFlagAlone() = runBlocking {
        val id = newPerson()
        dao.settle(id)

        repo.importPayload(id, payload(entries = listOf(SharedEntry(1L, 0L, "Note"))), now = 9_000L)

        assertTrue("nothing moved, so they stay settled", dao.getPersonNow(id)!!.isSettled)
    }

    // ---------------------------------------------------------------------------------------
    // Dedupe
    // ---------------------------------------------------------------------------------------

    /** Links get forwarded and tapped twice. The second time must change nothing at all. */
    @Test
    fun applyingTheSameLinkTwiceIsANoOp() = runBlocking {
        val id = newPerson()
        val p = payload()

        val first = repo.importPayload(id, p, now = 9_000L)
        val second = repo.importPayload(id, p, now = 12_000L)

        assertTrue(first is ImportOutcome.Applied)
        assertTrue("expected AlreadyApplied, got $second", second is ImportOutcome.AlreadyApplied)
        assertEquals(
            "the first application should be the one reported",
            9_000L,
            (second as ImportOutcome.AlreadyApplied).appliedAt
        )
        assertEquals("the balance must not move twice", -34_000L, dao.getBalanceNow(id))
        assertEquals("no duplicate entries", 1, dao.getTransactionsForPersonNow(id).size)
    }

    /** Dedupe is by payload id, not by person: the same link must not apply to someone else. */
    @Test
    fun theSameLinkCannotBeAppliedToADifferentPerson() = runBlocking {
        val asha = newPerson("Asha")
        val ravi = newPerson("Ravi")
        val p = payload()

        repo.importPayload(asha, p, now = 9_000L)
        val second = repo.importPayload(ravi, p, now = 10_000L)

        assertTrue("expected AlreadyApplied, got $second", second is ImportOutcome.AlreadyApplied)
        assertEquals("Ravi must be untouched", 0L, dao.getBalanceNow(ravi))
    }

    @Test
    fun differentLinksBothApply() = runBlocking {
        val id = newPerson()
        repo.importPayload(id, payload(id = "one"), now = 9_000L)
        repo.importPayload(id, payload(id = "two"), now = 10_000L)

        assertEquals(2, dao.getTransactionsForPersonNow(id).size)
        assertEquals(-68_000L, dao.getBalanceNow(id))
    }

    @Test
    fun theAuditTrailRecordsWhatWasApplied() = runBlocking {
        val id = newPerson()
        repo.importPayload(id, payload(name = "Parth"), now = 9_000L)

        val record = dao.getAppliedPayload("pay1")
        assertNotNull(record)
        assertEquals("Parth", record!!.senderName)
        assertEquals(1, record.entryCount)
        assertEquals("the record stores what actually landed, already mirrored", -34_000L, record.netMinor)
        assertEquals(9_000L, record.appliedAt)
    }

    // ---------------------------------------------------------------------------------------
    // Currency
    // ---------------------------------------------------------------------------------------

    /**
     * A payload's amounts are minor units with no exchange rate attached. Writing 34000 INR-shaped
     * units onto a USD person would claim $340 instead of ₹340: the same number, silently a
     * different amount of money. Refused rather than converted.
     */
    @Test
    fun aCurrencyMismatchIsRefusedAndWritesNothing() = runBlocking {
        val id = newPerson(currency = "USD")
        val outcome = repo.importPayload(id, payload(currency = "INR"), now = 9_000L)

        assertTrue("expected CurrencyMismatch, got $outcome", outcome is ImportOutcome.CurrencyMismatch)
        outcome as ImportOutcome.CurrencyMismatch
        assertEquals("INR", outcome.payloadCurrency)
        assertEquals("USD", outcome.personCurrency)

        assertEquals("nothing may be written on a refusal", 0L, dao.getBalanceNow(id))
        assertEquals(0, dao.getTransactionsForPersonNow(id).size)
        assertNull(
            "a refused link must not be recorded as applied, or retrying is impossible",
            dao.getAppliedPayload("pay1")
        )
    }

    /** Older rows stored a symbol rather than a code, so the comparison has to normalise first. */
    @Test
    fun aLegacyCurrencySymbolStillMatchesItsCode() = runBlocking {
        val id = dao.insertPerson(Person(name = "Asha", currency = "₹"))
        val outcome = repo.importPayload(id, payload(currency = "INR"), now = 9_000L)

        assertTrue("a legacy ₹ row should accept an INR payload, got $outcome", outcome is ImportOutcome.Applied)
        assertEquals(-34_000L, dao.getBalanceNow(id))
    }

    // ---------------------------------------------------------------------------------------
    // Hostile timestamps
    // ---------------------------------------------------------------------------------------

    /**
     * A link is untrusted input. An entry dated in the year 5000 would sit at the top of the
     * history forever and push the share watermark past every real entry, so every later link for
     * that person would come out empty. Clamped to now on the way in.
     */
    @Test
    fun aFutureTimestampIsClampedToNow() = runBlocking {
        val id = newPerson()
        val farFuture = 99_999_999_999_999L
        repo.importPayload(id, payload(entries = listOf(SharedEntry(farFuture, 100L, "Crafted"))), now = 5_000L)

        assertEquals(5_000L, dao.getTransactionsForPersonNow(id).single().timestamp)
    }

    @Test
    fun aPastTimestampIsLeftAlone() = runBlocking {
        val id = newPerson()
        repo.importPayload(id, payload(entries = listOf(SharedEntry(1_000L, 100L, "Real"))), now = 5_000L)

        assertEquals(1_000L, dao.getTransactionsForPersonNow(id).single().timestamp)
    }

    // ---------------------------------------------------------------------------------------
    // The outgoing side: the watermark
    // ---------------------------------------------------------------------------------------

    @Test
    fun aFirstShareOffersTheWholeHistory() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 10_000, timestamp = 1_000, note = "a"))
        dao.recordEntry(Transaction(personId = id, amountMinor = 20_000, timestamp = 2_000, note = "b"))

        assertEquals(2, repo.entriesToShare(id, fullHistory = false).size)
    }

    @Test
    fun afterSharingOnlyNewerEntriesAreOffered() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 10_000, timestamp = 1_000, note = "a"))
        dao.recordEntry(Transaction(personId = id, amountMinor = 20_000, timestamp = 2_000, note = "b"))

        val (_, upTo) = repo.buildPayload(id, "Parth", fullHistory = false)!!
        assertEquals("the watermark is the newest entry shared", 2_000L, upTo)
        repo.markShared(id, upTo)

        assertTrue("everything has been shared", repo.entriesToShare(id, fullHistory = false).isEmpty())

        dao.recordEntry(Transaction(personId = id, amountMinor = 5_000, timestamp = 3_000, note = "c"))
        val next = repo.entriesToShare(id, fullHistory = false)
        assertEquals(1, next.size)
        assertEquals("c", next.single().note)
    }

    /** The entry that ended the last payload must not be sent again. */
    @Test
    fun theWatermarkIsExclusiveOfTheEntryItPointsAt() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 10_000, timestamp = 2_000, note = "a"))
        repo.markShared(id, 2_000L)

        assertTrue(repo.entriesToShare(id, fullHistory = false).isEmpty())
    }

    /** An entry back-dated between two shares is still picked up, because the filter is on time. */
    @Test
    fun anEntryBackdatedAfterTheWatermarkIsStillOffered() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 10_000, timestamp = 1_000, note = "a"))
        repo.markShared(id, 1_000L)

        // Recorded later, but dated after the watermark.
        dao.recordEntry(Transaction(personId = id, amountMinor = 7_000, timestamp = 1_500, note = "backdated"))

        assertEquals("backdated", repo.entriesToShare(id, fullHistory = false).single().note)
    }

    /** The escape hatch for a link that never arrived. Without it a debt can become unreconcilable. */
    @Test
    fun fullHistoryIgnoresTheWatermark() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 10_000, timestamp = 1_000, note = "a"))
        dao.recordEntry(Transaction(personId = id, amountMinor = 20_000, timestamp = 2_000, note = "b"))
        repo.markShared(id, 2_000L)

        assertTrue(repo.entriesToShare(id, fullHistory = false).isEmpty())
        assertEquals(2, repo.entriesToShare(id, fullHistory = true).size)
    }

    @Test
    fun buildingAPayloadWithNothingToShareReturnsNull() = runBlocking {
        val id = newPerson()
        assertNull("there is nothing to send, so there should be no link", repo.buildPayload(id, "Parth", false))
    }

    @Test
    fun theBuiltPayloadCarriesThePersonsCurrencyAndTheSendersName() = runBlocking {
        val id = newPerson(currency = "USD")
        dao.recordEntry(Transaction(personId = id, amountMinor = 1_000, timestamp = 1_000, note = "a"))

        val (payload, _) = repo.buildPayload(id, "Parth", fullHistory = false)!!
        assertEquals("USD", payload.currency)
        assertEquals("Parth", payload.senderName)
        assertEquals(16, payload.payloadId.length)
    }

    @Test
    fun twoPayloadsNeverShareAnId() = runBlocking {
        val id = newPerson()
        dao.recordEntry(Transaction(personId = id, amountMinor = 1_000, timestamp = 1_000, note = "a"))

        val first = repo.buildPayload(id, "Parth", fullHistory = true)!!.first.payloadId
        val second = repo.buildPayload(id, "Parth", fullHistory = true)!!.first.payloadId
        assertTrue("two links built from the same entries must still differ", first != second)
    }

    // ---------------------------------------------------------------------------------------
    // Sender identity
    // ---------------------------------------------------------------------------------------

    /**
     * The self row ships named "You", which says nothing useful on the recipient's phone. The share
     * flow captures a real name and saves it here, so later links carry it without asking again.
     */
    @Test
    fun renamingSelfPersistsAndDoesNotCreateAnotherPerson() = runBlocking {
        val before = repo.personsForImport().size
        repo.renameSelf("Parth")

        assertEquals("Parth", repo.self().name)
        assertTrue("the self row stays hidden from the people list", repo.self().isSelf)
        assertEquals("renaming must not add a person", before, repo.personsForImport().size)
    }

    @Test
    fun renamingSelfToBlankIsIgnored() = runBlocking {
        repo.renameSelf("Parth")
        repo.renameSelf("   ")
        assertEquals("Parth", repo.self().name)
    }

    @Test
    fun theImportPickerExcludesYourself() = runBlocking {
        newPerson("Asha")
        repo.renameSelf("Parth")

        val names = repo.personsForImport().map { it.name }
        assertTrue(names.contains("Asha"))
        assertFalse("you cannot owe yourself", names.contains("Parth"))
    }

    // ---------------------------------------------------------------------------------------
    // End to end
    // ---------------------------------------------------------------------------------------

    /**
     * The whole feature in one test: Asha owes Parth ₹340 on Parth's phone, the link travels, and
     * Asha's phone ends up owing ₹340. The two balances must be exact negations. That is the
     * definition of the two ledgers agreeing.
     */
    @Test
    fun aFullRoundTripLeavesTheTwoLedgersAgreeing() = runBlocking {
        // Parth's phone: Asha owes him 340.00, in two entries.
        val ashaOnParthsPhone = newPerson("Asha")
        dao.recordEntry(Transaction(personId = ashaOnParthsPhone, amountMinor = 30_000, timestamp = 1_000, note = "Dinner"))
        dao.recordEntry(Transaction(personId = ashaOnParthsPhone, amountMinor = 4_000, timestamp = 2_000, note = "Auto"))
        val parthSees = dao.getBalanceNow(ashaOnParthsPhone)
        assertEquals(34_000L, parthSees)

        val (payload, upTo) = repo.buildPayload(ashaOnParthsPhone, "Parth", fullHistory = false)!!
        repo.markShared(ashaOnParthsPhone, upTo)

        // Over the wire, as a link, and back.
        val blob = extractPayloadBlob(buildShareLink(encodePayload(payload)))!!
        val decoded = decodePayload(blob) as PayloadResult.Ok

        // Asha's phone: a person representing Parth, currently at zero.
        val parthOnAshasPhone = newPerson("Parth")
        val outcome = repo.importPayload(parthOnAshasPhone, decoded.payload, now = 5_000L)

        assertTrue("expected Applied, got $outcome", outcome is ImportOutcome.Applied)
        val ashaSees = dao.getBalanceNow(parthOnAshasPhone)
        assertEquals("the two ledgers must be exact negations", -parthSees, ashaSees)
        assertEquals(-34_000L, ashaSees)

        // And the history matches line for line, which is why entries travel rather than a total.
        val imported = dao.getTransactionsForPersonNow(parthOnAshasPhone)
        assertEquals(2, imported.size)
        assertEquals(listOf("Dinner", "Auto"), imported.map { it.note })
        assertEquals(listOf(-30_000L, -4_000L), imported.map { it.amountMinor })
        assertEquals(listOf(1_000L, 2_000L), imported.map { it.timestamp })
    }
}
