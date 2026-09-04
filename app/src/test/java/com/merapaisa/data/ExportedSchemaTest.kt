package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
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
    fun version3SchemaIsCommittedAndDescribesTheLedgerTables() {
        val json = schemaFile(3).readText()

        assertEquals(
            "schema 3.json does not declare version 3",
            true,
            json.contains("\"version\": 3")
        )
        assertTrue(
            "schema 3.json identity hash changed — regenerate and commit a new version instead",
            json.contains("\"identityHash\": \"$VERSION_3_IDENTITY_HASH\"")
        )
        assertTrue("persons table missing from schema 3", json.contains("\"tableName\": \"persons\""))
        assertTrue("transactions table missing from schema 3", json.contains("\"tableName\": \"transactions\""))
    }

    private fun schemaFile(version: Int): File {
        val relative = "schemas/com.kg.merapaisa.data.AppDatabase/$version.json"
        // Unit tests run with the module dir as working dir, but tolerate the repo root too.
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError(
                "Exported Room schema $version.json not found. Looked in: " +
                    candidates.joinToString { it.absolutePath }
            )
    }

    private companion object {
        const val VERSION_3_IDENTITY_HASH = "7832b63c7294206c6a0df305a2bf54dc"
    }
}
