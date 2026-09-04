package com.kg.merapaisa.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The exported schema JSON under app/schemas is the baseline that every migration is
 * validated against. If it is deleted, or a schema change lands without a new version
 * file, migration tests silently lose their reference point — so guard it here.
 */
class ExportedSchemaTest {

    @Test
    fun version3SchemaIsCommittedUnchanged() {
        val json = readSchema(3)
        assertTrue("schema 3.json does not declare version 3", json.contains("\"version\": 3"))
        assertTrue(
            "schema 3 identity hash changed — v3 is frozen history, do not regenerate it",
            json.contains("\"identityHash\": \"7832b63c7294206c6a0df305a2bf54dc\"")
        )
        assertTrue("v3 persons should still carry the old balance column", json.contains("\"columnName\": \"balance\""))
    }

    @Test
    fun version4SchemaIsCommittedAndHasTheMinorUnitLedger() {
        val json = readSchema(4)
        assertTrue("schema 4.json does not declare version 4", json.contains("\"version\": 4"))
        assertTrue(
            "schema 4 identity hash changed — regenerate and commit a new version instead",
            json.contains("\"identityHash\": \"b59b0ce14101d0a8a004f49a30bbfe7c\"")
        )
        assertTrue("transactions should store amountMinor", json.contains("\"columnName\": \"amountMinor\""))
        assertTrue("amountMinor should be an INTEGER column", json.contains("\"affinity\": \"INTEGER\""))
        assertTrue(
            "persons should no longer carry a stored balance",
            !json.contains("\"columnName\": \"balance\"")
        )
    }

    private fun readSchema(version: Int): String {
        val relative = "schemas/com.kg.merapaisa.data.AppDatabase/$version.json"
        // Unit tests run with the module dir as working dir, but tolerate the repo root too.
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: throw AssertionError(
                "Exported Room schema $version.json not found. Looked in: " +
                    candidates.joinToString { it.absolutePath }
            )
    }
}
