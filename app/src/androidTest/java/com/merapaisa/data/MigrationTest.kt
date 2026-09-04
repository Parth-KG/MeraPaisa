package com.kg.merapaisa.data

import androidx.room.testing.MigrationTestHelper
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
            db.execSQL(
                "INSERT INTO persons (name, balance, pfpType, pfpValue, pfpColor, sortOrder, isSettled, currency) " +
                    "VALUES ('Asha', 250.5, 'initials', 'AS', '#4CAF50', 0, 0, '₹')"
            )
            db.execSQL(
                "INSERT INTO transactions (personId, amount, timestamp, note) VALUES (1, 250.5, 1700000000000, 'dinner')"
            )
        }

        // Re-open at the same version: Room validates the on-disk schema against 3.json.
        helper.runMigrationsAndValidate(TEST_DB, 3, true).use { db ->
            db.query("SELECT name, balance, currency FROM persons").use { c ->
                assertTrue("expected the seeded person row", c.moveToFirst())
                assertEquals("Asha", c.getString(0))
                assertEquals(250.5, c.getDouble(1), 0.0001)
                assertEquals("₹", c.getString(2))
            }
            db.query("SELECT COUNT(*), SUM(amount) FROM transactions WHERE personId = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
                assertEquals(250.5, c.getDouble(1), 0.0001)
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
