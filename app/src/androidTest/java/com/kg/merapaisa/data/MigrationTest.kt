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
