package com.kg.merapaisa.data

/**
 * A small JSON reader and writer, just enough for the backup file.
 *
 * Hand-rolled for the same reason as the share codec: `org.json` is an Android framework stub that
 * throws under plain JVM unit tests, and every alternative (kotlinx.serialization, Moshi, Gson) is a
 * new dependency, which `loop-audit/PROTOCOL.md` rules out. A backup format that cannot be tested
 * without a device is a backup format nobody should trust.
 *
 * **Integers only, deliberately.** Money in this app is a `Long` count of minor units, and there is
 * no value anywhere in a backup that wants to be a float. Refusing `1.5` and `1e3` outright means a
 * corrupted or hand-edited file fails loudly at parse time instead of silently landing a rounded
 * amount in somebody's ledger. Every number here round-trips exactly.
 *
 * A backup file is untrusted input — it can be edited, truncated by a failed write, or handed over
 * by someone else — so the parser bounds its own recursion and refuses trailing junk.
 */

sealed interface JsonValue

data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue {
    operator fun get(key: String): JsonValue? = fields[key]

    fun string(key: String): String? = (fields[key] as? JsonString)?.value
    fun long(key: String): Long? = (fields[key] as? JsonNumber)?.value
    fun bool(key: String): Boolean? = (fields[key] as? JsonBoolean)?.value
    fun array(key: String): List<JsonValue>? = (fields[key] as? JsonArray)?.items
    fun objects(key: String): List<JsonObject>? =
        array(key)?.let { items -> items.map { it as? JsonObject ?: return null } }
}

data class JsonArray(val items: List<JsonValue>) : JsonValue
data class JsonString(val value: String) : JsonValue
data class JsonNumber(val value: Long) : JsonValue
data class JsonBoolean(val value: Boolean) : JsonValue
data object JsonNull : JsonValue

// -------------------------------------------------------------------------------------------
// Writing
// -------------------------------------------------------------------------------------------

/**
 * Renders [value] as indented JSON.
 *
 * Indented rather than compact on purpose: a backup is something you should be able to open and
 * read. The size difference on a ledger of a few thousand entries is not worth the opacity.
 */
fun writeJson(value: JsonValue): String = StringBuilder().also { write(value, it, 0) }.toString()

private fun write(value: JsonValue, out: StringBuilder, depth: Int) {
    when (value) {
        is JsonNull -> out.append("null")
        is JsonBoolean -> out.append(if (value.value) "true" else "false")
        is JsonNumber -> out.append(value.value)
        is JsonString -> writeString(value.value, out)
        is JsonArray -> {
            if (value.items.isEmpty()) { out.append("[]"); return }
            out.append("[\n")
            value.items.forEachIndexed { i, item ->
                indent(out, depth + 1)
                write(item, out, depth + 1)
                if (i < value.items.size - 1) out.append(',')
                out.append('\n')
            }
            indent(out, depth)
            out.append(']')
        }
        is JsonObject -> {
            if (value.fields.isEmpty()) { out.append("{}"); return }
            out.append("{\n")
            val entries = value.fields.entries.toList()
            entries.forEachIndexed { i, (key, item) ->
                indent(out, depth + 1)
                writeString(key, out)
                out.append(": ")
                write(item, out, depth + 1)
                if (i < entries.size - 1) out.append(',')
                out.append('\n')
            }
            indent(out, depth)
            out.append('}')
        }
    }
}

private fun indent(out: StringBuilder, depth: Int) {
    repeat(depth) { out.append("  ") }
}

private fun writeString(s: String, out: StringBuilder) {
    out.append('"')
    for (c in s) {
        when {
            c == '"' -> out.append("\\\"")
            c == '\\' -> out.append("\\\\")
            c == '\n' -> out.append("\\n")
            c == '\r' -> out.append("\\r")
            c == '\t' -> out.append("\\t")
            c == '\b' -> out.append("\\b")
            c == '\u000C' -> out.append("\\f")
            // Everything below 0x20 must be escaped; \\u form covers the ones without a short name.
            c < ' ' -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> out.append(c)
        }
    }
    out.append('"')
}

// -------------------------------------------------------------------------------------------
// Reading
// -------------------------------------------------------------------------------------------

/** Null when [text] is not valid JSON under the rules in this file's header. */
fun parseJson(text: String): JsonValue? {
    val parser = JsonParser(text)
    val value = parser.parseValue(0) ?: return null
    parser.skipWhitespace()
    // Trailing content means the file is not what it claims to be — a concatenation, or a partial
    // overwrite of a longer previous backup. Either way it is not safe to use the first half.
    return if (parser.atEnd()) value else null
}

/** Deep enough for any backup this app writes; shallow enough that a crafted file cannot recurse away. */
private const val MAX_DEPTH = 32

private class JsonParser(private val text: String) {
    private var i = 0

    fun atEnd(): Boolean = i >= text.length

    fun skipWhitespace() {
        while (i < text.length && (text[i] == ' ' || text[i] == '\n' || text[i] == '\r' || text[i] == '\t')) i++
    }

    fun parseValue(depth: Int): JsonValue? {
        if (depth > MAX_DEPTH) return null
        skipWhitespace()
        if (atEnd()) return null
        return when (text[i]) {
            '{' -> parseObject(depth)
            '[' -> parseArray(depth)
            '"' -> parseString()?.let { JsonString(it) }
            't' -> literal("true", JsonBoolean(true))
            'f' -> literal("false", JsonBoolean(false))
            'n' -> literal("null", JsonNull)
            else -> parseNumber()
        }
    }

    private fun literal(word: String, value: JsonValue): JsonValue? {
        if (!text.startsWith(word, i)) return null
        i += word.length
        return value
    }

    private fun parseObject(depth: Int): JsonValue? {
        i++ // '{'
        val fields = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (i < text.length && text[i] == '}') { i++; return JsonObject(fields) }
        while (true) {
            skipWhitespace()
            if (i >= text.length || text[i] != '"') return null
            val key = parseString() ?: return null
            skipWhitespace()
            if (i >= text.length || text[i] != ':') return null
            i++
            val value = parseValue(depth + 1) ?: return null
            // A duplicate key means two different answers to the same question; refuse rather than
            // silently keeping one of them.
            if (fields.put(key, value) != null) return null
            skipWhitespace()
            if (i >= text.length) return null
            when (text[i]) {
                ',' -> i++
                '}' -> { i++; return JsonObject(fields) }
                else -> return null
            }
        }
    }

    private fun parseArray(depth: Int): JsonValue? {
        i++ // '['
        val items = ArrayList<JsonValue>()
        skipWhitespace()
        if (i < text.length && text[i] == ']') { i++; return JsonArray(items) }
        while (true) {
            val value = parseValue(depth + 1) ?: return null
            items.add(value)
            skipWhitespace()
            if (i >= text.length) return null
            when (text[i]) {
                ',' -> i++
                ']' -> { i++; return JsonArray(items) }
                else -> return null
            }
        }
    }

    private fun parseString(): String? {
        i++ // opening quote
        val out = StringBuilder()
        while (true) {
            if (i >= text.length) return null
            val c = text[i]
            when {
                c == '"' -> { i++; return out.toString() }
                c == '\\' -> {
                    i++
                    if (i >= text.length) return null
                    when (text[i]) {
                        '"' -> out.append('"')
                        '\\' -> out.append('\\')
                        '/' -> out.append('/')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> {
                            if (i + 4 >= text.length) return null
                            val hex = text.substring(i + 1, i + 5)
                            val code = hex.toIntOrNull(16) ?: return null
                            out.append(code.toChar())
                            i += 4
                        }
                        else -> return null
                    }
                    i++
                }
                // Raw control characters are not legal inside a JSON string.
                c < ' ' -> return null
                else -> { out.append(c); i++ }
            }
        }
    }

    /**
     * Integers only. `1.5`, `1e3` and `-0` with a fraction are all refused — see the file header for
     * why a money format is better off rejecting them than rounding them.
     */
    private fun parseNumber(): JsonValue? {
        val start = i
        if (i < text.length && text[i] == '-') i++
        val digitsStart = i
        while (i < text.length && text[i].isDigit()) i++
        if (i == digitsStart) return null
        // Leading zeros are not valid JSON, and are a sign of a hand-edited or generated file.
        if (i - digitsStart > 1 && text[digitsStart] == '0') return null
        if (i < text.length && (text[i] == '.' || text[i] == 'e' || text[i] == 'E')) return null
        return text.substring(start, i).toLongOrNull()?.let { JsonNumber(it) }
    }
}

// -------------------------------------------------------------------------------------------
// Small builders, so callers read as the shape they are producing
// -------------------------------------------------------------------------------------------

fun jsonObject(vararg pairs: Pair<String, JsonValue>): JsonObject = JsonObject(linkedMapOf(*pairs))

fun jsonArray(items: List<JsonValue>): JsonArray = JsonArray(items)

fun String.json(): JsonString = JsonString(this)
fun Long.json(): JsonNumber = JsonNumber(this)
fun Int.json(): JsonNumber = JsonNumber(this.toLong())
fun Boolean.json(): JsonBoolean = JsonBoolean(this)
