package com.kg.merapaisa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JSON layer under the backup file. A backup is read precisely when someone has lost data and
 * is depending on it, so these tests care most about two things: that a round trip is exact, and
 * that a damaged file is refused outright rather than parsed into something plausible.
 */
class JsonTest {

    private fun roundTrip(v: JsonValue): JsonValue? = parseJson(writeJson(v))

    // -----------------------------------------------------------------------------------------
    // Round trip
    // -----------------------------------------------------------------------------------------

    @Test
    fun `round trips the scalar types`() {
        assertEquals(JsonString("hello"), roundTrip(JsonString("hello")))
        assertEquals(JsonNumber(42), roundTrip(JsonNumber(42)))
        assertEquals(JsonNumber(-42), roundTrip(JsonNumber(-42)))
        assertEquals(JsonNumber(0), roundTrip(JsonNumber(0)))
        assertEquals(JsonBoolean(true), roundTrip(JsonBoolean(true)))
        assertEquals(JsonBoolean(false), roundTrip(JsonBoolean(false)))
        assertEquals(JsonNull, roundTrip(JsonNull))
    }

    /** Money is a Long, and the extremes have to survive a backup exactly. */
    @Test
    fun `round trips the full Long range`() {
        assertEquals(JsonNumber(Long.MAX_VALUE), roundTrip(JsonNumber(Long.MAX_VALUE)))
        assertEquals(JsonNumber(Long.MIN_VALUE), roundTrip(JsonNumber(Long.MIN_VALUE)))
    }

    @Test
    fun `round trips an object preserving key order`() {
        val o = jsonObject("b" to 1L.json(), "a" to 2L.json(), "c" to 3L.json())
        val back = roundTrip(o) as JsonObject
        assertEquals(o, back)
        assertEquals(listOf("b", "a", "c"), back.fields.keys.toList())
    }

    @Test
    fun `round trips nesting`() {
        val o = jsonObject(
            "list" to jsonArray(listOf(1L.json(), 2L.json())),
            "nested" to jsonObject("inner" to jsonArray(listOf(jsonObject("deep" to true.json()))))
        )
        assertEquals(o, roundTrip(o))
    }

    @Test
    fun `round trips empty containers`() {
        assertEquals(JsonObject(emptyMap()), roundTrip(JsonObject(emptyMap())))
        assertEquals(JsonArray(emptyList()), roundTrip(JsonArray(emptyList())))
    }

    /** Notes and names are free text, and every one of these has broken a hand-rolled writer. */
    @Test
    fun `round trips strings that need escaping`() {
        val nasty = listOf(
            "quote \" inside",
            "back\\slash",
            "new\nline",
            "carriage\rreturn",
            "tab\there",
            "control\u0001char",
            "form\u000Cfeed",
            "back\bspace",
            "slash/forward",
            "",
            "   ",
            "₹ ☕ 🎉 पार्थ"
        )
        for (s in nasty) {
            assertEquals("failed on: ${s.map { it.code }}", JsonString(s), roundTrip(JsonString(s)))
        }
    }

    @Test
    fun `round trips keys that need escaping`() {
        val o = jsonObject("key \"with\" quotes" to 1L.json(), "key\nwith\nnewlines" to 2L.json())
        assertEquals(o, roundTrip(o))
    }

    // -----------------------------------------------------------------------------------------
    // Refusals
    // -----------------------------------------------------------------------------------------

    @Test
    fun `refuses junk`() {
        for (bad in listOf("", "   ", "hello", "{", "}", "[", "]", "{\"a\"}", "{\"a\":}", "[1,]", "{,}")) {
            assertNull("should have refused: $bad", parseJson(bad))
        }
    }

    /** A truncated file is what a failed or interrupted write leaves behind. */
    @Test
    fun `refuses a truncated document`() {
        val full = writeJson(jsonObject(
            "persons" to jsonArray(listOf(jsonObject("id" to 1L.json(), "name" to "Asha".json())))
        ))
        for (fraction in listOf(2, 3, 4)) {
            assertNull(
                "a document clipped to 1/$fraction should not parse",
                parseJson(full.take(full.length / fraction))
            )
        }
    }

    /**
     * Trailing content means the file is not what it claims: a concatenation, or a partial
     * overwrite of a longer previous backup, where the first half parses perfectly.
     */
    @Test
    fun `refuses trailing content after a complete value`() {
        assertNull(parseJson("{\"a\": 1} {\"b\": 2}"))
        assertNull(parseJson("[1, 2] garbage"))
        assertNotNull("whitespace after a value is fine", parseJson("{\"a\": 1}   \n  "))
    }

    /**
     * Money here is Long minor units. Accepting a float would mean a hand-edited or
     * foreign-generated file could land a rounded amount in someone's ledger.
     */
    @Test
    fun `refuses non integer numbers`() {
        for (bad in listOf("1.5", "1.0", "-0.5", "1e3", "1E3", "1.2e4", ".5", "5.")) {
            assertNull("should have refused the number $bad", parseJson(bad))
        }
    }

    @Test
    fun `refuses leading zeros`() {
        assertNull(parseJson("01"))
        assertNull(parseJson("-01"))
        assertEquals(JsonNumber(0), parseJson("0"))
        assertEquals(JsonNumber(-0), parseJson("-0"))
    }

    /** Two answers to the same question; keeping either one silently would be a guess. */
    @Test
    fun `refuses duplicate keys`() {
        assertNull(parseJson("{\"a\": 1, \"a\": 2}"))
    }

    @Test
    fun `refuses an unterminated string`() {
        assertNull(parseJson("\"no closing quote"))
        assertNull(parseJson("{\"a\": \"unterminated}"))
    }

    @Test
    fun `refuses a raw control character inside a string`() {
        assertNull(parseJson("\"line\nbreak\""))
        assertNull(parseJson("\"tab\there\""))
    }

    @Test
    fun `refuses an unknown or dangling escape`() {
        assertNull(parseJson("\"bad \\x escape\""))
        assertNull(parseJson("\"dangling \\"))
        assertNull(parseJson("\"short \\u12\""))
    }

    @Test
    fun `reads the escapes it is supposed to`() {
        assertEquals(JsonString("\" \\ / \n \r \t \b \u000C"), parseJson("\"\\\" \\\\ \\/ \\n \\r \\t \\b \\f\""))
        assertEquals(JsonString("\u20B9"), parseJson("\"\\u20B9\""))
    }

    /** A crafted file must not be able to recurse the parser off the stack. */
    @Test
    fun `refuses a document nested past the depth limit`() {
        val deep = "[".repeat(200) + "]".repeat(200)
        assertNull(parseJson(deep))
        val shallow = "[".repeat(20) + "]".repeat(20)
        assertNotNull("ordinary nesting must still parse", parseJson(shallow))
    }

    @Test
    fun `accepts whitespace anywhere it is legal`() {
        val parsed = parseJson("  {  \"a\"  :  [  1  ,  2  ]  ,  \"b\"  :  true  }  ")
        assertEquals(
            jsonObject("a" to jsonArray(listOf(1L.json(), 2L.json())), "b" to true.json()),
            parsed
        )
    }

    @Test
    fun `output is indented rather than compact`() {
        val text = writeJson(jsonObject("a" to jsonArray(listOf(1L.json()))))
        assertTrue("a backup should be readable by eye", text.contains("\n"))
        assertTrue(text.contains("  "))
    }

    /**
     * A deeply nested document must not take the app down with it.
     *
     * The parser is recursive descent, so nesting depth maps straight onto stack depth, and a
     * backup file is read from wherever the user points the picker, which includes a file that is
     * corrupt, truncated, or simply not a backup. A StackOverflowError is not a `Damaged` result;
     * it is a crash on a screen whose entire job is to fail safely.
     */
    @Test
    fun `deep nesting is refused rather than crashing the parser`() {
        listOf(200, 2_000, 50_000).forEach { depth ->
            val deep = "[".repeat(depth) + "]".repeat(depth)
            val result = try {
                decodeBackup(deep)
            } catch (e: StackOverflowError) {
                throw AssertionError("the JSON parser overflowed the stack at depth $depth")
            } catch (e: Exception) {
                throw AssertionError("the JSON parser threw at depth $depth: $e")
            }
            assertTrue("depth $depth", result is BackupResult.NotABackup || result is BackupResult.Damaged)
        }
    }

    @Test
    fun `deeply nested objects are refused too`() {
        val depth = 50_000
        val deep = """{"a":""".repeat(depth) + "1" + "}".repeat(depth)
        val result = try {
            decodeBackup(deep)
        } catch (e: StackOverflowError) {
            throw AssertionError("the JSON parser overflowed the stack on nested objects")
        }
        assertTrue(result is BackupResult.NotABackup || result is BackupResult.Damaged)
    }
}
