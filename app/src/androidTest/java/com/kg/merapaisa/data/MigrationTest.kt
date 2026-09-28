package com.kg.merapaisa.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Harness for validating [AppDatabase] schema migrations against the JSON schemas
 * exported to app/schemas. Every future schema change adds a case here.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun version3_schemaOpensAndReads() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.insertV3Person(id = 1, name = "Asha", balance = 250.5, currency = "₹")
            db.insertV3Transaction(personId = 1, amount = 250.5, timestamp = 1_700_000_000_000, note = "dinner")
        }

        // Re-open at the same version: Room validates the on-disk schema against 3.json.
        helper.runMigrationsAndValidate(TEST_DB, 3, true).use { db ->
            db.query("SELECT name, balance, currency FROM persons").use { c ->
                assertTrue("expected the seeded person row", c.moveToFirst())
                assertEquals("Asha", c.getString(0))
                assertEquals(250.5, c.getDouble(1), 0.0001)
                assertEquals("₹", c.getString(2))
            }
        }
    }

    /**
     * The migration drops the stored balance column, so its whole job is to make the summed
     * transactions equal what the column said — for people whose rows already agreed, for
     * people whose rows had drifted, and for people who never had any rows at all.
     */
    @Test
    fun migrate3To4_derivedBalanceMatchesEveryStoredBalance() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            // (a) transactions already sum to the stored balance
            db.insertV3Person(id = 1, name = "Agrees", balance = 250.50, currency = "₹")
            db.insertV3Transaction(personId = 1, amount = 100.00, timestamp = 2_000, note = "cab")
            db.insertV3Transaction(personId = 1, amount = 150.50, timestamp = 3_000, note = "dinner")

            // (b) transactions do not sum to the stored balance (pre-logging writes, conversions)
            db.insertV3Person(id = 2, name = "Drifted", balance = 900.00, currency = "$")
            db.insertV3Transaction(personId = 2, amount = 200.00, timestamp = 5_000, note = "old")
            db.insertV3Transaction(personId = 2, amount = -50.00, timestamp = 6_000, note = "refund")

            // (c) a balance with no transactions whatsoever
            db.insertV3Person(id = 3, name = "Bare", balance = -75.25, currency = "€")

            // (d) a person who is genuinely at zero and should gain nothing
            db.insertV3Person(id = 4, name = "Zero", balance = 0.0, currency = "¥")
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        db.use {
            assertEquals("Agrees should keep 250.50", 25_050L, it.derivedBalance(1))
            assertEquals("Drifted should keep 900.00", 90_000L, it.derivedBalance(2))
            assertEquals("Bare should keep -75.25", -7_525L, it.derivedBalance(3))
            assertEquals("Zero should stay at zero", 0L, it.derivedBalance(4))

            // Only the people who needed reconciling got an entry, for exactly the difference.
            assertEquals("Agrees needs no reconciling entry", 0, it.openingBalanceCount(1))
            assertEquals("Zero needs no reconciling entry", 0, it.openingBalanceCount(4))
            assertEquals(1, it.openingBalanceCount(2))
            assertEquals(1, it.openingBalanceCount(3))
            assertEquals("900.00 stored minus 150.00 logged", 75_000L, it.openingBalanceAmount(2))
            assertEquals("-75.25 stored minus nothing logged", -7_525L, it.openingBalanceAmount(3))

            // Reconciling entries sit one millisecond before the earliest real transaction,
            // so running-balance views start from the right number.
            assertEquals(4_999L, it.openingBalanceTimestamp(2))
            assertTrue(
                "a person with no transactions should be stamped at migration time",
                it.openingBalanceTimestamp(3) > 1_700_000_000_000L
            )

            // Existing rows carry over as minor units, and history is not otherwise disturbed.
            assertEquals(10_000L, it.amountOf(personId = 1, note = "cab"))
            assertEquals(15_050L, it.amountOf(personId = 1, note = "dinner"))
            assertEquals(-5_000L, it.amountOf(personId = 2, note = "refund"))
            assertEquals("Agrees keeps exactly their two rows", 2, it.transactionCount(1))

            // Symbols become ISO 4217 codes.
            assertEquals("INR", it.currencyOf(1))
            assertEquals("USD", it.currencyOf(2))
            assertEquals("EUR", it.currencyOf(3))
            assertEquals("JPY", it.currencyOf(4))
        }
    }

    /**
     * The 4 -> 5 migration adds a foreign key, which SQLite will not apply while rows point at
     * a person who no longer exists. Those rows are invisible anyway — counted by no balance,
     * shown in no history — so the migration clears them, and must not touch anything real.
     */
    @Test
    fun migrate4To5_dropsOrphansAndLeavesEveryRealBalanceAlone() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.insertV4Person(id = 1, name = "Asha", currency = "INR")
            db.insertV4Transaction(personId = 1, amountMinor = 250_50, timestamp = 2_000, note = "dinner")
            db.insertV4Transaction(personId = 1, amountMinor = -50_00, timestamp = 3_000, note = "refund")

            // Left behind by some earlier delete that never cleaned up after itself.
            db.insertV4Transaction(personId = 99, amountMinor = 900_00, timestamp = 4_000, note = "orphan")
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5).use { db ->
            assertEquals("Asha's balance must be untouched", 20_050L, db.derivedBalance(1))
            assertEquals("both of Asha's entries survive", 2, db.transactionCount(1))
            assertEquals("the orphan is gone", 0, db.transactionCount(99))
            assertEquals(
                "and nothing else was swept up with it",
                2,
                db.longOf("SELECT COUNT(*) FROM transactions").toInt()
            )
        }
    }

    /** Deleting a person now takes their history with it, rather than orphaning it. */
    @Test
    fun afterMigrating_deletingAPersonCascadesToTheirTransactions() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.insertV4Person(id = 1, name = "Asha", currency = "INR")
            db.insertV4Person(id = 2, name = "Ravi", currency = "INR")
            db.insertV4Transaction(personId = 1, amountMinor = 100_00, timestamp = 1, note = "a")
            db.insertV4Transaction(personId = 2, amountMinor = 200_00, timestamp = 2, note = "b")
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM persons WHERE id = 1")

            assertEquals(0, db.transactionCount(1))
            assertEquals("the other person is unaffected", 20_000L, db.derivedBalance(2))
        }
    }

    /**
     * 5 -> 6 introduces groups, and with them the row that represents you. Until now "You" was
     * a sentinel invented inside the split screen; it has to become a real person without
     * disturbing anybody's balance.
     */
    @Test
    fun migrate5To6_addsTheSelfPersonAndLeavesBalancesAlone() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.insertV4Person(id = 1, name = "Asha", currency = "INR")
            db.insertV4Transaction(personId = 1, amountMinor = 250_50, timestamp = 2_000, note = "dinner")
            db.insertV4Person(id = 2, name = "Ravi", currency = "USD")
            db.insertV4Transaction(personId = 2, amountMinor = -40_00, timestamp = 3_000, note = "cab")
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6).use { db ->
            assertEquals("Asha's balance must survive", 25_050L, db.derivedBalance(1))
            assertEquals("Ravi's balance must survive", -4_000L, db.derivedBalance(2))

            assertEquals("exactly one row is you", 1, db.longOf("SELECT COUNT(*) FROM persons WHERE isSelf = 1").toInt())
            assertEquals(
                "the people you actually track are unchanged",
                2,
                db.longOf("SELECT COUNT(*) FROM persons WHERE isSelf = 0").toInt()
            )

            // The new tables exist and accept a full group round-trip.
            db.execSQL("INSERT INTO expense_groups (name, currency, createdAt, archived) VALUES ('Goa', 'INR', 1, 0)")
            val selfId = db.longOf("SELECT id FROM persons WHERE isSelf = 1")
            db.execSQL("INSERT INTO group_members (groupId, personId) VALUES (1, $selfId), (1, 1)")
            db.execSQL(
                "INSERT INTO expenses (groupId, description, amountMinor, paidByPersonId, timestamp) " +
                    "VALUES (1, 'Hotel', 200_00, $selfId, 5)".replace("_", "")
            )
            db.execSQL("INSERT INTO expense_shares (expenseId, personId, shareMinor) VALUES (1, $selfId, 10000), (1, 1, 10000)")

            assertEquals(20_000L, db.longOf("SELECT SUM(shareMinor) FROM expense_shares WHERE expenseId = 1"))
            assertEquals(1, db.longOf("SELECT COUNT(*) FROM expenses WHERE groupId = 1").toInt())
        }
    }

    /** Deleting a group takes its expenses and shares with it, rather than orphaning them. */
    @Test
    fun afterMigrating_deletingAGroupCascades() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.insertV4Person(id = 1, name = "Asha", currency = "INR")
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO expense_groups (name, currency, createdAt, archived) VALUES ('Goa', 'INR', 1, 0)")
            db.execSQL("INSERT INTO group_members (groupId, personId) VALUES (1, 1)")
            db.execSQL(
                "INSERT INTO expenses (groupId, description, amountMinor, paidByPersonId, timestamp) " +
                    "VALUES (1, 'Hotel', 20000, 1, 5)"
            )
            db.execSQL("INSERT INTO expense_shares (expenseId, personId, shareMinor) VALUES (1, 1, 20000)")

            db.execSQL("DELETE FROM expense_groups WHERE id = 1")

            assertEquals(0, db.longOf("SELECT COUNT(*) FROM expenses").toInt())
            assertEquals(0, db.longOf("SELECT COUNT(*) FROM expense_shares").toInt())
            assertEquals(0, db.longOf("SELECT COUNT(*) FROM group_members").toInt())
            assertEquals("the person themselves is untouched", 1, db.longOf("SELECT COUNT(*) FROM persons WHERE id = 1").toInt())
        }
    }

    /**
     * 6 -> 7 adds the share-link tables. Nothing about anyone's balance may move: this migration
     * only gains a watermark column and a dedupe table, so the ledger it inherits must come
     * through byte for byte.
     */
    @Test
    fun migrate6To7_addsShareStateAndLeavesTheLedgerAlone() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.insertV6Person(id = 1, name = "Asha", currency = "INR")
            db.insertV4Transaction(personId = 1, amountMinor = 250_50, timestamp = 2_000, note = "dinner")
            db.insertV6Person(id = 2, name = "Ravi", currency = "USD")
            db.insertV4Transaction(personId = 2, amountMinor = -40_00, timestamp = 3_000, note = "cab")
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7).use { db ->
            assertEquals("Asha's balance must survive", 25_050L, db.derivedBalance(1))
            assertEquals("Ravi's balance must survive", -4_000L, db.derivedBalance(2))
            assertEquals("no entry may be invented", 1, db.transactionCount(1).toInt())
            assertEquals("no entry may be invented", 1, db.transactionCount(2).toInt())

            // Nothing has been shared yet, so every watermark starts at zero — which is what makes
            // the first link for an existing person offer their whole history rather than nothing.
            assertEquals(
                "an existing install has shared nothing, so every watermark must be 0",
                0,
                db.longOf("SELECT COUNT(*) FROM persons WHERE lastSharedAt != 0").toInt()
            )

            assertEquals(
                "the dedupe table should exist and be empty",
                0,
                db.longOf("SELECT COUNT(*) FROM applied_payloads").toInt()
            )
        }
    }

    /** The primary key is what makes applying a forwarded link twice impossible. */
    @Test
    fun afterMigrating_theSamePayloadIdCannotBeRecordedTwice() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.insertV6Person(id = 1, name = "Asha", currency = "INR")
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7).use { db ->
            db.execSQL(
                "INSERT INTO applied_payloads (payloadId, appliedAt, personId, senderName, entryCount, netMinor) " +
                    "VALUES ('abc123', 10, 1, 'Parth', 2, 34000)"
            )

            var rejected = false
            try {
                db.execSQL(
                    "INSERT INTO applied_payloads (payloadId, appliedAt, personId, senderName, entryCount, netMinor) " +
                        "VALUES ('abc123', 20, 1, 'Parth', 2, 34000)"
                )
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                rejected = true
            }

            assertTrue("a duplicate payloadId must be refused by the primary key", rejected)
            assertEquals(1, db.longOf("SELECT COUNT(*) FROM applied_payloads").toInt())
            assertEquals("the first application must be the one that stands", 10L, db.longOf("SELECT appliedAt FROM applied_payloads"))
        }
    }

    /**
     * Locks in the one deliberate asymmetry in this schema: `applied_payloads` has **no** foreign
     * key to `persons`, so deleting someone does not take their dedupe records with them.
     *
     * Every other child table here cascades. If this one did too, deleting a person and then
     * tapping their old link again would find no record of it and apply the entries a second
     * time. The record has to outlive the person it was about. A future tidy-up that "fixes the
     * missing foreign key" would reintroduce exactly that bug, which is why this test exists.
     */
    @Test
    fun afterMigrating_deletingAPersonKeepsTheirAppliedPayloadRecords() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.insertV6Person(id = 1, name = "Asha", currency = "INR")
            db.insertV4Transaction(personId = 1, amountMinor = 10_000, timestamp = 1_000, note = "dinner")
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL(
                "INSERT INTO applied_payloads (payloadId, appliedAt, personId, senderName, entryCount, netMinor) " +
                    "VALUES ('keepme', 10, 1, 'Parth', 1, 10000)"
            )

            db.execSQL("DELETE FROM persons WHERE id = 1")

            assertEquals("their transactions still cascade away", 0, db.transactionCount(1).toInt())
            assertEquals(
                "the dedupe record must survive the person, or a forwarded link applies twice",
                1,
                db.longOf("SELECT COUNT(*) FROM applied_payloads WHERE payloadId = 'keepme'").toInt()
            )
        }
    }

    /**
     * 7 -> 8 adds how a group settles up, and the flag that tells a repayment from a purchase.
     *
     * `simplifyDebts` must default to **on**: that is what every existing group has been doing
     * since groups shipped, and defaulting to off would silently change the plan shown for all of
     * them without anybody asking for it.
     */
    @Test
    fun migrate7To8_defaultsExistingGroupsToTheBehaviourTheyAlreadyHad() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.insertV7Person(id = 1, name = "Asha", currency = "INR")
            db.execSQL("INSERT INTO expense_groups (id, name, currency, createdAt, archived) VALUES (1, 'Goa', 'INR', 1, 0)")
            db.execSQL("INSERT INTO group_members (groupId, personId) VALUES (1, 1)")
        }

        helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8).use { db ->
            assertEquals(
                "an existing group must keep netting down to the fewest payments",
                1L,
                db.longOf("SELECT simplifyDebts FROM expense_groups WHERE id = 1")
            )
        }
    }

    /**
     * Older settlement rows are recognised from their description, which is the only signal they
     * carry. `recordTransfer` has always written exactly "Settlement".
     */
    @Test
    fun migrate7To8_backfillsSettlementsFromTheirDescription() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.insertV7Person(id = 1, name = "Asha", currency = "INR")
            db.insertV7Person(id = 2, name = "Ravi", currency = "INR")
            db.execSQL("INSERT INTO expense_groups (id, name, currency, createdAt, archived) VALUES (1, 'Goa', 'INR', 1, 0)")
            db.execSQL(
                "INSERT INTO expenses (id, groupId, description, amountMinor, paidByPersonId, timestamp) " +
                    "VALUES (1, 1, 'Hotel', 20000, 1, 5), (2, 1, 'Settlement', 5000, 2, 6)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8).use { db ->
            assertEquals(
                "a real expense stays an expense",
                0L,
                db.longOf("SELECT isSettlement FROM expenses WHERE id = 1")
            )
            assertEquals(
                "a repayment is recognised",
                1L,
                db.longOf("SELECT isSettlement FROM expenses WHERE id = 2")
            )
        }
    }

    /** The flag changes how a row is shown, never how it is counted. Balances must not move. */
    @Test
    fun migrate7To8_leavesEveryBalanceAlone() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.insertV7Person(id = 1, name = "Asha", currency = "INR")
            db.insertV4Transaction(personId = 1, amountMinor = 25_050, timestamp = 2_000, note = "dinner")
            db.execSQL("INSERT INTO expense_groups (id, name, currency, createdAt, archived) VALUES (1, 'Goa', 'INR', 1, 0)")
            db.execSQL(
                "INSERT INTO expenses (id, groupId, description, amountMinor, paidByPersonId, timestamp) " +
                    "VALUES (1, 1, 'Settlement', 5000, 1, 6)"
            )
            db.execSQL("INSERT INTO expense_shares (expenseId, personId, shareMinor) VALUES (1, 1, 5000)")
        }

        helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8).use { db ->
            assertEquals("the direct balance must survive", 25_050L, db.derivedBalance(1))
            assertEquals(
                "the settlement still counts in the group arithmetic",
                5_000L,
                db.longOf("SELECT SUM(shareMinor) FROM expense_shares WHERE expenseId = 1")
            )
        }
    }

    /**
     * 8 -> 9 gives every entry a stable name, so two phones can line their ledgers up.
     *
     * Seeded with `insertV4Transaction` on purpose: nothing has been added to `transactions`
     * between v4 and v8, so the v4 shape is still the v8 shape. That was checked against the
     * exported 8.json rather than assumed — see the warning on the helpers below, which exists
     * because exactly this assumption has been wrong twice.
     */
    @Test
    fun migrate8To9_givesEveryExistingEntryAUid() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.insertV7Person(id = 1, name = "Asha", currency = "INR")
            db.insertV4Transaction(personId = 1, amountMinor = 25_050, timestamp = 2_000, note = "dinner")
            db.insertV4Transaction(personId = 1, amountMinor = -5_000, timestamp = 3_000, note = "part payment")
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9).use { db ->
            assertEquals(
                "every row must come out with a uid",
                0L,
                db.longOf("SELECT COUNT(*) FROM transactions WHERE uid IS NULL OR uid = ''")
            )
        }
    }

    /**
     * And they must all be different. A shared uid would make two unrelated debts look like one
     * entry to reconcile, which is the one mistake this column exists to prevent.
     */
    @Test
    fun migrate8To9_givesEveryEntryADifferentUid() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.insertV7Person(id = 1, name = "Asha", currency = "INR")
            // Identical in every visible way. A uid derived from the contents would collide here.
            repeat(25) { db.insertV4Transaction(personId = 1, amountMinor = 2_000, timestamp = 1_000, note = "Chai") }
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9).use { db ->
            assertEquals(25L, db.longOf("SELECT COUNT(*) FROM transactions"))
            assertEquals(
                "25 identical entries must still get 25 different uids",
                25L,
                db.longOf("SELECT COUNT(DISTINCT uid) FROM transactions")
            )
        }
    }

    /**
     * Existing rows are nobody else's to delete.
     *
     * `fromShare` is 0 for every one of them, including entries that really did arrive by link
     * before v2.5 — there is no record of which those were. Treating them as yours is the safe
     * direction: reconcile will never offer to delete them on a sender's say-so.
     */
    @Test
    fun migrate8To9_marksEveryExistingEntryAsYours() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.insertV7Person(id = 1, name = "Asha", currency = "INR")
            db.insertV4Transaction(personId = 1, amountMinor = 1_000, timestamp = 1, note = "a")
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9).use { db ->
            assertEquals(
                0L,
                db.longOf("SELECT COUNT(*) FROM transactions WHERE fromShare != 0")
            )
        }
    }

    /** A new column changes what an entry is called, never what it is worth. */
    @Test
    fun migrate8To9_leavesEveryBalanceAlone() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.insertV7Person(id = 1, name = "Asha", currency = "INR")
            db.insertV7Person(id = 2, name = "Ravi", currency = "USD")
            db.insertV4Transaction(personId = 1, amountMinor = 25_050, timestamp = 2_000, note = "dinner")
            db.insertV4Transaction(personId = 1, amountMinor = -5_000, timestamp = 3_000, note = "part payment")
            db.insertV4Transaction(personId = 2, amountMinor = -1_234, timestamp = 4_000, note = "cab")
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9).use { db ->
            assertEquals(20_050L, db.derivedBalance(1))
            assertEquals(-1_234L, db.derivedBalance(2))
            assertEquals(2, db.transactionCount(1))
            assertEquals("notes must survive untouched", 25_050L, db.amountOf(1, "dinner"))
        }
    }

    /** An empty table is the fresh-install case, and the back-fill must not trip over it. */
    @Test
    fun migrate8To9_survivesAnEmptyLedger() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.insertV7Person(id = 1, name = "Asha", currency = "INR")
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9).use { db ->
            assertEquals(0L, db.longOf("SELECT COUNT(*) FROM transactions"))
        }
    }

    // -----------------------------------------------------------------------------------------
    // The whole chain, which is what a real upgrade actually runs
    // -----------------------------------------------------------------------------------------

    /**
     * Version 3 to 9 in one go, through every migration in the order the app registers them.
     *
     * Every other test here is a *pair*: seed at n, migrate to n+1, check. That proves each step
     * in isolation and proves nothing about the sequence, which is the only thing a real phone
     * ever runs. Somebody still on the first release opens v2.5 and executes six migrations
     * back to back against data seeded in the oldest shape — a path that, until now, had never
     * been executed anywhere.
     *
     * It is also the test that would catch a migration registered out of order, or omitted from
     * the list in [AppDatabase] while still existing as a value, which no pairwise test can see.
     */
    @Test
    fun migrate3To9_theWholeChainAsARealUpgradeRunsIt() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.insertV3Person(id = 1, name = "Asha", balance = 250.50, currency = "INR")
            db.insertV3Person(id = 2, name = "Bilal", balance = -40.25, currency = "INR")
            db.insertV3Transaction(personId = 1, amount = 300.00, timestamp = 1_000, note = "Dinner")
            db.insertV3Transaction(personId = 1, amount = -49.50, timestamp = 2_000, note = "Part payment")
            db.insertV3Transaction(personId = 2, amount = -40.25, timestamp = 3_000, note = "Cab")
        }

        helper.runMigrationsAndValidate(
            TEST_DB, 9, true,
            MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
        ).use { db ->
            // The money survives, in minor units, with the paise intact.
            assertEquals("Asha's balance must survive six migrations", 25_050L, db.derivedBalance(1))
            assertEquals(-4_025L, db.derivedBalance(2))
            assertEquals(2, db.transactionCount(1))
            assertEquals(1, db.transactionCount(2))

            // And everything v2.5 added is in place for rows that predate all of it.
            assertEquals(
                "every entry from 2023 still needs a uid",
                0L,
                db.longOf("SELECT COUNT(*) FROM transactions WHERE uid IS NULL OR uid = ''")
            )
            assertEquals(
                "all of them distinct",
                3L,
                db.longOf("SELECT COUNT(DISTINCT uid) FROM transactions")
            )
            assertEquals(
                "and none of them attributed to a sender",
                0L,
                db.longOf("SELECT COUNT(*) FROM transactions WHERE fromShare != 0")
            )
        }
    }

    /**
     * The same chain with nothing in it. An empty ledger is what most upgrades actually carry,
     * and a back-fill that assumes at least one row would fail on exactly those phones.
     */
    @Test
    fun migrate3To9_survivesAnEmptyDatabase() {
        helper.createDatabase(TEST_DB, 3).use { }

        helper.runMigrationsAndValidate(
            TEST_DB, 9, true,
            MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
        ).use { db ->
            assertEquals(0L, db.longOf("SELECT COUNT(*) FROM transactions"))
            // Exactly one person, and it is the row that is you: migration 5 -> 6 creates it, and
            // an empty ledger is precisely the case where nothing else exists to hide a mistake in
            // it. Groups need that row to exist, so "none" would be the wrong answer here.
            assertEquals(1L, db.longOf("SELECT COUNT(*) FROM persons"))
            assertEquals(1L, db.longOf("SELECT COUNT(*) FROM persons WHERE isSelf = 1"))
        }
    }

    // --- seeding helpers, written against the v3 shape ---

    private fun SupportSQLiteDatabase.insertV3Person(
        id: Long,
        name: String,
        balance: Double,
        currency: String
    ) = execSQL(
        "INSERT INTO persons (id, name, balance, pfpType, pfpValue, pfpColor, sortOrder, isSettled, currency) " +
            "VALUES ($id, '$name', $balance, 'initials', '${name.take(2)}', '#4CAF50', 0, 0, '$currency')"
    )

    private fun SupportSQLiteDatabase.insertV3Transaction(
        personId: Long,
        amount: Double,
        timestamp: Long,
        note: String
    ) = execSQL(
        "INSERT INTO transactions (personId, amount, timestamp, note) VALUES ($personId, $amount, $timestamp, '$note')"
    )

    // --- seeding helpers, written against the v4 shape ---

    /**
     * Seeds a person into a **v6** `persons` table.
     *
     * [insertV4Person] cannot be used at v6: migration 5 -> 6 added `isSelf` as INTEGER NOT NULL
     * with no default in the exported schema, so an INSERT that omits the column is rejected.
     * `migrate5To6` gets away with the v4 helper only because it seeds at v5, before the column
     * exists. Caught on the device, where the constraint is real.
     */
    /**
     * Seeds a person into a **v7** `persons` table.
     *
     * Every schema version that adds a NOT NULL column to `persons` needs its own helper, and this
     * is now the second time that has been learned the hard way: migration 5 -> 6 added `isSelf`
     * and broke the v4 helper, then 6 -> 7 added `lastSharedAt` and broke the v6 one. Neither has a
     * default in the exported schema, so an INSERT that omits the column is rejected — and the
     * failure only appears on a device, where the constraint is real.
     *
     * **If you add a NOT NULL column to `persons`, add the next helper here at the same time.**
     */
    private fun SupportSQLiteDatabase.insertV7Person(
        id: Long,
        name: String,
        currency: String,
        isSelf: Int = 0
    ) = execSQL(
        "INSERT INTO persons (id, name, pfpType, pfpValue, pfpColor, sortOrder, isSettled, currency, isSelf, lastSharedAt) " +
            "VALUES ($id, '$name', 'initials', '${name.take(2)}', '#4CAF50', 0, 0, '$currency', $isSelf, 0)"
    )

    private fun SupportSQLiteDatabase.insertV6Person(
        id: Long,
        name: String,
        currency: String,
        isSelf: Int = 0
    ) = execSQL(
        "INSERT INTO persons (id, name, pfpType, pfpValue, pfpColor, sortOrder, isSettled, currency, isSelf) " +
            "VALUES ($id, '$name', 'initials', '${name.take(2)}', '#4CAF50', 0, 0, '$currency', $isSelf)"
    )

    private fun SupportSQLiteDatabase.insertV4Person(id: Long, name: String, currency: String) =
        execSQL(
            "INSERT INTO persons (id, name, pfpType, pfpValue, pfpColor, sortOrder, isSettled, currency) " +
                "VALUES ($id, '$name', 'initials', '${name.take(2)}', '#4CAF50', 0, 0, '$currency')"
        )

    private fun SupportSQLiteDatabase.insertV4Transaction(
        personId: Long,
        amountMinor: Long,
        timestamp: Long,
        note: String
    ) = execSQL(
        "INSERT INTO transactions (personId, amountMinor, timestamp, note) " +
            "VALUES ($personId, $amountMinor, $timestamp, '$note')"
    )

    // --- assertion helpers, written against the v4 shape ---

    private fun SupportSQLiteDatabase.longOf(sql: String): Long =
        query(sql).use { c ->
            assertTrue("no row for: $sql", c.moveToFirst())
            c.getLong(0)
        }

    private fun SupportSQLiteDatabase.derivedBalance(personId: Long) =
        longOf("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE personId = $personId")

    private fun SupportSQLiteDatabase.transactionCount(personId: Long) =
        longOf("SELECT COUNT(*) FROM transactions WHERE personId = $personId").toInt()

    private fun SupportSQLiteDatabase.openingBalanceCount(personId: Long) =
        longOf("SELECT COUNT(*) FROM transactions WHERE personId = $personId AND note = 'Opening balance'").toInt()

    private fun SupportSQLiteDatabase.openingBalanceAmount(personId: Long) =
        longOf("SELECT amountMinor FROM transactions WHERE personId = $personId AND note = 'Opening balance'")

    private fun SupportSQLiteDatabase.openingBalanceTimestamp(personId: Long) =
        longOf("SELECT timestamp FROM transactions WHERE personId = $personId AND note = 'Opening balance'")

    private fun SupportSQLiteDatabase.amountOf(personId: Long, note: String) =
        longOf("SELECT amountMinor FROM transactions WHERE personId = $personId AND note = '$note'")

    private fun SupportSQLiteDatabase.currencyOf(personId: Long): String =
        query("SELECT currency FROM persons WHERE id = $personId").use { c ->
            assertTrue(c.moveToFirst())
            c.getString(0)
        }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
